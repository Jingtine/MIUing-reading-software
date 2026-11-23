package com.miuing.reader

import android.view.View
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MultiformatReaderTest {
    private fun screenshot(h:BookReaderHarness,name:String) {
        val painted=CountDownLatch(1)
        h.instrument.runOnMainSync {
            val web=h.find {it is WebView && it.importantForAccessibility!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS} as WebView
            web.afterReadingPaint {h.root.postOnAnimation {h.root.postOnAnimation {painted.countDown()}}}
        }
        assertTrue("Reader frame was not painted",painted.await(10,TimeUnit.SECONDS))
        h.device.takeScreenshot(File(h.instrument.targetContext.getExternalFilesDir(null),name))
    }
    private fun fixture()=BookReaderHarness(counts=listOf(1,1),sourceFactory={root,title->MultiformatFixtures.fb2(File(root,"reader-format.fb2"),title)})
    @Test fun fb2SharesFootnotesSearchNotesPaginationAndRestoration()=fixture().use {h->
        assertEquals("FB2",h.book.format)
        h.await {h.footer()!=null}
        assertEquals("true",h.js("document.getElementById('miuing-content').textContent.includes('跨元素搜索')"))
        h.await {h.js("Array.from(document.images).some(x=>x.complete && x.naturalWidth>0)")=="true"}
        val shadow="document.getElementById('miuing-footnote') && document.getElementById('miuing-footnote').shadowRoot"
        h.js("document.querySelector('a[role=doc-noteref]').click()")
        h.await {h.js("!!($shadow) && ($shadow).querySelector('.content').textContent.includes('这是跨文件脚注内容')")=="true"}
        h.js("($shadow).querySelector('[aria-label=关闭脚注]').click()")
        h.await {h.js("document.getElementById('miuing-footnote')===null")=="true"}
        val saved=h.saved()
        h.instrument.runOnMainSync {LibraryStore(h.instrument.targetContext).update(saved.copy(notes=listOf(Note(0,"跨元素搜索","批注测试"))))}
        h.reopen();h.await {h.js("!!document.querySelector('[data-miuing]')")=="true"}
        h.js("window.miuingConfigure(Object.assign({},window.miuingConfig,{search:'跨元素搜索'}))")
        h.await {h.js("!!document.querySelector('[data-miuing-search]') && !!document.querySelector('[data-miuing]')")=="true"}
        h.js("window.miuingConfigure(Object.assign({},window.miuingConfig,{search:''}))")
        // Reconfiguration removes spread padding until measurement completes.
        h.await {h.js("window.miuingPageMetrics()!==null")=="true"}
        h.js("window.miuingJump(.6)")
        h.await {h.saved().position>.2f && h.saved().anchor>=0}
        val before=h.saved().anchor
        h.scenario.recreate();h.await {h.js("!!window.miuingConfig && window.miuingAnchor()>=0")=="true"}
        h.await {h.saved().anchor==before}
        h.await {h.js("window.miuingProgress()>.2")=="true"}
        assertEquals("批注测试",h.saved().notes.single().note)
        screenshot(h,"fb2-reader.png")
        h.js("window.miuingJump(0)");h.await {h.js("window.miuingProgress()<.001")=="true"}
        h.js("Array.from(document.querySelectorAll('a')).find(a=>a.textContent.includes('跳到第二章')).click()")
        h.await {h.saved().chapter==1 && h.js("document.getElementById('miuing-content').textContent.includes('第二章定位正文')")=="true"}
        assertTrue(h.maximumWebViews<=2)
    }
    @Test fun mobiUsesUnifiedThemeAndPageTurn()=BookReaderHarness(counts=listOf(1,1),sourceFactory={root,title->MultiformatFixtures.mobi(File(root,"reader-format.mobi"),title)}).use {h->
        h.await {h.footer()!=null};h.device.openReaderThemes()
        h.device.wait(Until.findObject(By.desc("翻页样式")),5000)!!.click()
        h.device.wait(Until.findObject(By.text("平移")),5000)!!.click()
        h.device.wait(Until.findObject(By.desc("关闭主题与设置")),5000)!!.click()
        h.await {h.js("window.miuingConfig.pageTurn==='slide'")=="true"}
        h.js("document.body.dispatchEvent(new MouseEvent('click',{clientX:window.miuingWidth*.92,clientY:window.miuingHeight*.5,bubbles:true}))")
        h.await {h.js("window.miuingPageMetrics().spread===2")=="true"}
        h.await {var gone=false;h.instrument.runOnMainSync {gone=(h.find {it is PageTurnOverlay} as PageTurnOverlay).visibility==View.GONE};gone}
        screenshot(h,"mobi-reader.png")
        val before=h.saved().anchor;h.reopen();h.await {h.saved().anchor==before}
        assertEquals("MOBI",h.book.format)
    }
    @Test fun kf8EmbeddedFontLoadsAfterConverterShutdown() {
        val corpus=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("mobiCorpusDirectory")
        org.junit.Assume.assumeTrue("Optional external KF8 fixture",corpus!=null)
        BookReaderHarness(counts=listOf(1),sourceFactory={root,_->File(corpus!!,"sample-obfuscated-fonts.mobi").copyTo(File(root,"reader-font.azw3"),true)}).use {h->
            assertEquals("AZW3",h.book.format)
            h.await {h.js("!!document.fonts && document.fonts.size>0")=="true"}
            h.js("window.kf8FontLoaded=false;window.kf8FontError='';window.kf8Faces=Array.from(document.fonts).map(f=>({family:f.family,style:f.style,weight:f.weight,status:f.status}));const f=Array.from(document.fonts)[0];document.fonts.load(f.style+' '+f.weight+' 16px '+f.family).then(x=>window.kf8FontLoaded=x.length>0 && x.some(f=>f.status==='loaded')).catch(e=>window.kf8FontError=String(e))")
            Thread.sleep(400)
            android.util.Log.i("KF8FontDiagnostic",h.js("JSON.stringify({faces:window.kf8Faces,error:window.kf8FontError,loaded:window.kf8FontLoaded,styles:Array.from(document.styleSheets).map(s=>s.href)})"))
            h.await {h.js("window.kf8FontLoaded===true")=="true"}
            screenshot(h,"kf8-reader.png")
        }
    }
}
