package com.miuing.reader

import android.content.Intent
import android.content.ClipData
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExternalImportTest {
    @Test fun queuedRequestIsMarkedInterruptedEvenWhileWaitingForBackup() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        instrument.runOnMainSync {
            val state=androidx.lifecycle.SavedStateHandle()
            val session=LibrarySession(instrument.targetContext.applicationContext as android.app.Application,state)
            val owner=androidx.lifecycle.ViewModelStore().apply {put("test",session)}
            try {session.backupBusy=true;session.submit(listOf(Uri.parse("content://test/queued")),true);assertEquals(true,state.get<Boolean>("importPending"))}
            finally {owner.clear()}
        }
    }
    @Test fun viewShareAndMultipleStreamsUseOnlyContentUrisAndDeduplicate() {
        val one=Uri.parse("content://books/one");val two=Uri.parse("content://books/two")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        fun resolves(intent:Intent)=context.packageManager.queryIntentActivities(intent.setPackage(context.packageName),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        for(type in listOf("application/epub+zip","application/pdf","text/plain","application/x-mobipocket-ebook","application/vnd.amazon.ebook","application/vnd.amazon.mobi8-ebook","application/x-fictionbook+xml"))
            assertTrue("Missing system open handler: $type",resolves(Intent(Intent.ACTION_VIEW).setDataAndType(one,type)))
        assertTrue(resolves(Intent(Intent.ACTION_SEND).setType("application/epub+zip")))
        assertTrue(resolves(Intent(Intent.ACTION_SEND_MULTIPLE).setType("application/epub+zip")))
        assertFalse(resolves(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://example.com/book.epub"),"application/epub+zip")))
        assertEquals(listOf(one),IncomingBooks.parse(Intent(Intent.ACTION_VIEW,one)))
        val share=Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM,one).apply {clipData=ClipData.newRawUri("book",one)}
        assertEquals(listOf(one),IncomingBooks.parse(share))
        assertEquals(listOf(one,two),IncomingBooks.parse(Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,arrayListOf(one,two,one))))
        assertTrue(IncomingBooks.parse(Intent(Intent.ACTION_MAIN)).isEmpty())
        for(uri in listOf("file:///data/data/com.miuing.reader/private","https://example.com/book.epub"))
            assertTrue(runCatching {IncomingBooks.parse(Intent(Intent.ACTION_VIEW,Uri.parse(uri)))}.isFailure)
    }
    @Test fun boundedHeaderDetectionSupportsWrongOrMissingNames() {
        assertEquals("pdf",ImportSource.detect("wrong.epub",null,"%PDF-1.7".toByteArray()))
        assertEquals("epub",ImportSource.detect("opaque","application/octet-stream",byteArrayOf(80,75,3,4)))
        assertEquals("txt",ImportSource.detect("opaque","text/plain","正文".toByteArray()))
        assertEquals("fb2",ImportSource.detect("opaque",null,"<?xml version='1.0'?><FictionBook>".toByteArray()))
        assertEquals("azw3",ImportSource.detect("book.azw3",null,ByteArray(60)+"BOOKMOBI".toByteArray()))
        assertTrue(runCatching {ImportSource.detect("opaque","application/octet-stream",byteArrayOf(1,0,2))}.isFailure)
    }
    @Test fun misleadingExtensionImportsCorrectFormatAndKeepsPrivateCopy()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,"external-format-${System.nanoTime()}").apply {mkdirs()}
        try {
            val original=EpubCompatibilityFixtures.book(File(root,"wrong.pdf"),"外部格式识别",null)
            val store=LibraryStore(context,File(root,"library"))
            val book=store.import(Uri.fromFile(original));assertEquals("EPUB",book.format)
            original.delete();assertTrue(store.open(book).chapters.isNotEmpty())
        } finally {root.deleteRecursively()}
    }
    @Test fun mislabeledEpubAbove64MiBUsesActualFormatLimit()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,"external-large-${System.nanoTime()}").apply {mkdirs()}
        try {
            val base=EpubCompatibilityFixtures.book(File(root,"base.epub"),"大文件识别",null)
            val file=File(root,"wrong.mobi")
            java.util.zip.ZipOutputStream(file.outputStream()).use {out->
                java.util.zip.ZipInputStream(base.inputStream()).use {input->while(true){val e=input.nextEntry?:break;out.putNextEntry(java.util.zip.ZipEntry(e.name));input.copyTo(out);out.closeEntry()}}
                out.setLevel(0);val buffer=ByteArray(65536)
                repeat(6){out.putNextEntry(java.util.zip.ZipEntry("padding$it"));repeat(192){out.write(buffer)};out.closeEntry()}
            }
            assertTrue(file.length()>64L*1024*1024)
            assertEquals("EPUB",LibraryStore(context,File(root,"library")).import(Uri.fromFile(file)).format)
        }finally {root.deleteRecursively()}
    }
}
