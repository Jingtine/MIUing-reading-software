package com.miuing.reader

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookSearchUiTest {
    private fun fixture()=BookReaderHarness(counts=listOf(2,1,2),sourceFactory={root,title->
        File(root,"search.epub").also {file->ZipOutputStream(file.outputStream()).use {zip->
            val entries=linkedMapOf("META-INF/container.xml" to "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                "book.opf" to "<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>$title</dc:title></metadata><manifest>"+(0..2).joinToString(""){"<item id='c$it' href='c$it.xhtml' media-type='application/xhtml+xml'/>"}+"</manifest><spine>"+(0..2).joinToString(""){"<itemref idref='c$it'/>"}+"</spine></package>")
            listOf(2,1,2).forEachIndexed {chapter,count->entries["c$chapter.xhtml"]="<html><body><p>原文起点 $chapter</p>"+(0 until count).joinToString(""){"<p>needle 章节$chapter 命中$it</p>"}+"</body></html>"}
            entries.forEach {(name,text)->zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray());zip.closeEntry()}
        }}
    })
    @Test fun eachHitCrossesChaptersAndCloseReturnsToOriginalPassage()=fixture().use {h->
        val source=h.saved()
        h.device.readerAction("在图书中搜索").click()
        h.device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!.text="needle"
        assertNotNull(h.device.wait(Until.findObject(By.text("找到 5 处匹配")),15000))
        val row=h.device.wait(Until.findObject(By.desc("文字搜索结果 第 1 章 第 1 处").enabled(true)),15000)!!
        assertEquals(source.anchor,h.saved().anchor);assertEquals(source.chapter,h.saved().chapter)
        h.device.takeScreenshot(File(h.instrument.targetContext.getExternalFilesDir(null),"search-results.png"))
        row.click()
        h.await {h.js("!!document.querySelector('[data-miuing-search]')")=="true"}
        repeat(2) {
            h.device.wait(Until.findObject(By.desc("下一处匹配").enabled(true)),5000)!!.click()
            h.await {h.js("!!document.querySelector('[data-miuing-search]')")=="true"}
        }
        h.await {h.saved().chapter==1}
        h.device.wait(Until.findObject(By.desc("下一处匹配").enabled(true)),5000)!!.click()
        h.await {h.saved().chapter==2 && h.js("!!document.querySelector('[data-miuing-search]')")=="true"}
        h.device.wait(Until.findObject(By.desc("上一处匹配").enabled(true)),5000)!!.click()
        h.await {h.saved().chapter==1 && h.js("!!document.querySelector('[data-miuing-search]')")=="true"}
        h.scenario.recreate()
        h.await {h.js("!!document.querySelector('[data-miuing-search]')")=="true"}
        h.device.wait(Until.findObject(By.desc("下一处匹配").enabled(true)),15000)!!.click()
        h.await {h.saved().chapter==2 && h.js("!!document.querySelector('[data-miuing-search]')")=="true"}
        h.device.wait(Until.findObject(By.desc("关闭搜索导航").enabled(true)),5000)!!.click()
        h.await {h.saved().chapter==source.chapter && h.saved().anchor==source.anchor}
        h.await {h.js("document.querySelector('[data-miuing-search]')===null")=="true"}
        assertTrue(h.maximumWebViews<=2)
    }
    @Test fun rapidQueryReplacementCancelsPreviewAndResumesPageCounting()=fixture().use {h->
        val source=h.saved()
        h.device.readerAction("在图书中搜索").click()
        val field=h.device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!
        field.text="need"
        assertNotNull(h.device.wait(Until.findObject(By.desc("文字搜索结果 第 1 章 第 1 处")),10000))
        field.text="missing"
        field.text="needle"
        assertNotNull(h.device.wait(Until.findObject(By.text("找到 5 处匹配")),15000))
        assertEquals(source.chapter,h.saved().chapter);assertEquals(source.anchor,h.saved().anchor)
        h.device.pressBack()
        h.await {h.footer()!=null}
        assertTrue(h.maximumWebViews<=2)
    }
    @Test fun layoutInvalidationCompletesThePendingPreviewCallback()=fixture().use {h->
        h.await {h.footer()!=null}
        val context=h.instrument.targetContext
        val root=File(File(h.book.source).parentFile,"content")
        val publication=LibraryStore(context).open(h.book)
        val config=org.json.JSONTokener(h.js("JSON.stringify(window.miuingConfig)")).nextValue() as String
        val spec=ReadingPreviewSpec(root,publication.chapters[0].path.toFile(),config,loadReaderScript(context),readingLoader(context,root),null)
        h.await {var ready=false;h.instrument.runOnMainSync {ready=!(h.find {it is ReadingTurnHost} as ReadingTurnHost).busy};ready}
        h.instrument.runOnMainSync {
            val host=h.find {it is ReadingTurnHost} as ReadingTurnHost
            host.releaseIdlePreview()
            var completed=false
            assertTrue(host.search(spec,"needle",10) {completed=true;assertNull(it)})
            host.invalidateLayout()
            assertTrue("Layout invalidation stranded the search continuation",completed)
        }
    }
    @Test fun scannerRetriesItsChapterAfterLayoutInvalidation()=fixture().use {h->
        h.await {h.footer()!=null}
        val context=h.instrument.targetContext
        val root=File(File(h.book.source).parentFile,"content")
        val p=LibraryStore(context).open(h.book)
        val config=org.json.JSONTokener(h.js("JSON.stringify(window.miuingConfig)")).nextValue() as String
        val size=org.json.JSONObject(config)
        val binding=ReadingLayoutBinding().apply {layout=ReadingLayout(root,p,0,config,emptyList(),loadReaderScript(context),readingLoader(context,root),null,size.getDouble("width").toFloat(),size.getDouble("height").toFloat(),"test")}
        val complete=java.util.concurrent.CountDownLatch(1)
        val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Main.immediate)
        var result:BookSearchResults?=null
        try {
            h.instrument.runOnMainSync {
                val host=h.find {it is ReadingTurnHost} as ReadingTurnHost
                scope.launch {try {result=BookSearchEngine(host,binding).search("needle",3) {}} finally {complete.countDown()}}
                host.invalidateLayout()
            }
            assertTrue(complete.await(30,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(5,result!!.hits.size)
        }finally {h.instrument.runOnMainSync {scope.cancel()}}
    }
}
