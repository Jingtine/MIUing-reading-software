package com.miuing.reader

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookSearchUiTest {
    private fun fixture(counts: List<Int> = listOf(2, 1, 2), paragraphs: Int = 0)=BookReaderHarness(counts=counts,sourceFactory={root,title->
        File(root,"search.epub").also {file->ZipOutputStream(file.outputStream()).use {zip->
            val entries=linkedMapOf("META-INF/container.xml" to "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                "book.opf" to "<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>$title</dc:title></metadata><manifest>"+counts.indices.joinToString(""){"<item id='c$it' href='c$it.xhtml' media-type='application/xhtml+xml'/>"}+"</manifest><spine>"+counts.indices.joinToString(""){"<itemref idref='c$it'/>"}+"</spine></package>")
            counts.forEachIndexed {chapter,count->entries["c$chapter.xhtml"]="<html><body><p>原文起点 $chapter</p>"+(0 until count).joinToString(""){"<p>needle 章节$chapter 命中$it</p>"}+"<p>长篇搜索样书，走过河流与群山，灯火照亮北方的道路。</p>".repeat(paragraphs)+"</body></html>"}
            entries.forEach {(name,text)->zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray());zip.closeEntry()}
        }}
    })
    @Test fun partialHitCanOpenAndLaterNavigateToAnotherChapter() = fixture(List(24) { if (it == 0 || it == 23) 1 else 0 }, 260).use { h ->
        val source = h.saved()
        h.device.readerAction("在图书中搜索").click()
        h.device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)!!.text = "needle"
        val row = h.device.wait(Until.findObject(By.desc("文字搜索结果 第 1 章 第 1 处")), 10000)!!
        assertNull("Expected a partial batch before the last chapter", h.device.findObject(By.text("找到 2 处匹配")))
        assertTrue("The first result should be clickable during scanning", row.isEnabled)
        row.click()
        h.await { h.js("!!document.querySelector('[data-miuing-search]')") == "true" }
        h.device.wait(Until.findObject(By.desc("下一处匹配").enabled(true)), 20000)!!.click()
        h.await { h.saved().chapter == 23 && h.js("!!document.querySelector('[data-miuing-search]')") == "true" }
        h.device.wait(Until.findObject(By.desc("关闭搜索导航").enabled(true)), 5000)!!.click()
        h.await { h.saved().chapter == source.chapter && h.saved().anchor == source.anchor }
    }

    @Test fun longBookSearchTiming() = fixture(List(24) { if (it == 0 || it == 23) 1 else 0 }, 260).use { h ->
        repeat(3) { run ->
            h.device.readerAction("在图书中搜索").click()
            val field = h.device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)!!
            val started = android.os.SystemClock.elapsedRealtime()
            field.text = "needle"
            assertNotNull(h.device.wait(Until.findObject(By.desc("文字搜索结果 第 1 章 第 1 处")), 10000))
            val first = android.os.SystemClock.elapsedRealtime()
            assertNotNull(h.device.wait(Until.findObject(By.text("找到 2 处匹配")), 30000))
            val complete = android.os.SystemClock.elapsedRealtime()
            android.util.Log.i("MIUingSearchTiming", "run=$run first_ms=${first - started} complete_ms=${complete - started} app_pss_kib=${android.os.Debug.getPss()}")
            field.text = ""
            assertTrue(h.device.wait(Until.gone(By.text("找到 2 处匹配")), 5000))
            h.device.pressBack()
            if (h.device.hasObject(By.clazz("android.widget.EditText"))) h.device.pressBack()
        }
    }
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
        val originalWidth = h.js("window.miuingConfig.width")
        try {
            h.device.setOrientationLeft()
            h.await { h.js("window.miuingConfig.width") != originalWidth && h.js("!!document.querySelector('[data-miuing-search]')") == "true" }
            h.device.wait(Until.findObject(By.desc("下一处匹配").enabled(true)),15000)!!.click()
            h.await {h.saved().chapter==2 && h.js("!!document.querySelector('[data-miuing-search]')")=="true"}
            h.device.wait(Until.findObject(By.desc("关闭搜索导航").enabled(true)),5000)!!.click()
            h.await {h.saved().chapter==source.chapter && h.saved().anchor==source.anchor}
            h.await {h.js("document.querySelector('[data-miuing-search]')===null")=="true"}
        } finally {
            h.device.setOrientationNatural()
        }
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
    @Test fun scannerRetriesItsChapterAfterLayoutInvalidation() = scanAfterPreviewInterruption { host, _, _ ->
        host.invalidateLayout()
    }

    @Test fun scannerRetriesAfterAnAbortedChapterDrag() = scanAfterPreviewInterruption { host, layout, web ->
        host.begin(web, 1, 1, 0.0, layout.preview(1), android.graphics.Color.WHITE)
        host.abort(web, 1)
    }

    @Test fun completedSearchIsReusedOnlyForTheSameLayout() = fixture().use { h ->
        h.await { h.footer() != null }
        val host = h.find { it is ReadingTurnHost } as ReadingTurnHost
        val original = readingConfig(h)
        val binding = ReadingLayoutBinding()
        runBlocking {
            withContext(Dispatchers.Main) {
                binding.layout = previewLayout(h, original)
                val engine = BookSearchEngine(host, binding)
                val first = engine.search("needle", 3) {}
                assertEquals(5, first.hits.size)
                binding.layout = previewLayout(h, original.put("search", "needle").put("searchIndex", 1))
                assertSame(first, engine.search("needle", 3) {})
                binding.layout = previewLayout(h, original.put("fontSize", 28))
                val reflowed = engine.search("needle", 3) {}
                assertNotSame(first, reflowed)
                assertEquals(first, reflowed)
            }
        }
    }

    private fun readingConfig(h: BookReaderHarness): org.json.JSONObject {
        val config = org.json.JSONTokener(h.js("JSON.stringify(window.miuingConfig)")).nextValue() as String
        return org.json.JSONObject(config)
    }

    private fun previewLayout(h: BookReaderHarness, config: org.json.JSONObject): ReadingLayout {
        val context = h.instrument.targetContext
        val root = File(File(h.book.source).parentFile, "content")
        val publication = LibraryStore(context).open(h.book)
        return ReadingLayout(root, publication, 0, config.toString(), emptyList(), loadReaderScript(context),
            readingLoader(context, root), null, config.getDouble("width").toFloat(), config.getDouble("height").toFloat(), "test")
    }

    private fun scanAfterPreviewInterruption(interrupt: (ReadingTurnHost, ReadingLayout, android.webkit.WebView) -> Unit) = fixture().use {h->
        h.await {h.footer()!=null}
        val binding = ReadingLayoutBinding().apply { layout = previewLayout(h, readingConfig(h)) }
        val complete=java.util.concurrent.CountDownLatch(1)
        val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Main.immediate)
        var result:BookSearchResults?=null
        try {
            h.instrument.runOnMainSync {
                val host=h.find {it is ReadingTurnHost} as ReadingTurnHost
                val web = host.getChildAt(0) as android.webkit.WebView
                scope.launch {try {result=BookSearchEngine(host,binding).search("needle",3) {}} finally {complete.countDown()}}
                interrupt(host, binding.layout!!, web)
            }
            assertTrue(complete.await(30,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(5,result!!.hits.size)
        }finally {h.instrument.runOnMainSync {scope.cancel()}}
    }
}
