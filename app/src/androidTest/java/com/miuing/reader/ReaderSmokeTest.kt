package com.miuing.reader

import android.app.Activity
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ReaderSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun web(view:View):WebView? {
        if(view is WebView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) web(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun evaluate(scenario:ActivityScenario<MainActivity>,js:String):String {
        val latch=CountDownLatch(1);var result=""
        scenario.onActivity { activity ->
            val reader=web(activity.window.decorView) ?: throw AssertionError("Reader WebView missing")
            reader.evaluateJavascript(js) { result=it;latch.countDown() }
        }
        assertTrue("JavaScript evaluation timed out",latch.await(10,TimeUnit.SECONDS))
        return result
    }
    @Test fun epubRendersOfflineBlocksBookScriptsAndRestoresAfterRecreation()= runBlocking {
        val context=instrumentation.targetContext
        context.getSharedPreferences("reading",0).edit().clear().putBoolean("scroll",false).commit()
        val file=File(context.cacheDir,"smoke.epub")
        val chapters="<p>这是一段离线阅读正文。</p>".repeat(100)
        ZipOutputStream(file.outputStream()).use { zip ->
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                "book.opf" to "<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>MIUing测试图书</dc:title></metadata><manifest><item id='chapter' href='OPS/content/chapter.xhtml' media-type='application/xhtml+xml'/><item id='second' href='second.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='chapter'/><itemref idref='second'/></spine></package>",
                "OPS/content/chapter.xhtml" to "<html><head><title>第一章</title></head><body><h1>第一章</h1><img src='../images/pixel.svg'/><p>跨元素<em>选择</em>高亮</p>$chapters<script>window.bookScriptRan=true;</script></body></html>",
                "OPS/images/pixel.svg" to "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'><rect width='10' height='10' fill='red'/></svg>",
                "second.xhtml" to "<html><head><title>第二章</title></head><body>章节切换后的正文。</body></html>"
            ).forEach { (name,text) -> zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray());zip.closeEntry() }
        }
        val store=LibraryStore(context)
        var id:String?=null
        try {
            val book=store.import(Uri.fromFile(file));id=book.id
            store.update(book.copy(notes=listOf(Note(0,"跨元素选择高亮"))))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val device=UiDevice.getInstance(instrumentation)
                val title=device.wait(Until.findObject(By.text("MIUing测试图书")),15000)
                assertNotNull("Imported book was not shown",title)
                device.takeScreenshot(File(context.getExternalFilesDir(null),"library.png"))
                title.click()
                val limit=System.currentTimeMillis()+15000
                var rendered=false
                while(System.currentTimeMillis()<limit) {
                    try { rendered=evaluate(scenario,"document.body.innerText.includes('离线阅读正文') && typeof window.miuingJump==='function' && !!window.miuingConfig")=="true" } catch(_:AssertionError) { }
                    if(rendered) break
                    Thread.sleep(150)
                }
                assertTrue("EPUB正文未渲染",rendered)
                assertEquals("Book-authored scripts executed","false",evaluate(scenario,"window.bookScriptRan===true"))
                assertEquals("Reader script did not initialize","\"function\"",evaluate(scenario,"typeof window.miuingJump"))
                assertEquals("Cross-element highlight missing","true",evaluate(scenario,"Array.from(document.querySelectorAll('mark')).map(m=>m.textContent).join('').includes('跨元素选择高亮')"))
                assertEquals("Nested content directory broke image loading","true",evaluate(scenario,"document.images[0].naturalWidth>0"))
                Thread.sleep(800)
                assertEquals("First paragraph is outside the visible reading page","true",evaluate(scenario,"(()=>{const r=document.querySelector('p').getBoundingClientRect();return r.x>=0&&r.x<screen.width&&r.height>10&&r.height<200})()"))
                println("READER_METRICS "+evaluate(scenario,"JSON.stringify({iw:innerWidth,ih:innerHeight,scrollWidth:document.documentElement.scrollWidth,p:document.querySelector('p').getBoundingClientRect().toJSON()})"))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"phone-reader.png"))
                evaluate(scenario,"window.miuingJump(0.5)")
                Thread.sleep(700)
                assertTrue("Scroll progress was not persisted",LibraryStore(context).current(book.id).position>.2f)
                scenario.recreate()
                Thread.sleep(1500)
                assertEquals("Lost reader after activity recreation","true",evaluate(scenario,"document.body.innerText.includes('离线阅读正文')"))
                val settings=device.findObject(By.text("aA"))
                assertNotNull("Reading settings button missing",settings)
                settings.click()
                device.waitForIdle()
                val toggle=device.wait(Until.findObject(By.desc("连续滚动")),5000)
                assertNotNull("Scroll mode switch missing",toggle)
                toggle.click()
                device.pressBack()
                Thread.sleep(500)
                assertEquals("Switching reading mode destroyed the reader","true",evaluate(scenario,"document.body.innerText.includes('离线阅读正文')"))
                val next=device.findObject(By.desc("下一章"))
                assertNotNull("Next chapter control missing",next);next.click()
                Thread.sleep(800)
                assertEquals("Switching chapter destroyed the reader","true",evaluate(scenario,"document.body.innerText.includes('章节切换后的正文')"))
            }
        } finally { file.delete();id?.let { LibraryStore(context).delete(it) } }
    }
    @Test fun pdfOpensAsRenderedPages()=runBlocking {
        val context=instrumentation.targetContext
        val file=File(context.cacheDir,"MIUing-PDF-test.pdf")
        val document=android.graphics.pdf.PdfDocument()
        try {
            for(i in 1..2) {
                val page=document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(600,800,i).create())
                page.canvas.drawColor(android.graphics.Color.WHITE)
                page.canvas.drawText("MIUing PDF page $i",40f,80f,android.graphics.Paint().apply {textSize=26f;color=android.graphics.Color.BLACK})
                document.finishPage(page)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally { document.close() }
        var id:String?=null
        try {
            val book=LibraryStore(context).import(Uri.fromFile(file));id=book.id
            ActivityScenario.launch(MainActivity::class.java).use {
                val device=UiDevice.getInstance(instrumentation)
                val title=device.wait(Until.findObject(By.text("MIUing-PDF-test")),10000)
                assertNotNull("PDF missing from library",title);title.click()
                assertNotNull("PDF did not render",device.wait(Until.findObject(By.desc("第 1 页")),15000))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"pdf-reader.png"))
                repeat(2) { _ -> it.recreate() }
            }
        } finally { file.delete();id?.let { LibraryStore(context).delete(it) } }
    }
}
