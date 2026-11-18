package com.miuing.reader

import android.net.Uri
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.*
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfInteractionTest {
    @Test fun searchesNavigatesSelectsAndPersistsInk()=runBlocking {
        val instrument=InstrumentationRegistry.getInstrumentation();val context=instrument.targetContext
        val file=File(context.cacheDir,"PDF交互测试.pdf")
        val exported=File(context.cacheDir,"pdf-ui-export.pdf")
        PDFBoxResourceLoader.init(context)
        PDDocument().use {doc->
            for(i in 0..1) {
                val page=PDPage(PDRectangle(600f,800f));doc.addPage(page)
                PDPageContentStream(doc,page).use {s->s.beginText();s.setFont(PDType1Font.HELVETICA,20f);s.newLineAtOffset(60f,700f);s.showText(if(i==0) "First searchable passage." else "Second searchable passage.");s.endText()}
            }
            val outline=PDDocumentOutline();doc.documentCatalog.documentOutline=outline
            outline.addLast(PDOutlineItem().apply {title="Part One";destination=PDPageFitDestination().apply {page=doc.getPage(0)}})
            doc.save(file)
        }
        var id:String?=null
        try {
            val book=LibraryStore(context).import(Uri.fromFile(file));id=book.id
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                val device=UiDevice.getInstance(instrument)
                device.wait(Until.findObject(By.text("PDF交互测试")),10000)!!.click()
                assertNotNull(device.wait(Until.findObject(By.desc("第 1 页")),15000))
                val search=device.wait(Until.findObject(By.desc("在图书中搜索")),5000)
                assertNotNull("PDF search action is missing",search);search.click()
                device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="Second"
                val hit=device.wait(Until.findObject(By.desc("PDF 搜索结果 第 2 页")),10000)
                assertNotNull("No PDF text search results",hit);hit.click()
                val arrived=device.wait(Until.findObject(By.text("第 2 / 2 页")),5000)
                if(arrived==null) {
                    device.takeScreenshot(File(context.getExternalFilesDir(null),"pdf-search-failed.png"))
                    device.dumpWindowHierarchy(File(context.getExternalFilesDir(null),"pdf-search-failed.xml"))
                }
                assertNotNull("Search did not jump to page; saved page=${LibraryStore(context).current(book.id).chapter}",arrived)
                device.findObject(By.desc("目录与书签")).click()
                device.wait(Until.findObject(By.text("Part One")),5000)!!.click()
                assertNotNull(device.wait(Until.findObject(By.text("第 1 / 2 页")),5000))
                device.wait(Until.findObject(By.desc("第 1 页")),5000)!!.click()
                assertTrue("PDF controls did not hide",device.wait(Until.gone(By.desc("在图书中搜索")),5000))
                device.waitForIdle()
                val page=device.wait(Until.findObject(By.desc("第 1 页")),5000)!!.visibleBounds
                // Accessibility bounds are clipped on tablets; glyph positions use
                // the full 600x800 page, not only its currently visible portion.
                val fullHeight=page.width()*800f/600f
                val x=page.left+(page.width()*.13f).toInt();val y=page.top+(fullHeight*.117f).toInt()
                device.swipe(x,y,x+2,y,140)
                val highlight=device.wait(Until.findObject(By.text("高亮标记")),5000)
                assertNotNull("PDF long press did not select text",highlight);highlight.click();Thread.sleep(500)
                assertTrue("PDF highlight was not saved",LibraryStore(context).current(book.id).notes.isNotEmpty())
                device.findObject(By.desc("标记 PDF")).click()
                assertNotNull("Ink mode not ready",device.wait(Until.findObject(By.text("完成标记")),5000))
                device.waitForIdle()
                val mid=page.top+page.height()/2
                device.swipe(page.left+page.width()/4,mid,page.left+page.width()*3/4,mid+20,25);Thread.sleep(500)
                assertTrue("Ink was not saved",LibraryStore(context).current(book.id).pdfInk.isNotEmpty())
                device.findObject(By.desc("撤销标记")).click();Thread.sleep(300)
                assertTrue("Ink undo failed",LibraryStore(context).current(book.id).pdfInk.isEmpty())
                device.swipe(page.left+page.width()/4,mid,page.left+page.width()*3/4,mid+30,25);Thread.sleep(500)
                device.takeScreenshot(File(context.getExternalFilesDir(null),"pdf-markup.png"))
                scenario.recreate();Thread.sleep(1500)
                assertTrue("PDF marks were lost after recreation",LibraryStore(context).current(book.id).pdfInk.isNotEmpty())
                val filter=IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply {addCategory(Intent.CATEGORY_OPENABLE);addDataType("application/pdf")}
                val monitor=instrument.addMonitor(filter,Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(Uri.fromFile(exported))),true)
                try {
                    device.findObject(By.desc("导出 PDF")).click()
                    scenario.recreate()
                    assertNotNull("Export completion not shown",device.wait(Until.findObject(By.text("已导出包含标记的 PDF 副本")),10000))
                    PDDocument.load(exported).use {doc->
                        assertTrue("UI export lost highlights",doc.getPage(0).annotations.any {it.subtype=="Highlight"})
                        assertTrue("UI export lost ink",doc.getPage(0).annotations.any {it.subtype=="Ink"})
                    }
                } finally {instrument.removeMonitor(monitor)}
            }
        } finally {file.delete();exported.delete();id?.let {LibraryStore(context).delete(it)}}
    }
}
