package com.miuing.reader

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MultiformatImportTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(block:suspend (LibraryStore,File)->Unit)=runBlocking<Unit> {
        val root=File(context.cacheDir,"format-fixture-${System.nanoTime()}").apply {mkdirs()}
        val store=LibraryStore(context,File(root,"library"))
        try {block(store,root)} finally {root.deleteRecursively()}
    }
    @Test fun mobiAndAzwFamilyUseActualMobiContentAndRetainOriginal()=fixture {store,root->
        for(ext in listOf("mobi","azw","azw3")) {
            val source=MultiformatFixtures.mobi(File(root,"book.$ext"),"格式测试$ext")
            val original=source.readBytes();val book=store.import(Uri.fromFile(source))
            assertEquals(ext.uppercase(),book.format);assertEquals("格式测试$ext",book.title)
            assertArrayEquals(original,File(book.source).readBytes());source.delete()
            val p=store.open(book);assertEquals(2,p.chapters.size)
            assertTrue(p.chapters.first().path.toFile().readText().contains("开源整合阅读测试"))
            val duplicate=File(root,"duplicate.$ext").apply {writeBytes(original)}
            assertEquals(book.id,store.import(Uri.fromFile(duplicate)).id)
        }
        assertEquals(3,store.books.size)
    }
    @Test fun fb2PreservesChineseImageNestedTocAndCrossFileNote()=fixture {store,root->
        val source=MultiformatFixtures.fb2(File(root,"book.fb2"))
        val book=store.import(Uri.fromFile(source));source.delete()
        assertEquals("FB2",book.format);assertEquals("FB2中文测试",book.title);assertTrue(book.author.contains("张"))
        val p=store.open(book);assertEquals("Nonlinear notes must not become reading chapters",2,p.chapters.size)
        assertTrue(p.navigation.any {it.title=="嵌套目录" && it.depth>0})
        assertTrue(p.navigation.any {it.title=="第二章" && it.chapter==1})
        assertNotNull("Embedded cover was lost",p.cover);assertTrue(p.cover!!.toFile().length()>0)
        val first=p.chapters[0].path.toFile().readText()
        assertTrue(first.contains("跨元素搜索"));assertTrue(first.contains("c1.xhtml#"))
        assertTrue("Blob URLs cannot survive importer shutdown",!first.contains("blob:"))
        assertTrue(first.contains("epub:type=\"noteref\"") || first.contains("role=\"doc-noteref\""))
        val noteFiles=p.chapters[0].path.parent.toFile().listFiles()!!.filter {it.extension=="xhtml" && it.readText().contains("这是跨文件脚注内容")}
        assertTrue("Nonlinear note document was not preserved",noteFiles.isNotEmpty())
        val restored=LibraryStore(context,File(root,"library"));assertEquals(2,restored.open(restored.books.single()).chapters.size)
    }
    @Test fun drmAndWrongContainersFailWithoutPublishingRecords()=fixture {store,root->
        val protected=MultiformatFixtures.mobi(File(root,"protected.mobi"),encrypted=true)
        val error=runCatching {store.import(Uri.fromFile(protected))}.exceptionOrNull()
        assertNotNull(error);assertTrue("DRM needs an explicit explanation: ${error?.message}",error!!.message.orEmpty().contains("DRM"))
        val wrong=File(root,"wrong.azw3").apply {writeText("not a Kindle ebook")}
        assertTrue(runCatching {store.import(Uri.fromFile(wrong))}.isFailure)
        val broken=File(root,"broken.fb2").apply {writeText("<FictionBook><body>")}
        assertTrue(runCatching {store.import(Uri.fromFile(broken))}.isFailure)
        assertTrue(store.books.isEmpty());assertTrue("Failed imports retained book directories",File(root,"library").listFiles().orEmpty().none {it.isDirectory})
    }
    @Test fun missingReadingCopyCanBeRepairedWithoutLosingNotes()=fixture {store,root->
        val source=MultiformatFixtures.fb2(File(root,"repair.fb2"),"副本修复测试")
        val book=store.import(Uri.fromFile(source));val saved=book.copy(notes=listOf(Note(0,"跨元素搜索","保留的笔记")),marks=listOf(Mark(0,.3f,"保留的书签")),position=.3f)
        store.update(saved);File(File(book.source).parentFile,"normalized.epub").delete()
        assertTrue(runCatching {store.open(saved)}.isFailure)
        val repaired=store.import(Uri.fromFile(source));assertEquals(saved,repaired)
        assertEquals(2,store.open(repaired).chapters.size);assertEquals(1,store.books.size)
    }
    @Test fun cancellationRemovesConversionFilesAndDoesNotPublish()=fixture {store,root->
        val source=MultiformatFixtures.fb2(File(root,"cancel.fb2"),"取消导入测试")
        val xml=source.readText();source.writeText(xml.replace("</body>","<section><p>${"取消时不得残留记录。".repeat(100000)}</p></section></body>"))
        kotlinx.coroutines.coroutineScope {
        val job=launch {store.import(Uri.fromFile(source))}
        val until=System.currentTimeMillis()+10000
        while(!File(root,"library").walk().any {it.name.startsWith("convert-")} && job.isActive && System.currentTimeMillis()<until) delay(5)
        assertTrue("Cancellation test missed the active conversion",job.isActive)
        job.cancelAndJoin();assertTrue(store.books.isEmpty())
        assertTrue(File(root,"library").listFiles().orEmpty().none {it.isDirectory})
        }
    }
    @Test fun indexWriteFailureRollsBackTheImportedBook()=fixture {store,root->
        // API26 writes the base after its .bak rename; newer releases write .new.
        for(name in listOf("index.json","index.json.new","index.json.bak")) File(root,"library/$name").apply {mkdirs();File(this,"blocker").writeText("test")}
        val source=MultiformatFixtures.fb2(File(root,"persist.fb2"),"写入失败测试")
        assertTrue(runCatching {store.import(Uri.fromFile(source))}.isFailure)
        assertTrue("Failed persistence published a broken book",store.books.isEmpty())
        assertTrue(File(root,"library").listFiles().orEmpty().filter {it.isDirectory}.all {it.name.startsWith("index.json")})
    }
    @Test fun fb2Utf16AndActiveContentAreHandledAsBookData()=fixture {store,root->
        val source=MultiformatFixtures.fb2(File(root,"encoded.fb2"),"编码与清理测试")
        val xml=source.readText().replace("encoding=\"utf-8\"","encoding=\"utf-16\"")
            .replace("<p>第二章定位正文。</p>","<p onclick=\"alert(1)\">第二章定位正文。</p><script>MIUingImport.fail('book script executed')</script>")
        source.writeBytes(xml.toByteArray(Charsets.UTF_16))
        val book=store.import(Uri.fromFile(source));assertEquals("编码与清理测试",book.title)
        val html=store.open(book).chapters.joinToString("") {it.path.toFile().readText()}
        assertTrue(html.contains("第二章定位正文"));assertFalse(html.contains("<script"));assertFalse(html.contains("onclick="))
    }
    @Test fun oversizedOrEntityBooksFailAndCleanStaging()=fixture {store,root->
        val source=File(root,"large.mobi");java.io.RandomAccessFile(source,"rw").use {it.setLength(64L*1024*1024+1)}
        assertTrue(runCatching {store.import(Uri.fromFile(source))}.exceptionOrNull()?.message.orEmpty().contains("64MiB"))
        val entity=File(root,"entity.fb2").apply {writeText("<!DOCTYPE FictionBook [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><FictionBook><body>&x;</body></FictionBook>")}
        assertTrue(runCatching {store.import(Uri.fromFile(entity))}.isFailure)
        assertTrue(store.books.isEmpty());assertTrue(File(root,"library").listFiles().orEmpty().isEmpty())
    }
    @Test fun reusedFb2ImageDoesNotCountAsRepeatedBodyText()=fixture {store,root->
        val pixel=android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==",0)
        val image=ByteArray(1024*1024).apply {pixel.copyInto(this)}
        val binary=android.util.Base64.encodeToString(image,android.util.Base64.NO_WRAP)
        val source=File(root,"repeated.fb2").apply {writeText("<FictionBook xmlns:l='http://www.w3.org/1999/xlink'><description><title-info><book-title>重复图片测试</book-title></title-info></description><body>"+
            (1..25).joinToString("") {"<section><title><p>第${it}章</p></title><p>短正文。</p><image l:href='#picture'/></section>"}+
            "</body><binary id='picture' content-type='image/png'>$binary</binary></FictionBook>")}
        val book=store.import(Uri.fromFile(source));val p=store.open(book)
        assertEquals(25,p.chapters.size)
        assertTrue(p.chapters.all {it.path.toFile().length()<10000})
        assertEquals("One embedded resource should be shared",1,p.chapters[0].path.parent.toFile().listFiles()!!.count {it.extension=="bin"})
    }
}
