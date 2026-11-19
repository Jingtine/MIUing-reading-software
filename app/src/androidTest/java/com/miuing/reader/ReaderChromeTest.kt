package com.miuing.reader

import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ReaderChromeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) repeat(view.childCount) { web(view.getChildAt(it))?.let { found -> return found } }
        return null
    }

    private fun js(scenario: ActivityScenario<MainActivity>, code: String): String {
        val done = CountDownLatch(1)
        var result = ""
        scenario.onActivity { web(it.window.decorView)!!.evaluateJavascript(code) { value -> result = value; done.countDown() } }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun await(scenario: ActivityScenario<MainActivity>, condition: String) {
        val deadline = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < deadline) {
            if (runCatching { js(scenario, condition) == "true" }.getOrDefault(false)) return
            Thread.sleep(80)
        }
        fail("Reader condition timed out: $condition")
    }

    private fun action(description: String) = device.wait(Until.findObject(By.desc(description)), 5000)
        ?: throw AssertionError("Missing action: $description")

    private fun text(value: String) = device.wait(Until.findObject(By.text(value)), 5000)
        ?: throw AssertionError("Missing text: $value")

    private fun displayedPages(): Pair<Int, Int> {
        val label = device.wait(Until.findObject(By.descStartsWith("本章第 ")), 5000)?.contentDescription
            ?: throw AssertionError("Missing native page status")
        val values = Regex("本章第 (\\d+) 页，共 (\\d+) 页").matchEntire(label)?.groupValues
            ?: throw AssertionError("Invalid page status: $label")
        return values[1].toInt() to values[2].toInt()
    }

    private fun screenshot(scenario: ActivityScenario<MainActivity>, name: String) {
        val ready = CountDownLatch(1)
        scenario.onActivity {
            web(it.window.decorView)!!.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { ready.countDown() }
            })
        }
        assertTrue("WebView did not finish drawing the current DOM", ready.await(10, TimeUnit.SECONDS))
        device.waitForIdle()
        Thread.sleep(250) // Let the dialog exit/ripple finish before recording visual evidence.
        device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), name))
    }

    private fun assertPaperMargins(scenario: ActivityScenario<MainActivity>, name: String = "text-reader-chrome.png", minimum: Int = 240) {
        val location = IntArray(2)
        var width = 0
        var height = 0
        scenario.onActivity {
            val reader = web(it.window.decorView)!!
            reader.getLocationOnScreen(location)
            width = reader.width
            height = reader.height
        }
        val bitmap = BitmapFactory.decodeFile(File(instrumentation.targetContext.getExternalFilesDir(null), name).path)
        try {
            for (step in 1..9) for (x in listOf(location[0] + 5, location[0] + width - 6)) {
                val y = location[1] + height * step / 10
                val pixel = bitmap.getPixel(x, y)
                assertTrue("Blank paper rendered a dark tile at $x,$y: ${Integer.toHexString(pixel)}", Color.red(pixel) > minimum && Color.green(pixel) > minimum && Color.blue(pixel) > minimum)
            }
        } finally { bitmap.recycle() }
    }

    private fun reader(block: (ActivityScenario<MainActivity>, LibraryStore, Book) -> Unit) = runBlocking {
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("reading", 0)
        preferences.edit().clear().commit()
        val title = "阅读控件-${UUID.randomUUID().toString().take(8)}"
        val file = File(context.cacheDir, "reader-chrome.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            val entries = linkedMapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                "book.opf" to "<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>$title</dc:title></metadata><manifest><item id='a' href='a.xhtml' media-type='application/xhtml+xml'/><item id='b' href='b.xhtml' media-type='application/xhtml+xml'/><item id='nav' href='nav.xhtml' media-type='application/xhtml+xml' properties='nav'/></manifest><spine><itemref idref='a'/><itemref idref='b'/></spine></package>",
                "nav.xhtml" to "<html xmlns:epub='http://www.idpf.org/2007/ops'><body><nav epub:type='toc'><ol><li><a href='a.xhtml'>第一章</a></li><li><a href='b.xhtml'>第二章</a></li></ol></nav></body></html>"
            )
            listOf("a", "b").forEachIndexed { chapter, name ->
                entries["$name.xhtml"] = "<html><body><h1 id='chapter$chapter'>Chapter $chapter</h1>" +
                    (0..140).joinToString("") { "<p>第${it}段。A readable needle in the chapter. 阅读控件应该浮在正文上方，显示和隐藏均保持原文位置。</p>" } + "</body></html>"
            }
            entries.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry() }
        }
        val store = LibraryStore(context)
        var book: Book? = null
        try {
            book = store.import(Uri.fromFile(file))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                text(title).click()
                await(scenario, "!!window.miuingConfig")
                block(scenario, store, book)
            }
        } finally {
            book?.let { store.delete(it.id) }
            file.delete()
            preferences.edit().clear().commit()
        }
    }

    @Test fun overlaysDoNotResizeOrMoveThePassage() = reader { scenario, _, _ ->
        val menu = action("阅读菜单").visibleBounds
        val close = action("返回书库").visibleBounds
        assertTrue("Close must be at the top right", close.centerX() > device.displayWidth * .75)
        assertTrue("Reading menu must be at the bottom right", menu.centerX() > device.displayWidth * .75 && menu.centerY() > device.displayHeight * .75)
        assertNull("Old inline typography button is still on the page", device.findObject(By.text("aA")))
        assertNull("Chapter navigation belongs in the menu", device.findObject(By.desc("下一章")))
        assertNotNull("Actual chapter pages are missing", device.wait(Until.findObject(By.descStartsWith("本章第 ")), 5000))
        js(scenario, "window.miuingJump(.4)")
        Thread.sleep(350)
        val dimensions = js(scenario, "JSON.stringify([window.miuingWidth,window.miuingHeight])")
        val anchor = js(scenario, "window.miuingAnchor()").toInt()
        repeat(3) {
            js(scenario, "MIUing.tap()")
            assertTrue(device.wait(Until.gone(By.desc("阅读菜单")), 5000))
            assertEquals(dimensions, js(scenario, "JSON.stringify([window.miuingWidth,window.miuingHeight])"))
            js(scenario, "MIUing.tap()")
            action("阅读菜单")
        }
        assertEquals("Control visibility moved the passage", anchor, js(scenario, "window.miuingAnchor()").toInt())
        action("阅读菜单").click()
        scenario.recreate()
        action("关闭阅读菜单")
        action("关闭阅读菜单").click()
        assertEquals(dimensions, js(scenario, "JSON.stringify([window.miuingWidth,window.miuingHeight])"))
        assertEquals("Menu/recreation lost the passage", anchor, js(scenario, "window.miuingAnchor()").toInt())
        assertTrue(device.wait(Until.gone(By.desc("关闭阅读菜单")), 5000))
        screenshot(scenario, "text-reader-chrome.png")
        assertPaperMargins(scenario)
    }

    @Test fun menuRoutesToExistingReadingToolsAndPersistsBookmarks() = reader { scenario, store, book ->
        action("阅读菜单").click()
        action("添加书签").click()
        text("移除书签")
        assertEquals(1, LibraryStore(instrumentation.targetContext).current(book.id).marks.size)
        screenshot(scenario, "text-reader-menu.png")
        assertPaperMargins(scenario, "text-reader-menu.png", minimum = 40)
        text("主题与设置").click()
        action("关闭主题与设置")
        assertNull("Two menus were stacked", device.findObject(By.desc("关闭阅读菜单")))
        action("关闭主题与设置").click()
        action("阅读菜单").click()
        action("下一章").click()
        await(scenario, "!!document.getElementById('chapter1') && !!window.miuingConfig")
        action("阅读菜单").click()
        action("上一章").click()
        await(scenario, "!!document.getElementById('chapter0') && !!window.miuingConfig")
        action("阅读菜单").click()
        action("目录与书签").click()
        text("第二章").click()
        await(scenario, "!!document.getElementById('chapter1') && !!window.miuingConfig")
        action("阅读菜单").click()
        action("在图书中搜索").click()
        assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000))
        device.pressBack()
    }

    @Test fun shortLandscapeMenuKeepsItsCloseAndScrolledActionsReachable() = reader { scenario, _, _ ->
        try {
            device.setOrientationLeft()
            action("阅读菜单").click()
            action("关闭阅读菜单")
            if (!device.hasObject(By.desc("高亮标记和笔记"))) {
                UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().description("高亮标记和笔记"))
            }
            val notes = action("高亮标记和笔记")
            assertTrue("Scrolled action has no tappable bounds", notes.visibleBounds.height() > 20)
            action("关闭阅读菜单")
            screenshot(scenario, "text-reader-menu-short.png")
            notes.click()
            text("高亮标记和笔记")
            assertNull("The menu stayed open over Notes", device.findObject(By.desc("关闭阅读菜单")))
        } finally {
            device.setOrientationNatural()
        }
    }

    @Test fun menuSeekPersistsTheActualAnchorAndBookmarkRestoresIt() = reader { scenario, _, book ->
        action("阅读菜单").click()
        val slider = action("阅读进度").visibleBounds
        device.click(slider.left + slider.width() * 55 / 100, slider.centerY())
        assertTrue(device.wait(Until.gone(By.desc("关闭阅读菜单")), 5000))
        await(scenario, "window.miuingProgress()>.3 && window.miuingProgress()<.7")
        Thread.sleep(250)
        val anchor = js(scenario, "window.miuingAnchor()").toInt()
        assertTrue(anchor > 100)
        assertEquals("Slider seek persisted a different passage", anchor, LibraryStore(instrumentation.targetContext).current(book.id).anchor)
        action("阅读菜单").click()
        action("添加书签").click()
        action("移除书签")
        val saved = LibraryStore(instrumentation.targetContext).current(book.id).marks.single()
        assertEquals("Bookmark lost the actual text anchor", anchor, saved.anchor)
        action("关闭阅读菜单").click()
        js(scenario, "window.miuingJump(0)")
        Thread.sleep(200)
        action("阅读菜单").click()
        action("目录与书签").click()
        text("书签").click()
        text(saved.label).click()
        await(scenario, "window.miuingProgress()>.3")
        assertTrue("Bookmark did not return to the selected passage", kotlin.math.abs(js(scenario, "window.miuingAnchor()").toInt() - saved.anchor) <= 5)
    }

    @Test fun nativePageStatusTracksEndpointsAndFontReflow() = reader { scenario, _, _ ->
        val initial = displayedPages()
        assertEquals(1, initial.first)
        assertTrue(initial.second > 1)
        js(scenario, "window.miuingJump(1)")
        action("本章第 ${initial.second} 页，共 ${initial.second} 页")
        device.openReaderThemes()
        repeat(4) { action("增大字号").click() }
        action("关闭主题与设置").click()
        val larger = displayedPages()
        assertTrue("Native page count did not respond to actual font reflow", larger.second > initial.second)
        js(scenario, "window.miuingJump(1)")
        action("本章第 ${larger.second} 页，共 ${larger.second} 页")
        js(scenario, "window.miuingJump(0)")
        action("本章第 1 页，共 ${larger.second} 页")
    }
}
