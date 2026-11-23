package com.miuing.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EpubCompatibilityTest {
    private fun fixture(algorithm:String?)=BookReaderHarness(counts=listOf(650),sourceFactory={root,title->
        EpubCompatibilityFixtures.book(File(root,"font-compatibility.epub"),title,algorithm)
    })
    private fun loaded(h:BookReaderHarness) {
        assertEquals("true",h.js("document.fonts.size>0"))
        h.js("window.compatibilityFont='pending';document.fonts.load('16px CompatibilityFont').then(x=>window.compatibilityFont=x.length>0 && x.every(f=>f.status==='loaded')?'loaded':'fallback').catch(e=>window.compatibilityFont='error')")
        h.await {h.js("window.compatibilityFont!=='pending'")=="true"}
        assertEquals("Embedded publisher font failed; a CSS family name alone is insufficient","\"loaded\"",h.js("window.compatibilityFont"))
    }
    @Test fun plainPublisherFontIsAnIndependentLoaderControl()=fixture(null).use {loaded(it)}
    @Test fun adobePublisherFontLoadsInActualWebView()=fixture(EpubCompatibilityFixtures.adobe).use {loaded(it)}
    @Test fun idpfFontLoadsAndLongChapterPassageSurvivesReflowAndRecreation()=fixture(EpubCompatibilityFixtures.idpf).use {h->
        loaded(h)
        h.await {h.js("window.miuingPageMetrics()!==null")=="true"}
        val anchor=h.js("(()=>{const r=document.createRange();r.selectNodeContents(document.getElementById('miuing-content'));r.setEnd(document.getElementById('p320').firstChild,0);return r.toString().length})()").toInt()
        h.js("window.miuingJumpAnchor($anchor)")
        h.await {h.saved().anchor==anchor && h.saved().position>.2f}
        h.js("window.miuingConfigure(Object.assign({},window.miuingConfig,{fontSize:window.miuingConfig.fontSize+5,fontFamily:'sans'}))")
        h.await {h.js("window.miuingPageMetrics()!==null && window.miuingLocation().anchor===$anchor")=="true"}
        assertEquals("true",h.js("(()=>{const r=document.getElementById('p320').getBoundingClientRect(),s=document.getElementById('miuing-scroll').getBoundingClientRect();return r.left<s.right && r.right>s.left && r.top<s.bottom && r.bottom>s.top})()"))
        val record=h.saved()
        h.instrument.runOnMainSync {LibraryStore(h.instrument.targetContext).update(record.copy(
            marks=listOf(Mark(0,record.position,"兼容性书签",anchor)),notes=listOf(Note(0,"Reading passage 320","兼容性笔记"))))}
        h.reopen();loaded(h)
        h.await {h.saved().anchor==anchor && h.js("window.miuingProgress()>.2")=="true"}
        assertEquals(anchor,h.saved().marks.single().anchor);assertEquals("兼容性笔记",h.saved().notes.single().note)
        h.scenario.recreate()
        h.await {h.js("!!window.miuingConfig && window.miuingLocation().anchor===$anchor")=="true"}
        assertEquals("true",h.js("(()=>{const r=document.getElementById('p320').getBoundingClientRect(),s=document.getElementById('miuing-scroll').getBoundingClientRect();return r.left<s.right && r.right>s.left && r.top<s.bottom && r.bottom>s.top})()"))
        h.js("window.miuingJump(1)");h.await {h.js("window.miuingProgress()>.99")=="true"}
        h.js("window.miuingJump(0)");h.await {h.js("window.miuingProgress()<.001")=="true"}
    }
}
