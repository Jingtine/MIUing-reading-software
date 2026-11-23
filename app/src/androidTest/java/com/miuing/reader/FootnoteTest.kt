package com.miuing.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.webkit.WebView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FootnoteTest {
    private val shadow="document.getElementById('miuing-footnote') && document.getElementById('miuing-footnote').shadowRoot"
    private fun fixture()=BookReaderHarness(transform={chapter,html->
        if(chapter!=0) html else html.replace("<html>","<html xmlns:epub='http://www.idpf.org/2007/ops'>")
            .replace("<body>","<body style='font-family:monospace'>")
            .replaceFirst("中文正文。","中文正文。<a id='ref' epub:type='noteref' href='#note'>[1]</a> <a id='cross' role='doc-noteref' href='notes/%E6%B3%A8%20%E9%87%8A.xhtml#n'>[2]</a> <a id='missing' epub:type='noteref' href='#missing-note'>[3]</a><a id='ordinary' href='c1.xhtml#c1p1'>下一章</a>")
            .replace("</body>","<aside id='note' epub:type='footnote' style='display:none'><p>脚注 <em>强调</em>内容。</p></aside></body>")
    },extras=mapOf("notes/注 释.xhtml" to "<html><body><p id='n'>跨文件脚注内容。</p></body></html>"))

    @Test fun sameChapterPopupKeepsSourceTextPagesAndLocationAndInheritsFont()=fixture().use {r->
        r.await {r.footer()!=null}
        val before=r.saved();val text=r.js("document.getElementById('miuing-content').textContent")
        val metrics=r.js("JSON.stringify(window.miuingPageMetrics())")
        r.js("document.getElementById('ref').click()")
        r.await {r.js("!!($shadow) && ($shadow).querySelector('.content').textContent.includes('脚注')")=="true"}
        assertEquals("Popup must be visible inside the reader viewport","true",r.js("(()=>{const b=($shadow).querySelector('.panel').getBoundingClientRect();return b.width>100 && b.height>60 && b.left>=0 && b.top>=0 && b.right<=window.miuingWidth+1 && b.bottom<=window.miuingHeight+1})()"))
        assertEquals("true",r.js("getComputedStyle(($shadow).querySelector('.content')).fontFamily===getComputedStyle(document.body).fontFamily"))
        assertEquals(text,r.js("document.getElementById('miuing-content').textContent"))
        assertEquals(metrics,r.js("JSON.stringify(window.miuingPageMetrics())"))
        r.js("($shadow).querySelector('[aria-label=关闭脚注]').click()")
        r.await {r.js("document.getElementById('miuing-footnote')===null")=="true"}
        assertEquals(before.anchor,r.saved().anchor);assertEquals(before.chapter,r.saved().chapter);assertEquals(before.position,r.saved().position)
    }
    @Test fun nonSpineEncodedNoteUsesPopupAndSystemBackReturnsToSource()=fixture().use {r->
        val before=r.saved();r.js("document.getElementById('cross').click()")
        r.await {r.js("!!($shadow) && ($shadow).querySelector('.content').textContent.includes('跨文件脚注')")=="true"}
        r.await {!r.device.hasObject(androidx.test.uiautomator.By.desc("返回书库"))}
        val painted=CountDownLatch(1)
        r.instrument.runOnMainSync {(r.find {it is WebView} as WebView).postVisualStateCallback(0,object:WebView.VisualStateCallback() {
            override fun onComplete(requestId:Long) {painted.countDown()}
        })}
        assertTrue("Footnote did not paint",painted.await(10,TimeUnit.SECONDS));r.device.waitForIdle()
        r.device.takeScreenshot(java.io.File(r.instrument.targetContext.getExternalFilesDir(null),"footnote-popup.png"))
        r.device.pressBack();r.await {r.js("!document.getElementById('miuing-footnote')")=="true"}
        assertEquals(before.anchor,r.saved().anchor);assertEquals(0,r.saved().chapter)
    }
    @Test fun missingNoteIsDismissibleAndOrdinaryLinksStillNavigate()=fixture().use {r->
        r.js("document.getElementById('missing').click()")
        r.await {r.js("!!($shadow) && ($shadow).querySelector('.content').textContent.includes('无法显示脚注')")=="true"}
        r.device.pressBack();r.await {r.js("!document.getElementById('miuing-footnote')")=="true"}
        r.js("document.getElementById('ordinary').click()")
        r.await {r.saved().chapter==1}
    }
    @Test fun immediateDismissalAndRecreationNeverReopenStalePopup()=fixture().use {r->
        val before=r.saved()
        repeat(3) {r.js("document.getElementById('cross').click();window.miuingDismissFootnote()")}
        Thread.sleep(500);assertEquals("true",r.js("!document.getElementById('miuing-footnote')"))
        r.js("document.getElementById('ref').click()")
        r.await {r.js("!!($shadow) && ($shadow).querySelector('.content').textContent.includes('脚注')")=="true"}
        r.scenario.recreate();r.await {r.js("!!window.miuingConfig && !document.getElementById('miuing-footnote')")=="true"}
        assertEquals(before.anchor,r.saved().anchor)
    }
}
