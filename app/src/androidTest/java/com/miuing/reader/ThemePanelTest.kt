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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ThemePanelTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrument)
    private fun web(v:View):WebView? {if(v is WebView) return v;if(v is ViewGroup) repeat(v.childCount) {web(v.getChildAt(it))?.let {w->return w}};return null}
    private fun js(s:ActivityScenario<MainActivity>,code:String):String {
        val latch=CountDownLatch(1);var result=""
        s.onActivity {web(it.window.decorView)!!.evaluateJavascript(code) {result=it;latch.countDown()}}
        check(latch.await(10,TimeUnit.SECONDS));return result
    }
    private fun await(s:ActivityScenario<MainActivity>,condition:String) {
        val by=System.currentTimeMillis()+15000
        while(System.currentTimeMillis()<by) {if(runCatching {js(s,condition)=="true"}.getOrDefault(false)) {Thread.sleep(150);return};Thread.sleep(80)}
        fail("Theme reader condition timed out: $condition")
    }
    private fun text(value:String)=device.wait(Until.findObject(By.text(value)),5000) ?: throw AssertionError("Missing $value")
    private fun action(value:String)=device.wait(Until.findObject(By.desc(value)),5000) ?: throw AssertionError("Missing action $value")
    private fun reader(block:(ActivityScenario<MainActivity>,android.content.SharedPreferences,android.content.Context)->Unit)=runBlocking {
        val context=instrument.targetContext;val prefs=context.getSharedPreferences("reading",0);prefs.edit().clear().commit()
        val title="主题对照-${UUID.randomUUID().toString().take(8)}";val file=File(context.cacheDir,"theme-panel.epub")
        ZipOutputStream(file.outputStream()).use {z->
            mapOf("META-INF/container.xml" to "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                "book.opf" to "<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>$title</dc:title></metadata><manifest><item id='c' href='c.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='c'/></spine></package>",
                "c.xhtml" to "<html><body style='font-family:monospace'><h1>阅读主题</h1>"+(0..150).joinToString("") {"<p style='font-family:serif!important;text-align:center!important'><span style='font-family:serif!important;font-weight:400!important'>第${it}段。The reader should preserve this passage while customizing its theme. 主题设置不应丢失阅读位置。</span></p>"}+"</body></html>")
                .forEach {(name,data)->z.putNextEntry(ZipEntry(name));z.write(data.toByteArray());z.closeEntry()}
        }
        var id:String?=null
        try {
            val book=LibraryStore(context).import(Uri.fromFile(file));id=book.id
            ActivityScenario.launch(MainActivity::class.java).use {s->
                text(title).click();await(s,"!!window.miuingConfig")
                block(s,prefs,context)
            }
        } finally {id?.let {LibraryStore(context).delete(it)};file.delete();prefs.edit().clear().commit()}
    }
    @Test fun compactThemeHierarchyCommitsDiscardsRestoresAndResets()=reader {s,prefs,context->
                js(s,"window.miuingJump(.4)");Thread.sleep(400)
                val passage=js(s,"window.miuingAnchor()").toInt();assertTrue(passage>100)
                device.openReaderThemes()
                assertNotNull("Compact panel must have the original Customize entry",device.wait(Until.findObject(By.text("自定义")),5000))
                assertNull("Detailed layout sliders must live under Customize",device.findObject(By.desc("行距")))
                val original=text("原始").visibleBounds;val paper=text("纸张").visibleBounds;val bold=text("粗体").visibleBounds
                assertTrue("Theme cards must form two rows of three",kotlin.math.abs(original.top-paper.top)<5 && original.left<paper.left && bold.top>original.top)
                device.takeScreenshot(File(context.getExternalFilesDir(null),"themes-main.png"))
                action("增大字号").click();await(s,"window.miuingConfig.fontSize===21")
                action("减小字号").click();await(s,"window.miuingConfig.fontSize===20")
                action("外观").click();text("深色").click();await(s,"window.miuingConfig.paper==='#242426'")
                device.takeScreenshot(File(context.getExternalFilesDir(null),"themes-dark.png"))
                action("外观").click();text("浅色").click();await(s,"window.miuingConfig.paper==='#ffffff'")
                val bar=action("阅读亮度").visibleBounds;device.click(bar.left+bar.width()*3/4,bar.centerY())
                val brightBy=System.currentTimeMillis()+3000
                while(prefs.getFloat("brightness",-1f)<0 && System.currentTimeMillis()<brightBy) Thread.sleep(50)
                assertTrue("Brightness bar is not functional",prefs.getFloat("brightness",-1f)>0)
                action("外观").click();text("使用系统亮度").click()
                text("自定义").click();action("选择字体").click();text("无衬线字体").click();text("完成").click()
                action("粗体文本").click();action("自定义布局").click()
                if(!device.hasObject(By.desc("使文本两端对齐"))) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().description("使文本两端对齐"))
                action("使文本两端对齐").click()
                assertEquals("Unsaved font must not leak into the reader","book",ThemeOptionsStore(prefs).load(0).font)
                device.takeScreenshot(File(context.getExternalFilesDir(null),"themes-customize.png"))
                s.recreate();text("自定义主题")
                assertEquals("Unsaved draft committed during recreation","book",ThemeOptionsStore(prefs).load(0).font)
                text("完成").click();await(s,"window.miuingConfig.fontFamily==='sans' && window.miuingConfig.bold && window.miuingConfig.justify")
                assertEquals("Draft choices were not rendered","true",js(s,"getComputedStyle(document.body).fontFamily.includes('sans-serif') && getComputedStyle(document.querySelector('p')).textAlign==='justify' && parseInt(getComputedStyle(document.querySelector('p')).fontWeight)>=700"))
                assertEquals("Publisher paragraph and inline fonts defeated the chosen font","true",js(s,"getComputedStyle(document.querySelector('p')).fontFamily==='sans-serif' && getComputedStyle(document.querySelector('p span')).fontFamily==='sans-serif' && parseInt(getComputedStyle(document.querySelector('p span')).fontWeight)>=700"))
                text("纸张").click();await(s,"window.miuingConfig.fontFamily==='serif'")
                text("原始").click();await(s,"window.miuingConfig.fontFamily==='sans'")
                text("自定义").click();action("选择字体").click();text("等宽字体").click();text("完成").click()
                device.pressBack();text("放弃未存储的主题更改？");text("放弃更改").click()
                assertEquals("Discard changed the saved font","sans",ThemeOptionsStore(prefs).load(0).font)
                text("自定义").click()
                if(!device.hasObject(By.text("还原主题"))) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("还原主题"))
                text("还原主题").click();text("完成").click();await(s,"window.miuingConfig.fontFamily==='book' && !window.miuingConfig.bold")
                assertEquals("Original must respect the publisher's actual font","true",js(s,"getComputedStyle(document.body).fontFamily.includes('monospace')"))
                assertEquals("Original did not restore the publisher's inline typography","true",js(s,"getComputedStyle(document.querySelector('p')).fontFamily==='serif' && getComputedStyle(document.querySelector('p')).textAlign==='center' && getComputedStyle(document.querySelector('p span')).fontWeight==='400'"))
                assertEquals("serif",ThemeOptionsStore(prefs).load(2).font)
                action("关闭主题与设置").click()
                val after=js(s,"window.miuingAnchor()").toInt()
                assertTrue("Theme changes lost the passage: before=$passage, after=$after",kotlin.math.abs(after-passage)<200)
    }
    @Test fun shortPanelOpensCustomizationAtItsHeading()=reader {s,_,context->
        device.openReaderThemes()
        try {
            device.setOrientationLeft();text("主题与设置")
            if(!device.hasObject(By.text("自定义"))) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("自定义"))
            text("自定义").click()
            val font=action("选择字体").visibleBounds
            assertTrue("A new customization screen inherited the main panel's scroll offset",font.height()>=(40*context.resources.displayMetrics.density).toInt())
            device.takeScreenshot(File(context.getExternalFilesDir(null),"themes-short.png"))
        } finally {device.setOrientationNatural()}
    }

}
