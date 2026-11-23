package com.miuing.reader

import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A bounded, generated-files-only sink. Book-controlled paths never reach the filesystem. */
internal class ConvertedBookWriter(val directory:File):AutoCloseable {
    private val files=linkedSetOf<String>()
    private var output:OutputStream?=null
    private var current=""
    private var size=0L
    private var total=0L
    private var closed=false
    init {check(directory.mkdirs()) {"无法准备导入目录"}}
    @Synchronized fun write(name:String,data:String,last:Boolean):Boolean {
        if(closed) return false
        return try {
            require(name.matches(Regex("(?:c[0-9]+\\.xhtml|r[0-9]+\\.(?:bin|css))")) && name.length<=40)
            require(data.length<=65536)
            if(output==null) {
                require(name !in files && files.size<9996)
                current=name;size=0;output=File(directory,name).outputStream()
            }
            require(current==name)
            val bytes=Base64.decode(data,Base64.NO_WRAP)
            size+=bytes.size;total+=bytes.size
            require(size<=16L*1024*1024 && total<=256L*1024*1024)
            output!!.write(bytes)
            if(last) {output!!.close();output=null;files.add(name);current=""}
            true
        } catch(_:Exception) {close();false}
    }
    @Synchronized fun complete(metadata:JSONObject,destination:File) {
        check(!closed && output==null) {"导入内容未完整写入"}
        val sections=array(metadata,"sections",2000).map {value->
            val item=value as JSONObject
            Section(registered(item.getString("file")),text(item.optString("title"),"章节"),item.optBoolean("linear",true))
        }
        require(sections.isNotEmpty() && sections.any {it.linear}) {"图书没有可阅读正文"}
        require(sections.map {it.file}.distinct().size==sections.size)
        val resources=array(metadata,"resources",7996).map {value->
            val item=value as JSONObject;val media=item.optString("type")
            Resource(registered(item.getString("file")),if(media.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))) media else "application/octet-stream")
        }
        require((sections.map {it.file}+resources.map {it.file}).toSet()==files) {"转换资源清单不完整"}
        val title=text(metadata.optString("title"),"未命名图书");val author=text(metadata.optString("author"),"未知作者")
        val cover=metadata.optString("cover");require(cover.isEmpty() || resources.any {it.file==cover})
        val toc=array(metadata,"toc",2000).map {value->
            val item=value as JSONObject;val href=item.getString("href")
            require(href.length<=4096 && sections.any {it.file==href.substringBefore('#')})
            require(!href.contains('<') && !href.contains('"'))
            require(item.optInt("depth") in 0..32)
            Nav(text(item.optString("label"),"章节"),href,item.optInt("depth"))
        }.ifEmpty {sections.filter {it.linear}.map {Nav(it.title,it.file,0)}}
        val nav=navigation(toc)
        val opf=buildString {
            append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"book-id\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
            append("<dc:identifier id=\"book-id\">urn:uuid:${UUID.randomUUID()}</dc:identifier><dc:title>${xml(title)}</dc:title><dc:creator>${xml(author)}</dc:creator><dc:language>und</dc:language>")
            if(cover.isNotEmpty()) append("<meta name=\"cover\" content=\"${xml(cover)}\"/>")
            append("</metadata><manifest><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>")
            sections.forEach {append("<item id=\"${it.file}\" href=\"${it.file}\" media-type=\"application/xhtml+xml\"/>")}
            resources.forEach {append("<item id=\"${it.file}\" href=\"${it.file}\" media-type=\"${it.type}\"${if(it.file==cover) " properties=\"cover-image\"" else ""}/>")}
            append("</manifest><spine>");sections.filter {it.linear}.forEach {append("<itemref idref=\"${it.file}\"/>")};append("</spine></package>")
        }
        val pending=File(destination.parentFile,destination.name+".partial")
        try {
            ZipOutputStream(pending.outputStream()).use {zip->
                val mime="application/epub+zip".toByteArray()
                zip.putNextEntry(ZipEntry("mimetype").apply {method=ZipEntry.STORED;size=mime.size.toLong();compressedSize=size;crc=CRC32().apply {update(mime)}.value})
                zip.write(mime);zip.closeEntry()
                fun entry(name:String,bytes:ByteArray) {zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}
                entry("META-INF/container.xml","<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" version=\"1.0\"><rootfiles><rootfile full-path=\"book.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>".toByteArray())
                entry("book.opf",opf.toByteArray());entry("nav.xhtml",nav.toByteArray())
                files.forEach {name->zip.putNextEntry(ZipEntry(name));File(directory,name).inputStream().use {it.copyTo(zip)};zip.closeEntry()}
            }
            check(pending.renameTo(destination)) {"无法保存阅读副本"}
        } finally {pending.delete()}
    }
    private data class Section(val file:String,val title:String,val linear:Boolean)
    private data class Resource(val file:String,val type:String)
    private data class Nav(val title:String,val href:String,val depth:Int,val children:MutableList<Nav> = mutableListOf())
    private fun navigation(items:List<Nav>):String {
        val roots=mutableListOf<Nav>();val parents=mutableListOf(roots);var previous:Nav?=null
        for(item in items) {
            val depth=minOf(item.depth,if(previous==null) 0 else parents.size)
            if(depth==parents.size) parents.add(previous!!.children)
            while(parents.size>depth+1) parents.removeAt(parents.lastIndex)
            parents.last().add(item);previous=item
        }
        fun render(nodes:List<Nav>):String="<ol>"+nodes.joinToString("") {"<li><a href=\"${xml(it.href)}\">${xml(it.title)}</a>${if(it.children.isNotEmpty()) render(it.children) else ""}</li>"}+"</ol>"
        return "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><head><title>目录</title></head><body><nav epub:type=\"toc\">${render(roots)}</nav></body></html>"
    }
    private fun registered(name:String):String {require(name in files);return name}
    private fun array(data:JSONObject,key:String,limit:Int):List<Any> {
        val a=data.getJSONArray(key);require(a.length()<=limit);return (0 until a.length()).map {a.get(it)}
    }
    private fun text(value:String,fallback:String)=value.filter {it>=' ' || it=='\n' || it=='\t'}.trim().take(4096).ifEmpty {fallback}
    private fun xml(value:String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    @Synchronized override fun close() {closed=true;runCatching {output?.close()};output=null}
}
