package com.miuing.reader

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ReaderEngineTest {
    class Bridge {
        val selected = AtomicReference(SelectedText())
        @JavascriptInterface fun progress(position: Float, anchor: Int) {}
        @JavascriptInterface fun selection(text: String, start: Int, end: Int) { selected.set(SelectedText(text, start, end)) }
    }
    private val instrument = InstrumentationRegistry.getInstrumentation()

    private fun js(view: WebView, code: String): String {
        val done = CountDownLatch(1)
        var result = ""
        instrument.runOnMainSync { view.evaluateJavascript(code) { result = it; done.countDown() } }
        assertTrue("Reader script did not respond", done.await(15, TimeUnit.SECONDS))
        return result
    }

    private fun await(view: WebView) {
        val end = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < end) {
            if (js(view, "window.engineReady===true") == "true") return
            Thread.sleep(40)
        }
        fail("Reader layout did not finish")
    }

    private fun configure(view: WebView, patch: String, mutation: String = "") {
        js(view, "(()=>{$mutation;window.engineReady=false;window.testConfig=Object.assign({},window.testConfig,$patch);const started=performance.now();window.miuingConfigure(window.testConfig);window.configureMs=performance.now()-started;requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>window.engineReady=true))))})()")
        await(view)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun reader(paragraphs: Int, setup: String = "", initialPatch: String = "{}", block: (WebView, Bridge) -> Unit) {
        val script = loadReaderScript(instrument.targetContext)
        val bridge = Bridge()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var view: WebView? = null
            try {
                scenario.onActivity { activity ->
                    view = WebView(activity).apply {
                        settings.javaScriptEnabled = true
                        settings.blockNetworkLoads = true
                        addJavascriptInterface(bridge, "MIUing")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(v: WebView, url: String) {
                                val counter = "window.textWalks=0;const walk=document.createTreeWalker.bind(document);document.createTreeWalker=function(root,kind){if(root.id==='miuing-content' && kind===NodeFilter.SHOW_TEXT)window.textWalks++;return walk.apply(document,arguments)};"
                                val config = "window.testConfig={paper:'#fff',ink:'#222',width:360,height:500,fontSize:20,margin:24,scrolling:true,fontFamily:'book',customLayout:false,position:0,anchor:-1,notes:[]};"
                                v.evaluateJavascript(counter + script + ";" + setup + ";" + config + "Object.assign(window.testConfig,$initialPatch);window.miuingConfigure(window.testConfig);requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>window.engineReady=true))))", null)
                            }
                        }
                    }
                    activity.setContentView(view)
                    val text = (0 until paragraphs).joinToString("") { "<p id='p$it'>😀Paragraph $it. A chapter used to verify text offsets and highlights. 正文位置和精确高亮。</p>" }
                    view!!.loadDataWithBaseURL("https://appassets.androidplatform.net/", "<meta name='viewport' content='width=device-width,initial-scale=1'>$text", "text/html", "UTF-8", null)
                }
                await(view!!)
                block(view!!, bridge)
            } finally {
                instrument.runOnMainSync { view?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() } }
            }
        }
    }

    @Test fun repeatedAnchorLookupsReuseTheChapterIndex() = reader(3000) { view, _ ->
        assertTrue("Opening an unannotated chapter performed redundant text scans", js(view, "window.textWalks").toInt() <= 1)
        js(view, "window.miuingJumpAnchor(0);window.textWalks=0;window.lookupStart=performance.now();for(let i=0;i<25;i++){document.getElementById('p0').style.color=i%2?'#222':'#333';window.miuingJumpAnchor(i*300)}window.lookupMs=performance.now()-window.lookupStart")
        val walks = js(view, "window.textWalks").toInt()
        println("ENGINE_LOOKUPS walks=$walks elapsedMs=${js(view, "window.lookupMs")}")
        assertTrue("Repeated navigation rescanned the whole chapter $walks times", walks <= 1)
    }

    private val searchSetup = """
        (()=>{
          const p=document.getElementById('p120');
          p.innerHTML='MiXeD [<em>A+B</em>] &amp; 😀 and the rest of the passage.';
          const prefix=document.createRange();prefix.selectNodeContents(document.body);prefix.setEnd(p,0);
          window.searchStart=prefix.toString().length;
          const ignored=document.createElement('style');ignored.textContent='/* MiXeD [A+B] & 😀 */';document.head.appendChild(ignored);
        })()
    """.trimIndent()

    @Test fun searchRespectsBlockBreaksAndUnicodeCase() = reader(2) { view, _ ->
        js(view, "document.getElementById('miuing-content').innerHTML='<p>ab</p><p>cd</p><p id=real>abcd</p><p>xy<br>zw</p><p id=unicode>Kelvin İSTANBUL</p>'")
        configure(view, "{search:'abcd'}")
        assertEquals("Search matched a word assembled across paragraphs", "true", js(view, "!!document.querySelector('#real [data-miuing-search]') && document.querySelectorAll('[data-miuing-search]').length===1"))
        configure(view, "{search:'xyzw'}")
        assertEquals("Search joined across a visible line break", "true", js(view, "!document.querySelector('[data-miuing-search]')"))
        configure(view, "{search:'kelvin'}")
        assertEquals("Unicode case missed Kelvin sign", "true", js(view, "document.querySelector('[data-miuing-search]').textContent==='Kelvin'"))
        configure(view, "{search:'i̇stanbul'}")
        assertEquals("Case expansion changed DOM range boundaries", "true", js(view, "document.querySelector('[data-miuing-search]').textContent==='İSTANBUL'"))
    }

    @Test fun selectedSearchLocatesLiteralCrossInlineTextAndComposesNotes() = reader(150, searchSetup,
        "{search:'mixed [a+b] & 😀',notes:[{start:window.searchStart+2,end:window.searchStart+14,text:'overlap'}]}") { view, _ ->
        assertEquals("Selected search did not move beyond the chapter start", "true", js(view, "window.miuingProgress()>.5"))
        assertEquals("Search must retain actual DOM UTF-16 offset", js(view, "window.searchStart"), js(view, "(()=>{const r=document.createRange();r.selectNodeContents(document.getElementById('miuing-content'));r.setEnd(document.querySelector('[data-miuing-search]'),0);return r.toString().length})()"))
        assertEquals("Literal punctuation/case/inline search was not marked", "true", js(view, "Array.from(document.querySelectorAll('[data-miuing-search]')).map(n=>n.textContent).join('')==='MiXeD [A+B] & 😀'"))
        assertEquals("Overlap changed note coverage", "true", js(view, "Array.from(document.querySelectorAll('[data-miuing]')).map(n=>n.textContent).join('')===document.getElementById('miuing-content').textContent.slice(window.searchStart+2,window.searchStart+14)"))
        assertEquals("Search target is outside the viewport", "true", js(view, "(()=>{const r=document.querySelector('[data-miuing-search]').getBoundingClientRect();return r.top>=0 && r.top<window.miuingHeight})()"))
        js(view, "window.savedText=document.getElementById('miuing-content').textContent;window.savedSearch=document.querySelector('[data-miuing-search]')")
        configure(view, "{fontSize:22,paper:'#eee'}")
        assertEquals("Unchanged search rebuilt its DOM", "true", js(view, "window.savedSearch===document.querySelector('[data-miuing-search]')"))
        configure(view, "{search:'not present'}")
        assertEquals("Missing hit left stale search or changed text", "true", js(view, "!document.querySelector('[data-miuing-search]') && !!document.querySelector('[data-miuing]') && window.savedText===document.getElementById('miuing-content').textContent"))
        configure(view, "{search:''}")
        assertEquals("Search must not mutate text", "true", js(view, "window.savedText===document.getElementById('miuing-content').textContent"))
    }

    @Test fun initialSearchSurvivesConsecutivePagedConfigurationsAndSavedAnchorWins() = reader(150,
        "$searchSetup;window.requestAnimationFrame(function(){window.miuingConfigure(Object.assign(window.testConfig,{fontSize:21,height:520}))})",
        "{search:'mixed [a+b] & 😀',scrolling:false}") { view, _ ->
        assertEquals("Paged initial search was lost during configuration", "true", js(view, "(()=>{const r=document.querySelector('[data-miuing-search]').getBoundingClientRect();return r.left>=0 && r.left<window.miuingWidth && r.top>=0 && r.top<window.miuingHeight})()"))
        js(view, "window.miuingJumpAnchor(20)")
        configure(view, "{fontSize:23}")
        assertEquals("Changing typography jumped back to search", "true", js(view, "window.miuingProgress()<.1"))
    }

    private fun select(view: WebView, bridge: Bridge, mutation: String = ""): Int {
        val expected = js(view, "(()=>{$mutation;const root=document.getElementById('miuing-content'),p=document.getElementById('p20'),mark=p.querySelector('mark'),node=mark?mark.firstChild:p.firstChild;const prefix=document.createRange();prefix.selectNodeContents(root);prefix.setEnd(node,0);const at=prefix.toString().length;const range=document.createRange();range.setStart(node,0);range.setEnd(node,3);getSelection().removeAllRanges();getSelection().addRange(range);document.dispatchEvent(new Event('selectionchange'));return at})()").toInt()
        assertEquals("Selection start used stale text offsets", expected, bridge.selected.get().start)
        assertEquals(expected + 3, bridge.selected.get().end)
        assertEquals("😀P", bridge.selected.get().text)
        return expected
    }

    @Test fun sameTaskMutationsAndHighlightChangesKeepSelectionOffsetsFresh() = reader(50) { view, bridge ->
        val before = select(view, bridge)
        val inserted = select(view, bridge, "const flow=document.getElementById('miuing-content');flow.insertBefore(document.createTextNode('prefix '),flow.firstChild)")
        assertEquals(before + 7, inserted)
        val changed = select(view, bridge, "document.getElementById('p0').firstChild.appendData('extra')")
        assertEquals(inserted + 5, changed)
        configure(view, "{notes:[{start:$changed,end:${changed + 3},text:'😀P'}]}")
        select(view, bridge)
        configure(view, "{}", "const root=document.getElementById('miuing-content');root.insertBefore(document.createTextNode('later '),root.firstChild)")
        assertEquals("Unchanged notes ignored an external text revision", "true", js(view, "Array.from(document.querySelectorAll('mark[data-miuing]')).map(mark=>mark.textContent).join('')===document.getElementById('miuing-content').textContent.slice($changed,${changed + 3})"))
        val shifted = select(view, bridge)
        assertEquals(changed + 6, shifted)
        configure(view, "{notes:[]}")
        select(view, bridge)
        js(view, "(()=>{const p=document.getElementById('p20'),range=document.createRange();range.selectNodeContents(p);getSelection().removeAllRanges();getSelection().addRange(range);document.dispatchEvent(new Event('selectionchange'))})()")
        assertEquals("Element endpoints lost their prefix offset", shifted, bridge.selected.get().start)
        assertEquals(shifted + js(view, "document.getElementById('p20').textContent.length").toInt(), bridge.selected.get().end)
    }

    @Test fun highlightsAreBatchedAndUnchangedNotesKeepTheirDomNodes() = reader(400) { view, _ ->
        js(view, "window.originalText=document.getElementById('miuing-content').textContent;window.testNotes=[];for(let i=0;i<120;i++){const p=document.getElementById('p'+i),r=document.createRange();r.selectNodeContents(document.getElementById('miuing-content'));r.setEnd(p.firstChild,0);const at=r.toString().length;window.testNotes.push({start:at,end:at+5,text:p.textContent.slice(0,5)});if(i%10===0)window.testNotes.push({start:at+3,end:at+10,text:p.textContent.slice(3,10)})}window.textWalks=0;window.highlightStart=performance.now()")
        configure(view, "{notes:window.testNotes}")
        val walks = js(view, "window.textWalks").toInt()
        println("ENGINE_HIGHLIGHTS walks=$walks configureMs=${js(view, "window.configureMs")}")
        assertTrue("Highlighting rescanned the chapter $walks times", walks <= 2)
        assertEquals("Highlighting changed the chapter text", "true", js(view, "document.getElementById('miuing-content').textContent===window.originalText"))
        assertEquals("Overlapping highlights must preserve their visible union", "true", js(view, "document.getElementById('p0').querySelector('mark').textContent===document.getElementById('p0').textContent.slice(0,10) && !document.querySelector('mark mark')"))
        js(view, "(()=>{window.firstMark=document.querySelector('mark');const range=document.createRange();range.selectNodeContents(window.firstMark);getSelection().removeAllRanges();getSelection().addRange(range);const selected=getSelection();window.savedSelection={text:selected.toString(),anchor:selected.anchorNode,start:selected.anchorOffset,focus:selected.focusNode,end:selected.focusOffset};window.textWalks=0})()")
        configure(view, "{fontSize:21,paper:'#eeeded'}")
        assertEquals("Theme changes rebuilt identical highlight nodes", "true", js(view, "window.firstMark===document.querySelector('mark')"))
        assertEquals("Typography changes invalidated the text index", 0, js(view, "window.textWalks").toInt())
        assertEquals("Typography changes destroyed the active selection", "true", js(view, "(()=>{const s=getSelection(),before=window.savedSelection;return s.toString()===before.text && s.anchorNode===before.anchor && s.anchorOffset===before.start && s.focusNode===before.focus && s.focusOffset===before.end})()"))
        configure(view, "{notes:[{text:'正文位置和精确高亮。',start:-1,end:-1}]}")
        assertEquals("Legacy quote lookup must still highlight its first occurrence", "true", js(view, "!!document.getElementById('p0').querySelector('mark') && !document.getElementById('p1').querySelector('mark')"))
        configure(view, "{notes:[]}")
        assertEquals("Removing highlights changed the chapter", "true", js(view, "!document.querySelector('mark[data-miuing]') && document.getElementById('miuing-content').textContent===window.originalText"))
        js(view, "window.miuingJumpAnchor(window.originalText.length)")
        assertEquals("End-of-chapter anchor must remain reachable", 1.0, js(view, "window.miuingProgress()").toDouble(), .002)
        assertEquals("Empty text must not break navigation", "true", js(view, "(()=>{document.getElementById('miuing-content').textContent='';window.miuingJumpAnchor(0);return window.miuingAnchor()===-1})()"))
    }
}
