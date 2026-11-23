package com.miuing.reader

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Tiny self-authored books. MOBI is a real Palm database with two HTML sections. */
internal object MultiformatFixtures {
    fun mobi(file:File,title:String="MOBI格式测试",encrypted:Boolean=false):File {
        val html=("<html><head><title>第一章</title></head><body><h1 id='first'>第一章</h1>"+
            "<p>开源整合阅读测试，中文正文与 <em>强调</em>。</p>".repeat(80)+
            "<mbp:pagebreak/><h1 id='second'>第二章</h1><p>第二章定位正文。</p></body></html>").toByteArray()
        val records=html.asList().chunked(4096).map {it.toByteArray()}
        val titleBytes=title.toByteArray();val record0=ByteBuffer.allocate(280+titleBytes.size).order(ByteOrder.BIG_ENDIAN)
        record0.putShort(0,1);record0.putInt(4,html.size);record0.putShort(8,records.size.toShort());record0.putShort(10,4096)
        record0.putShort(12,(if(encrypted) 1 else 0).toShort())
        record0.position(16);record0.put("MOBI".toByteArray());record0.putInt(20,248)
        record0.putInt(24,2);record0.putInt(28,65001);record0.putInt(32,123);record0.putInt(36,6)
        record0.putInt(84,280);record0.putInt(88,titleBytes.size);record0.putInt(108,records.size+1);record0.putInt(244,-1)
        record0.position(280);record0.put(titleBytes)
        val all=listOf(record0.array())+records
        val head=ByteBuffer.allocate(80+8*all.size).order(ByteOrder.BIG_ENDIAN)
        head.put(titleBytes.copyOf(32));head.position(60);head.put("BOOKMOBI".toByteArray());head.putShort(76,all.size.toShort())
        var offset=head.capacity();all.forEachIndexed {i,bytes->head.putInt(78+8*i,offset);offset+=bytes.size}
        file.outputStream().use {out->out.write(head.array());all.forEach {out.write(it)}};return file
    }
    fun fb2(file:File,title:String="FB2中文测试"):File {
        file.writeText("""<?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
            <description><title-info><author><first-name>张</first-name><last-name>三</last-name></author><book-title>$title</book-title><lang>zh</lang><coverpage><image l:href="#cover"/></coverpage></title-info><document-info><id>miuing-self-authored</id></document-info></description>
            <body><section id="first"><title><p>第一章</p></title><p>开源整合阅读测试，<strong>跨元素搜索</strong>正文。<a l:href="#second">跳到第二章</a> <a type="note" l:href="#note1">[1]</a></p><image l:href="#cover"/>
            ${"<p>这是可调整排版的中文正文，用于验证翻页、批注和阅读位置恢复。</p>".repeat(70)}
            <section id="nested"><title><p>嵌套目录</p></title><p>嵌套章节正文。</p></section></section>
            <section id="second"><title><p>第二章</p></title><p>第二章定位正文。</p></section></body>
            <body name="notes"><section id="note1"><title><p>注释</p></title><p>这是跨文件脚注内容。</p></section></body>
            <binary id="cover" content-type="image/png">iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==</binary>
            </FictionBook>""".trimIndent());return file
    }
}
