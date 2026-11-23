package com.miuing.reader

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object EpubCompatibilityFixtures {
    const val idpf="http://www.idpf.org/2008/embedding"
    const val adobe="http://ns.adobe.com/pdf/enc#RC"
    fun book(file:File,title:String,algorithm:String?=idpf):File {
        val identifier="urn:uuid:12345678-1234-5678-9abc-def012345678"
        val fontFile=listOf("/system/fonts/Roboto-Regular.ttf","/system/fonts/RobotoStatic-Regular.ttf").map(::File).firstOrNull {it.isFile}
            ?: File("/system/fonts").listFiles()!!.first {it.extension=="ttf"}
        val font=fontFile.readBytes()
        val key=if(algorithm==adobe) byteArrayOf(0x12,0x34,0x56,0x78,0x12,0x34,0x56,0x78,0x9a.toByte(),0xbc.toByte(),0xde.toByte(),0xf0.toByte(),0x12,0x34,0x56,0x78)
            else MessageDigest.getInstance("SHA-1").digest(identifier.toByteArray())
        if(algorithm!=null) for(i in 0 until minOf(font.size,if(algorithm==adobe) 1024 else 1040)) font[i]=(font[i].toInt() xor key[i%key.size].toInt()).toByte()
        val entries=linkedMapOf(
            "mimetype" to "application/epub+zip".toByteArray(),
            "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='OPS/package.opf'/></rootfiles></container>".toByteArray(),
            "OPS/package.opf" to """<package unique-identifier="book-id" xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata><dc:title>$title</dc:title><dc:identifier id="book-id">$identifier</dc:identifier></metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/><item id="font" href="publisher.ttf" media-type="font/ttf"/></manifest><spine><itemref idref="chapter"/></spine></package>""".toByteArray(),
            "OPS/publisher.ttf" to font,
            "OPS/chapter.xhtml" to ("""<html><head><meta charset="UTF-8"/><title>$title</title><style>@font-face{font-family:CompatibilityFont;src:url('publisher.ttf')}body{font-family:CompatibilityFont}p{font-family:CompatibilityFont}</style></head><body>"""+
                (0 until 650).joinToString("") {"<p id='p$it'>兼容性原文段落 $it — Reading passage $it, reliable font and location restoration. 阅读文字不会丢失。</p>"}+"</body></html>").toByteArray()
        )
        if(algorithm!=null) entries["META-INF/encryption.xml"]="""<encryption><EncryptedData xmlns="http://www.w3.org/2001/04/xmlenc#"><EncryptionMethod Algorithm="$algorithm"/><CipherData><CipherReference URI="OPS/publisher.ttf"/></CipherData></EncryptedData></encryption>""".toByteArray()
        ZipOutputStream(file.outputStream()).use {out->entries.forEach {(name,bytes)->out.putNextEntry(ZipEntry(name));out.write(bytes);out.closeEntry()}}
        return file
    }
}
