package com.miuing.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

internal object ImportSource {
    data class Info(val name:String,val mime:String?)
    private val formats=setOf("epub","pdf","txt","mobi","azw","azw3","fb2")
    fun resolve(context:Context,uri:Uri):Info {
        val name=if(uri.scheme=="file") File(uri.path!!).name else
            runCatching {context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                if(it.moveToFirst()) it.getString(0) else null
            }}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "未命名图书"
        val mime=runCatching {context.contentResolver.getType(uri)}.getOrNull()
        return Info(name,mime)
    }
    fun detect(name:String,mime:String?,bytes:ByteArray):String {
        val ext=name.substringAfterLast('.',"").lowercase()
        val ascii=String(bytes,Charsets.ISO_8859_1)
        if(ascii.startsWith("%PDF-")) return "pdf"
        if(bytes.size>=4 && bytes[0]==80.toByte() && bytes[1]==75.toByte() && bytes[2]==3.toByte() && bytes[3]==4.toByte()) return "epub"
        if(bytes.size>=68 && ascii.substring(60,68) in setOf("BOOKMOBI","TEXtREAd")) return if(ext in setOf("mobi","azw","azw3")) ext else "mobi"
        val xml=if(bytes.size>=2 && (bytes[0]==(-1).toByte() && bytes[1]==(-2).toByte() || bytes[0]==(-2).toByte() && bytes[1]==(-1).toByte())) String(bytes,Charsets.UTF_16) else String(bytes,Charsets.UTF_8)
        if(Regex("<([A-Za-z_][\\w.-]*:)?FictionBook(?:\\s|>)").containsMatchIn(xml)) return "fb2"
        if(ext in formats) return ext
        return when(mime?.substringBefore(';')?.trim()?.lowercase()) {
            "application/epub+zip"->"epub";"application/pdf"->"pdf";"text/plain"->"txt"
            "application/x-mobipocket-ebook","application/vnd.amazon.ebook"->"mobi"
            "application/vnd.amazon.mobi8-ebook","application/x-mobi8-ebook","application/x-azw3"->"azw3"
            "application/x-fictionbook+xml","application/fb2+xml"->"fb2"
            else->error("无法识别图书格式：$name")
        }
    }
}
