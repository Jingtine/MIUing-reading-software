package com.miuing.reader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PdfExportState(val bookId:String="",val running:Boolean=false,val message:String?=null,val revision:Int=0)

/** Application-owned operation: a reading-page recreation must not cancel an export. */
object PdfExports {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutable=MutableStateFlow(PdfExportState())
    val state=mutable.asStateFlow()
    @Synchronized fun start(context:Context,book:Book,destination:Uri):Boolean {
        if(mutable.value.running) return false
        val application=context.applicationContext
        val revision=mutable.value.revision+1
        mutable.value=PdfExportState(book.id,true,revision=revision)
        scope.launch {
            var staged:File?=null
            try {
                require(destination.scheme!="file" || File(destination.path ?: "").canonicalFile!=File(book.source).canonicalFile) {"请选择副本保存位置"}
                staged=File.createTempFile("miuing-export-",".pdf",application.cacheDir)
                PdfDocumentService(File(book.source),application).use {service->staged.outputStream().use {service.export(it,book.notes,book.pdfInk)}}
                // Open the destination only once a complete valid copy exists.
                application.contentResolver.openOutputStream(destination)?.use {out->staged.inputStream().use {it.copyTo(out)}} ?: error("无法写入所选位置")
                mutable.value=PdfExportState(book.id,false,"已导出包含标记的 PDF 副本",revision)
            } catch(e:Exception) {mutable.value=PdfExportState(book.id,false,"导出失败：${e.localizedMessage}",revision)}
            finally {staged?.delete()}
        }
        return true
    }
    @Synchronized fun consume(revision:Int) {if(mutable.value.revision==revision) mutable.value=mutable.value.copy(message=null)}
}
