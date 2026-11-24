package com.miuing.reader

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.OutputStreamWriter

/** Own the selected notes and SAF operation across Activity recreation, independently of the reader. */
internal class NoteExportSession(application: Application, private val state: SavedStateHandle) : AndroidViewModel(application) {
    private data class Request(val document: NotesDocument, val format: NotesFormat)
    private var pending: Request? = null
    var busy by mutableStateOf(false); private set
    private val messages = Channel<String>(Channel.UNLIMITED)
    val notices = messages.receiveAsFlow()

    init {
        if (state.get<Boolean>("pending") == true) messages.trySend("上次笔记导出已中断，请重新导出")
        state["pending"] = false
    }

    fun begin(document: NotesDocument, format: NotesFormat): Intent? {
        if (busy || document.count == 0) return null
        pending = Request(document.copy(chapters = document.chapters.map { it.copy(notes = it.notes.toList()) }), format)
        busy = true; state["pending"] = true
        return Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(format.mime).putExtra(Intent.EXTRA_TITLE, document.filename(format))
    }

    fun complete(uri: Uri?) {
        val request = pending
        pending = null
        if (uri == null) { if (request != null) { busy = false; state["pending"] = false }; return }
        val app = getApplication<Application>()
        // A result delivered after process death has no selection. Remove its empty created file.
        if (request == null) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) } }
            return
        }
        viewModelScope.launch {
            try {
                require(uri.scheme == "content") { "请选择系统文件保存位置" }
                DocumentExport.write(app.cacheDir,
                    content = { stream ->
                        val writer = OutputStreamWriter(stream, Charsets.UTF_8)
                        request.document.write(writer, request.format); writer.flush()
                    },
                    destination = { app.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入所选位置") },
                    deleteDestination = { DocumentsContract.deleteDocument(app.contentResolver, uri) })
                messages.trySend("已导出 ${request.document.count} 条高亮标记和笔记")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { messages.trySend("笔记导出失败：${e.localizedMessage ?: "请重新选择保存位置"}") }
            finally { busy = false; state["pending"] = false }
        }
    }
}
