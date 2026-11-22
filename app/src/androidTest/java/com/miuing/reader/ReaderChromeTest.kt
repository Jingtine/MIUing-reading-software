package com.miuing.reader

import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.WindowCompat
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.toArgb
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
    private fun storedBook(id:String):Book {
        val context=instrumentation.targetContext
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()) return LibraryStore(context).current(id)
        lateinit var result:Book;instrumentation.runOnMainSync {result=LibraryStore(context).current(id)};return result
    }
    private fun awaitWholeProgress(minimum:Double=0.0,maximum:Double=1.0) {
        val deadline=System.currentTimeMillis()+15000
        while(System.currentTimeMillis()<deadline) {
            val label=device.findObject(By.descStartsWith("全书第 "))?.contentDescription
            val match=label?.let {Regex("全书第 (\\d+)(?: 至 (\\d+))? 页，共 (\\d+) 页").matchEntire(it)}
            if(match!=null) {
                val first=match.groupValues[1].toDouble();val last=match.groupValues[2].toDoubleOrNull() ?: first;val total=match.groupValues[3].toDouble()
                val progress=if(last>=total || total<=1) 1.0 else (first-1)/(total-1)
                if(progress in minimum..maximum) return
            }
            Thread.sleep(50)
        }
        fail("Whole-book progress did not reach $minimum..$maximum")
    }

    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) repeat(view.childCount) { web(view.getChildAt(it))?.let { found -> return found } }
        return null
    }

    private fun keepsScreenOn(view: View): Boolean = view.keepScreenOn ||
        (view is ViewGroup && (0 until view.childCount).any { keepsScreenOn(view.getChildAt(it)) })

    @Test fun readingSleepSettingPersistsAndReleasesOutsideReading() = reader { scenario, _, book ->
        fun assertAwake(expected: Boolean) {
            val deadline = System.currentTimeMillis() + 5000
            var actual = !expected
            while (actual != expected && System.currentTimeMillis() < deadline) {
                scenario.onActivity { actual = keepsScreenOn(it.window.decorView) }
                if (actual != expected) Thread.sleep(40)
            }
            assertEquals(expected, actual)
        }
        assertAwake(true)
        action("阅读菜单").click()
        action("阅读设置").click()
        screenshot(scenario,"reading-sleep-settings.png")
        action("阅读时延长亮屏").click()
        assertAwake(false)
        scenario.onActivity { assertFalse(it.getSharedPreferences("reading",0).getBoolean("extendSleep",true)) }
        scenario.recreate()
        await(scenario,"!!window.miuingConfig")
        assertAwake(false)
        action("阅读菜单").click(); action("阅读设置").click()
        action("阅读时延长亮屏").click()
        device.pressBack()
        assertAwake(true)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        assertAwake(false)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        assertAwake(true)
        action("返回书库").click()
        text(book.title)
        assertAwake(false)
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
        fail("Reader condition timed out: $condition; state=" + runCatching { js(scenario, "JSON.stringify({config:window.miuingConfig,progress:window.miuingConfig?window.miuingProgress():null,hit:!!document.querySelector('[data-miuing-search]')})") }.getOrDefault("unavailable"))
    }

    private fun action(description: String) = device.wait(Until.findObject(By.desc(description)), 5000)
        ?: throw AssertionError("Missing action: $description")

    private fun text(value: String) = device.wait(Until.findObject(By.text(value)), 5000)
        ?: throw AssertionError("Missing text: $value")

    private fun clickSearchResult(chapter: Int = 0) {
        val selector = By.desc("文字搜索结果 第 ${chapter + 1} 章")
        val deadline = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < deadline) {
            device.waitForIdle()
            try {
                val result = device.findObject(selector)
                if (result != null) {
                    result.parent.click()
                    // Verify delivery; IME/layout transitions can consume a tap.
                    if (device.wait(Until.gone(selector), 700)) return
                }
            } catch (_: androidx.test.uiautomator.StaleObjectException) { }
            Thread.sleep(50)
        }
        fail("Search result could not be selected: chapter $chapter")
    }
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

    private fun reader(beforeOpen: (ActivityScenario<MainActivity>) -> Unit = {}, block: (ActivityScenario<MainActivity>, LibraryStore, Book) -> Unit) = runBlocking {
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
                    (0..140).joinToString("") { "<p id='p$it'>第${it}段。A readable needle in the chapter. 阅读控件应该浮在正文上方，显示和隐藏均保持原文位置。${if(it==100) "<span>定位${chapter} [<em>A+B</em>] &amp; 😀 alpha beta Kelvin</span>" else ""}</p>" } + "</body></html>"
            }
            entries.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry() }
        }
        val store = LibraryStore(context)
        var book: Book? = null
        try {
            book = store.import(Uri.fromFile(file))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                beforeOpen(scenario)
                text(title).click()
                await(scenario, "!!window.miuingConfig")
                js(scenario,"window.fixtureReady=false;(document.fonts?document.fonts.ready:Promise.resolve()).then(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>window.fixtureReady=true)))))")
                await(scenario,"window.fixtureReady===true")
                block(scenario, store, book)
            }
        } finally {
            book?.let { store.delete(it.id) }
            file.delete()
            preferences.edit().clear().commit()
        }
    }

    @Test fun statusTextHasReadableContrastInEveryTheme() {
        val palettes=ReadingThemes+ReadingThemes.indices.map { readingTheme(it,"dark",false) }
        for(theme in palettes) {
            val background=theme.paper.toArgb()
            val foreground=ColorUtils.compositeColors(readerStatusColor(theme).toArgb(),background)
            val contrast=ColorUtils.calculateContrast(foreground,background)
            assertTrue("${theme.name} status contrast=$contrast",contrast>=4.5)
        }
    }

    @Suppress("DEPRECATION")
    @Test fun systemBarIconsFollowReadingAppearanceAndRestoreLibrary() {
        var originalStatus=false
        var originalNavigation=false
        var originalStatusColor=0
        var originalNavigationColor=0
        reader(beforeOpen={ scenario -> scenario.onActivity {
            val controller=WindowCompat.getInsetsController(it.window,it.window.decorView)
            originalStatus=controller.isAppearanceLightStatusBars
            originalNavigation=controller.isAppearanceLightNavigationBars
            originalStatusColor=it.window.statusBarColor
            originalNavigationColor=it.window.navigationBarColor
        } }) { scenario, _, book ->
            device.openReaderThemes()
            action("外观").click();text("深色").click()
            action("关闭主题与设置").click()
            scenario.onActivity {
                val controller=WindowCompat.getInsetsController(it.window,it.window.decorView)
                assertFalse("Dark reading has dark status icons",controller.isAppearanceLightStatusBars)
                assertFalse("Dark reading has dark navigation icons",controller.isAppearanceLightNavigationBars)
            }
            action("阅读菜单").click();action("在图书中搜索").click()
            assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000))
            screenshot(scenario,"text-reader-search-dark.png")
            device.pressBack()
            assertTrue(device.wait(Until.gone(By.clazz("android.widget.EditText")),5000))
            action("返回书库").click();text(book.title)
            scenario.onActivity {
                val controller=WindowCompat.getInsetsController(it.window,it.window.decorView)
                assertEquals(originalStatus,controller.isAppearanceLightStatusBars)
                assertEquals(originalNavigation,controller.isAppearanceLightNavigationBars)
                if(android.os.Build.VERSION.SDK_INT<35) {
                    assertEquals(originalStatusColor,it.window.statusBarColor)
                    assertEquals(originalNavigationColor,it.window.navigationBarColor)
                }
            }
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
        device.openReaderThemes()
        action("外观").click()
        text("深色").click()
        action("关闭主题与设置").click()
        screenshot(scenario,"text-reader-chrome-dark.png")
    }

    @Test fun menuRoutesToExistingReadingToolsAndPersistsBookmarks() = reader { scenario, store, book ->
        action("阅读菜单").click()
        action("添加书签").click()
        text("移除书签")
        assertEquals(1, storedBook(book.id).marks.size)
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
        awaitWholeProgress()
        action("阅读菜单").click()
        val slider = action("阅读进度").visibleBounds
        device.click(slider.left + slider.width() * 55 / 100, slider.centerY())
        assertTrue(device.wait(Until.gone(By.desc("关闭阅读菜单")), 5000))
        awaitWholeProgress(.3,.7)
        Thread.sleep(250)
        val anchor = js(scenario, "window.miuingAnchor()").toInt()
        assertTrue(anchor > 100)
        assertEquals("Slider seek persisted a different passage", anchor, storedBook(book.id).anchor)
        action("阅读菜单").click()
        action("添加书签").click()
        action("移除书签")
        val saved = storedBook(book.id).marks.single()
        assertEquals("Bookmark lost the actual text anchor", anchor, saved.anchor)
        action("关闭阅读菜单").click()
        js(scenario, "window.miuingJump(0)")
        Thread.sleep(200)
        action("阅读菜单").click()
        action("目录与书签").click()
        text("书签").click()
        text(saved.label).click()
        await(scenario, "!!window.miuingConfig && window.miuingConfig.anchor===${saved.anchor} && window.miuingAnchor()>100")
        screenshot(scenario, "bookmark-restored.png")
        val restored = js(scenario, "window.miuingAnchor()").toInt()
        assertTrue("Bookmark did not return to the selected passage: saved=${saved.anchor}, restored=$restored", kotlin.math.abs(restored - saved.anchor) <= 5)
        action("返回跳转前位置").click()
        await(scenario, "!!window.miuingConfig && window.miuingConfig.position===0")
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

    @Test fun systemBackDismissesToolsBeforeLeavingTheBook() = reader { _, _, book ->
        action("阅读菜单").click()
        action("关闭阅读菜单")
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.desc("关闭阅读菜单")), 5000))
        action("阅读菜单")
        device.openReaderThemes()
        action("关闭主题与设置")
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.desc("关闭主题与设置")), 5000))
        action("阅读菜单")
        device.pressBack()
        text(book.title)
        assertNull("Back did not return to the library", device.findObject(By.desc("阅读菜单")))
    }

    @Test fun deliberateJumpsReturnThroughHistoryAfterRecreation() = reader { scenario, _, book ->
        js(scenario, "window.miuingJump(.35)")
        await(scenario, "window.miuingProgress()>.2")
        Thread.sleep(250)
        val origin = storedBook(book.id).anchor
        val viewport = js(scenario, "JSON.stringify([window.miuingWidth,window.miuingHeight])")
        assertTrue(origin > 100)
        assertNull("Ordinary movement added history", device.findObject(By.desc("返回跳转前位置")))
        device.openReaderThemes()
        action("增大字号").click()
        action("关闭主题与设置").click()
        assertNull("Typography added navigation history", device.findObject(By.desc("返回跳转前位置")))
        action("阅读菜单").click()
        action("下一章").click()
        await(scenario, "!!document.getElementById('chapter1') && !!window.miuingConfig")
        js(scenario, "window.miuingJump(.3)")
        Thread.sleep(250)
        val second = storedBook(book.id).anchor
        action("阅读菜单").click()
        action("目录与书签").click()
        text("第一章").click()
        await(scenario, "!!document.getElementById('chapter0') && !!window.miuingConfig")
        action("返回跳转前位置").click()
        await(scenario, "!!document.getElementById('chapter1') && !!window.miuingConfig && window.miuingConfig.anchor===$second")
        scenario.recreate()
        await(scenario, "!!document.getElementById('chapter1') && !!window.miuingConfig")
        action("返回跳转前位置").click()
        await(scenario, "!!document.getElementById('chapter0') && !!window.miuingConfig && window.miuingConfig.anchor===$origin")
        assertEquals(viewport, js(scenario, "JSON.stringify([window.miuingWidth,window.miuingHeight])"))
        assertTrue(device.wait(Until.gone(By.desc("返回跳转前位置")), 5000))
        action("阅读菜单").click()
        action("下一章").click()
        await(scenario, "!!document.getElementById('chapter1') && !!window.miuingConfig")
        action("返回书库").click()
        text(book.title).click()
        await(scenario, "!!window.miuingConfig")
        assertNull("Reopening the book inherited session history", device.findObject(By.desc("返回跳转前位置")))
    }

    @Test fun seekReturnsToThePassageBeforeSliderPreview() = reader { scenario, _, book ->
        js(scenario, "window.miuingJump(.25)")
        await(scenario,"window.miuingProgress()>.15")
        val savedDeadline=System.currentTimeMillis()+5000
        while(storedBook(book.id).anchor<=100 && System.currentTimeMillis()<savedDeadline) Thread.sleep(40)
        val origin = storedBook(book.id).anchor
        val viewport = js(scenario,"JSON.stringify([window.miuingWidth,window.miuingHeight])")
        assertTrue("Origin=$origin, glyph=${js(scenario,"window.miuingAnchor()")}, progress=${js(scenario,"window.miuingProgress()")}",origin > 100)
        awaitWholeProgress()
        action("阅读菜单").click()
        val slider = action("阅读进度").visibleBounds
        device.click(slider.left + slider.width() * 75 / 100, slider.centerY())
        awaitWholeProgress(.65,1.0)
        val back = action("返回跳转前位置").visibleBounds
        val close = action("返回书库").visibleBounds
        assertTrue("Original return entry belongs at the top left",back.centerX()<device.displayWidth*.25 && back.centerY()<device.displayHeight*.2)
        assertTrue("Return and close overlap",back.right<close.left)
        assertEquals(viewport,js(scenario,"JSON.stringify([window.miuingWidth,window.miuingHeight])"))
        screenshot(scenario, "text-reader-history.png")
        action("返回跳转前位置").click()
        await(scenario, "!!window.miuingConfig && window.miuingConfig.anchor===$origin")
        Thread.sleep(250)
        assertEquals(origin, storedBook(book.id).anchor)
        assertTrue(device.wait(Until.gone(By.desc("返回跳转前位置")), 5000))
    }

    @Test fun unstyledTextUsesSerifFallbackAndKeepsPublisherFont() = reader { scenario, _, _ ->
        val metrics=js(scenario,"(()=>{const widths=['inherit','serif','sans-serif'].map(family=>{const span=document.createElement('span');span.textContent='iiiiWWWWmm123';span.style.cssText='position:absolute;white-space:pre;font-size:32px;font-weight:400;letter-spacing:0;font-family:'+family;document.body.appendChild(span);const width=span.getBoundingClientRect().width;span.remove();return width});return JSON.stringify(widths)})()")
        val widths=org.json.JSONArray(org.json.JSONTokener(metrics).nextValue() as String)
        println("READER_FALLBACK widths=$widths")
        assertEquals("Unstyled book text must use the system serif fallback",widths.getDouble(1),widths.getDouble(0),.2)
        js(scenario,"document.getElementById('p0').style.fontFamily='monospace'")
        device.openReaderThemes()
        action("增大字号").click()
        action("关闭主题与设置").click()
        await(scenario,"getComputedStyle(document.getElementById('p0')).fontFamily.indexOf('monospace')>=0")
    }

    @Test fun searchTypingKeepsSourceAndSelectedHitReturnsAfterRecreation() = reader { scenario, _, book ->
        js(scenario, "window.miuingJump(.25)")
        Thread.sleep(300)
        val origin = storedBook(book.id).anchor
        val expected = js(scenario, "(()=>{const r=document.createRange();r.selectNodeContents(document.getElementById('miuing-content'));r.setEnd(document.querySelector('#p100 span'),0);return r.toString().length})()").toInt()
        action("阅读菜单").click()
        action("在图书中搜索").click()
        device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)!!.text = "定位0 [a+b] & 😀"
        assertNotNull(device.wait(Until.findObject(By.desc("文字搜索结果 第 1 章")), 5000))
        Thread.sleep(300)
        assertEquals("Typing moved the source passage", origin, storedBook(book.id).anchor)
        clickSearchResult()
        await(scenario, "!!window.miuingConfig && !!document.querySelector('[data-miuing-search]')")
        // Marks are installed synchronously; restoration and the native bridge
        // publish after animation frames. Wait for the actual saved destination.
        val savedDeadline = System.currentTimeMillis() + 5000
        while (storedBook(book.id).anchor != expected && System.currentTimeMillis() < savedDeadline) Thread.sleep(40)
        assertEquals(expected, storedBook(book.id).anchor)
        assertEquals("Search hit is outside the visible page", "true", js(scenario, "(()=>{const r=document.querySelector('[data-miuing-search]').getBoundingClientRect();return r.left>=0 && r.left<window.miuingWidth && r.top>=0 && r.top<window.miuingHeight})()"))
        screenshot(scenario, "text-reader-search.png")
        js(scenario, "window.miuingJump(.9)")
        Thread.sleep(300)
        val continued = storedBook(book.id).anchor
        scenario.recreate()
        await(scenario, "!!window.miuingConfig && window.miuingConfig.anchor===$continued && !!document.querySelector('[data-miuing-search]')")
        action("返回跳转前位置").click()
        await(scenario, "!!window.miuingConfig && window.miuingConfig.anchor===$origin && !document.querySelector('[data-miuing-search]')")
        assertTrue(device.wait(Until.gone(By.desc("返回跳转前位置")), 5000))
    }

    @Test fun crossChapterSearchLocatesHitAndNaturalChapterMovementDoesNotReuseIt() = reader { scenario, _, book ->
        js(scenario, "window.miuingJump(.3)")
        Thread.sleep(300)
        val origin = storedBook(book.id).anchor
        action("阅读菜单").click()
        action("在图书中搜索").click()
        device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)!!.text = "定位1 [a+b] & 😀"
        clickSearchResult(1)
        await(scenario, "!!document.getElementById('chapter1') && !!window.miuingConfig && window.miuingProgress()>.5")
        action("返回跳转前位置").click()
        await(scenario, "!!document.getElementById('chapter0') && !!window.miuingConfig && window.miuingConfig.anchor===$origin")
        js(scenario, "MIUing.chapter(1)")
        await(scenario, "!!document.getElementById('chapter1') && !!window.miuingConfig && window.miuingProgress()<.1 && !document.querySelector('[data-miuing-search]')")
    }

    @Test fun searchDiscoveryUsesEquivalentWhitespaceAndUnicodeCase() = reader { scenario, _, book ->
        js(scenario, "window.miuingJump(.2)")
        Thread.sleep(300)
        val origin = storedBook(book.id).anchor
        for (query in listOf("alpha  beta", "kelvin")) {
            println("SEARCH_DISCOVERY query=$query")
            action("阅读菜单").click()
            action("在图书中搜索").click()
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)!!.text = query
            clickSearchResult()
            await(scenario, "!!window.miuingConfig && !!document.querySelector('[data-miuing-search]') && window.miuingProgress()>.5")
            action("返回跳转前位置").click()
            await(scenario, "!!window.miuingConfig && !document.querySelector('[data-miuing-search]')")
            // The return target is saved before the new WebView restores it.
            // Wait through the renderer's three-frame restoration before the next jump.
            js(scenario, "window.testRestored=false;requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>window.testRestored=true))))")
            await(scenario, "window.testRestored===true")
            val deadline = System.currentTimeMillis() + 5000
            while (storedBook(book.id).anchor != origin && System.currentTimeMillis() < deadline) Thread.sleep(40)
            assertEquals(origin, storedBook(book.id).anchor)
        }
    }

    @Test fun unreadableChapterStillAllowsReturnToTheSource() = reader { scenario, _, book ->
        js(scenario, "window.miuingJump(.3)")
        Thread.sleep(300)
        val origin = storedBook(book.id).anchor
        val chapterFile = File(File(book.source).parentFile, "content/b.xhtml")
        assertTrue("Fixture chapter was not extracted", chapterFile.delete())
        action("阅读菜单").click()
        action("下一章").click()
        assertNotNull("Chapter error was not displayed", device.wait(Until.findObject(By.textStartsWith("无法读取此章节")), 5000))
        action("阅读菜单").click()
        val slider = action("阅读进度").visibleBounds
        device.click(slider.centerX(), slider.centerY())
        assertTrue(device.wait(Until.gone(By.desc("关闭阅读菜单")), 5000))
        device.openReaderThemes()
        action("翻页样式").click()
        action("连续滚动").click()
        action("关闭主题与设置").click()
        action("返回跳转前位置").click()
        await(scenario, "!!document.getElementById('chapter0') && !!window.miuingConfig && window.miuingConfig.anchor===$origin")
        assertTrue(device.wait(Until.gone(By.desc("返回跳转前位置")), 5000))
    }

    @Test fun linksSearchAndNotesShareTheReturnPath() = reader { scenario, _, book ->
        js(scenario, "window.miuingJump(.2)")
        Thread.sleep(300)
        val origin = storedBook(book.id).anchor
        js(scenario, "(()=>{const link=document.createElement('a');link.href='b.xhtml#chapter1';document.getElementById('miuing-content').appendChild(link);link.click()})()")
        await(scenario, "!!document.getElementById('chapter1') && !!window.miuingConfig")
        action("返回跳转前位置").click()
        await(scenario, "!!document.getElementById('chapter0') && !!window.miuingConfig && window.miuingConfig.anchor===$origin")
        action("阅读菜单").click()
        action("在图书中搜索").click()
        device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)!!.text = "needle"
        device.wait(Until.findObject(By.textContains("A readable needle")), 5000)!!.click()
        await(scenario, "!!window.miuingConfig && window.miuingConfig.position===0")
        action("返回跳转前位置").click()
        await(scenario, "!!window.miuingConfig && window.miuingConfig.anchor===$origin")
        js(scenario, "(()=>{const p=document.querySelector('#miuing-content p'),range=document.createRange();range.setStart(p.firstChild,0);range.setEnd(p.firstChild,3);getSelection().removeAllRanges();getSelection().addRange(range);document.dispatchEvent(new Event('selectionchange'))})()")
        text("高亮标记").click()
        val note = storedBook(book.id).notes.single()
        js(scenario, "getSelection().removeAllRanges();window.miuingJump(.6)")
        Thread.sleep(300)
        val noteOrigin = storedBook(book.id).anchor
        action("阅读菜单").click()
        action("高亮标记和笔记").click()
        text("前往原文").click()
        await(scenario, "!!window.miuingConfig && window.miuingConfig.anchor===${note.start}")
        action("返回跳转前位置").click()
        await(scenario, "!!window.miuingConfig && window.miuingConfig.anchor===$noteOrigin")
    }
}
