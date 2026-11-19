package com.miuing.reader

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class ReadingParityTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private fun find(v:View):WebView? {
        if(v is WebView) return v
        if(v is ViewGroup) repeat(v.childCount) { find(v.getChildAt(it))?.let { w->return w } }
        return null
    }
    private fun js(s:ActivityScenario<MainActivity>,code:String):String {
        val done=CountDownLatch(1);var result=""
        s.onActivity { find(it.window.decorView)!!.evaluateJavascript(code) { value->result=value;done.countDown() } }
        check(done.await(10,TimeUnit.SECONDS));return result
    }
    private fun awaitReader(scenario:ActivityScenario<MainActivity>) {
        val deadline=System.currentTimeMillis()+15000
        while(System.currentTimeMillis()<deadline) {
            val ready=try {js(scenario,"typeof window.miuingAnchor==='function' && !!window.miuingConfig")=="true"} catch(_:Exception) {false}
            if(ready) {Thread.sleep(200);return}
            Thread.sleep(100)
        }
        fail("Reader engine did not initialize within 15 seconds")
    }
    @Test fun navigationAnchorsAndExactHighlightsSurviveLayoutChanges()=runBlocking {
        val context=instrument.targetContext
        context.getSharedPreferences("reading",0).edit().clear().putBoolean("scroll",true).commit()
        val file=File(context.cacheDir,"parity.epub")
        val sampleTitle="阅读行为测试-${java.util.UUID.randomUUID().toString().take(8)}"
        ZipOutputStream(file.outputStream()).use { zip->
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                "book.opf" to "<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>$sampleTitle</dc:title></metadata><manifest><item id='c' href='chapter.xhtml' media-type='application/xhtml+xml'/><item id='nav' href='nav.xhtml' properties='nav' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='c'/></spine></package>",
                "nav.xhtml" to "<html xmlns:epub='http://www.idpf.org/2007/ops'><body><nav epub:type='toc'><ol><li><a href='chapter.xhtml#target'>指定段落</a><ol><li><a href='chapter.xhtml#second'>重复文字的第二处</a></li></ol></li></ol></nav></body></html>",
                "chapter.xhtml" to ("<html><head><title>章节一</title></head><body><style>.unused{color:red}</style><script>window.authorScriptRan=true;</script><h1>正文</h1><p id='first'>重复的文字</p><p>中间内容</p><p id='second'>重复的文字</p><p id='cross-a'>跨段落选择的上半段</p><p id='cross-b'>跨段落选择的下半段</p>"+(0..150).joinToString("") { "<p id='p$it'>第${it}段。这是用于验证阅读位置的独立正文。</p>" }+"<p id='target'>目录要定位的目标段落。</p></body></html>")
            ).forEach { (name,content)->zip.putNextEntry(ZipEntry(name));zip.write(content.toByteArray());zip.closeEntry() }
        }
        var id:String?=null
        try {
            val book=LibraryStore(context).import(Uri.fromFile(file));id=book.id
            ActivityScenario.launch(MainActivity::class.java).use { scenario->
                val device=UiDevice.getInstance(instrument)
                device.wait(Until.findObject(By.text(sampleTitle)),15000)!!.click()
                awaitReader(scenario)
                assertEquals("Text anchors not implemented","\"function\"",js(scenario,"typeof window.miuingAnchor"))
                js(scenario,"window.miuingJump(0.5)");Thread.sleep(600)
                val anchor=js(scenario,"window.miuingAnchor()").toInt()
                assertTrue("No stable text location saved",anchor>100)
                val saveBy=System.currentTimeMillis()+5000
                while(LibraryStore(context).current(book.id).anchor<=100 && System.currentTimeMillis()<saveBy) Thread.sleep(50)
                assertTrue("Anchor not persisted",LibraryStore(context).current(book.id).anchor>100)
                js(scenario,"window.miuingTestFont=32;window.miuingConfigure({...window.miuingConfig,fontSize:32})")
                Thread.sleep(500)
                assertTrue("Font change lost the passage",kotlin.math.abs(js(scenario,"window.miuingAnchor()").toInt()-anchor)<100)
                scenario.recreate();awaitReader(scenario)
                assertTrue("Recreation lost the anchored passage",kotlin.math.abs(js(scenario,"window.miuingAnchor()").toInt()-anchor)<100)
                val paragraph75=("正文重复的文字中间内容重复的文字跨段落选择的上半段跨段落选择的下半段"+(0 until 75).joinToString("") { "第${it}段。这是用于验证阅读位置的独立正文。" }).length
                js(scenario,"window.miuingJumpAnchor($paragraph75)");Thread.sleep(300)
                assertEquals("Paragraph boundary restored to the preceding paragraph","true",js(scenario,"Math.abs(document.getElementById('p75').getBoundingClientRect().top-window.miuingConfig.margin)<12"))
                js(scenario,"(()=>{window.miuingJump(0);const r=document.createRange();r.selectNodeContents(document.getElementById('first'));getSelection().removeAllRanges();getSelection().addRange(r)})()")
                assertNotNull(device.wait(Until.findObject(By.text("高亮标记")),5000))
                js(scenario,"getSelection().removeAllRanges()");Thread.sleep(500)
                assertNull("Deselected text still offers highlight actions",device.findObject(By.text("高亮标记")))
                js(scenario,"(()=>{window.miuingJump(0);const r=document.createRange();r.selectNodeContents(document.getElementById('second'));getSelection().removeAllRanges();getSelection().addRange(r)})()")
                device.wait(Until.findObject(By.text("高亮标记")),5000)!!.click();Thread.sleep(500)
                assertEquals("Wrong occurrence highlighted","true",js(scenario,"!!document.querySelector('#second mark')&&!document.querySelector('#first mark')"))
                js(scenario,"(()=>{const a=document.getElementById('cross-a').firstChild,b=document.getElementById('cross-b').firstChild,r=document.createRange();r.setStart(a,0);r.setEnd(b,b.length);getSelection().removeAllRanges();getSelection().addRange(r)})()")
                device.wait(Until.findObject(By.text("高亮标记")),5000)!!.click();Thread.sleep(500)
                assertEquals("Cross-paragraph highlight not restored","true",js(scenario,"!!document.querySelector('#cross-a mark')&&!!document.querySelector('#cross-b mark')"))
                scenario.recreate();awaitReader(scenario)
                assertEquals("Exact highlights lost on reopening","true",js(scenario,"!!document.querySelector('#second mark')&&!document.querySelector('#first mark')&&!!document.querySelector('#cross-a mark')&&!!document.querySelector('#cross-b mark')"))
                device.readerAction("目录与书签").click()
                device.wait(Until.findObject(By.text("高亮标记和笔记")),5000)!!.click()
                device.wait(Until.findObject(By.text("编辑")),5000)!!.click()
                device.pressBack();Thread.sleep(500)
                assertNull("Cancelled note editing kept stale selection actions",device.findObject(By.text("高亮标记")))
                device.readerAction("目录与书签").click()
                val entry=device.wait(Until.findObject(By.text("指定段落")),5000)
                device.takeScreenshot(File(context.getExternalFilesDir(null),"navigation.png"))
                assertNotNull("Nested EPUB navigation not displayed",entry);entry.click();Thread.sleep(700)
                assertEquals("Fragment link failed to locate target","true",js(scenario,"(()=>{const r=document.getElementById('target').getBoundingClientRect();return r.top>=0&&r.top<window.miuingHeight})()"))
                js(scenario,"window.miuingJump(0.5)");Thread.sleep(500)
                val onward=js(scenario,"window.miuingAnchor()").toInt()
                device.openReaderThemes()
                assertNotNull(device.wait(Until.findObject(By.text("主题与设置")),5000))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"reading-settings.png"))
                device.wait(Until.findObject(By.desc("翻页样式")),5000)!!.click()
                device.wait(Until.findObject(By.desc("连续滚动")),5000)!!.click()
                device.wait(Until.findObject(By.desc("关闭主题与设置")),5000)!!.click();Thread.sleep(800)
                assertTrue("Old TOC fragment overrode latest location",kotlin.math.abs(js(scenario,"window.miuingAnchor()").toInt()-onward)<900)
            }
        } finally { file.delete();id?.let { LibraryStore(context).delete(it) } }
    }
}
