package com.miuing.reader

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ReaderFontMetricsTest {
    class Bridge {
        val reported = CopyOnWriteArrayList<ReaderPageInfo>()
        val anchors = CopyOnWriteArrayList<Int>()
        val writes = AtomicInteger()
        @JavascriptInterface fun progress(position: Float, anchor: Int) { anchors.add(anchor);writes.incrementAndGet() }
        @JavascriptInterface fun pages(current: Int, total: Int) { reported.add(ReaderPageInfo(current, total)) }
    }

    private val instrument = InstrumentationRegistry.getInstrumentation()
    private fun js(view: WebView, code: String): String {
        val done = CountDownLatch(1)
        var result = ""
        instrument.runOnMainSync { view.evaluateJavascript(code) { result = it; done.countDown() } }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun await(view: WebView, condition: String) {
        val end = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < end) {
            if (js(view, condition) == "true") return
            Thread.sleep(50)
        }
        fail("Font layout condition timed out: $condition")
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Test fun latePublisherFontRefreshesPageCountWithoutWritingReadingProgress() {
        val script = loadReaderScript(instrument.targetContext)
        val requested = CountDownLatch(1)
        val release = CountDownLatch(1)
        val bridge = Bridge()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var view: WebView? = null
            try {
                scenario.onActivity { activity ->
                    view = WebView(activity).apply {
                        settings.javaScriptEnabled = true
                        addJavascriptInterface(bridge, "MIUing")
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                                if (!request.url.path.orEmpty().endsWith("late.ttf")) return null
                                requested.countDown()
                                release.await(20, TimeUnit.SECONDS)
                                return WebResourceResponse("font/ttf", null, File("/system/fonts/Roboto-Regular.ttf").inputStream())
                            }
                            override fun onPageFinished(v: WebView, url: String) {
                                val config = "{paper:'#fff',ink:'#222',width:360,height:300,fontSize:20,margin:20,scrolling:false,lineHeight:1.85,letterSpacing:0,fontFamily:'mono',position:0,anchor:-1,customLayout:true}"
                                v.evaluateJavascript(script + ";const c=$config;window.miuingConfigure(c);document.body.style.fontFamily='Late,monospace';window.miuingConfigure(Object.assign({},c,{fontFamily:'book'}));requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>window.fontLayoutReady=true))))", null)
                            }
                        }
                    }
                    activity.setContentView(view)
                    val paragraphs = (0..200).joinToString("") { "<p>iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii</p>" }
                    view!!.loadDataWithBaseURL("https://appassets.androidplatform.net/", "<meta name='viewport' content='width=device-width,initial-scale=1'><style>@font-face{font-family:Late;src:url(late.ttf)}</style><body style='font-family:monospace'>$paragraphs</body>", "text/html", "UTF-8", null)
                }
                assertTrue("The publisher font was not requested", requested.await(10, TimeUnit.SECONDS))
                await(view!!, "window.fontLayoutReady===true")
                assertTrue("No initial page metrics", bridge.reported.isNotEmpty())
                val before = bridge.reported.last().total
                val writes = bridge.writes.get()
                release.countDown()
                await(view!!, "document.fonts.status==='loaded' && document.fonts.check('20px Late')")
                Thread.sleep(250)
                val actual = js(view!!, "Math.ceil((document.getElementById('miuing-scroll').scrollWidth-document.getElementById('miuing-scroll').clientWidth)/window.miuingWidth)+1").toInt()
                assertNotEquals("Fixture must genuinely reflow after loading the font", before, actual)
                assertEquals("Chapter count stayed at the fallback font layout", actual, bridge.reported.last().total)
                assertEquals("A metrics refresh wrote reading progress", writes, bridge.writes.get())
            } finally {
                release.countDown()
                instrument.runOnMainSync { view?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() } }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun delayedBodyFont(broken:Boolean,navigateWhileLoading:Boolean) {
        val requested=CountDownLatch(1);val release=CountDownLatch(1);val bridge=Bridge()
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            var view:WebView?=null
            try {
                scenario.onActivity {activity->
                    view=WebView(activity).apply {
                        settings.javaScriptEnabled=true;addJavascriptInterface(bridge,"MIUing")
                        webViewClient=object:WebViewClient() {
                            override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse? {
                                if(!r.url.path.orEmpty().endsWith("delayed.ttf")) return null
                                requested.countDown();release.await(20,TimeUnit.SECONDS)
                                return WebResourceResponse("font/ttf",null,if(broken) "invalid font".byteInputStream() else File("/system/fonts/Roboto-Regular.ttf").inputStream())
                            }
                            override fun onPageFinished(v:WebView,url:String) {
                                v.evaluateJavascript(loadReaderScript(instrument.targetContext)+";window.baseFontConfig={paper:'#fff',ink:'#222',width:360,height:300,fontSize:20,margin:20,scrolling:false,lineHeight:1.85,letterSpacing:0,fontFamily:'mono',position:0,anchor:-1,customLayout:true};window.miuingConfigure(window.baseFontConfig)",null)
                            }
                        }
                    }
                    activity.setContentView(view)
                    view!!.loadDataWithBaseURL("https://appassets.androidplatform.net/","<meta name='viewport' content='width=device-width,initial-scale=1'><body>"+(0..200).joinToString("") {"<p>"+"i".repeat(256)+"</p>"}+"</body>","text/html","UTF-8",null)
                }
                await(view!!,"!!window.miuingConfig && window.miuingAnchor()>=0")
                js(view!!,"window.miuingJumpAnchor(1800);window.miuingConfigure(Object.assign({},window.baseFontConfig,{fontFamily:'delayed',fontFace:{family:'DelayedBody',url:'https://appassets.androidplatform.net/delayed.ttf'}}))")
                assertTrue("Selected body font was not requested",requested.await(10,TimeUnit.SECONDS))
                val writes=bridge.writes.get()
                if(navigateWhileLoading) js(view!!,"window.miuingJumpAnchor(4200)")
                val fallbackPages=js(view!!,"Math.ceil((document.getElementById('miuing-scroll').scrollWidth-360)/360)+1")
                js(view!!,"window.fontSettled=false;document.fonts.load('20px DelayedBody').catch(()=>[]).then(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>window.fontSettled=true)))")
                release.countDown();await(view!!,"window.fontSettled===true")
                if(!broken) assertNotEquals("Delayed fixture did not actually reflow",fallbackPages,js(view!!,"Math.ceil((document.getElementById('miuing-scroll').scrollWidth-360)/360)+1"))
                val end=System.currentTimeMillis()+5000
                while(bridge.writes.get()<=writes && System.currentTimeMillis()<end) Thread.sleep(50)
                assertTrue("Font load left the reader stuck restoring",bridge.writes.get()>writes)
                assertEquals("Late font completion overwrote the intended source",if(navigateWhileLoading) 4200 else 1800,bridge.anchors.last())
                val destination=if(navigateWhileLoading) 4200 else 1800
                assertEquals("Reported source survived but its text moved offscreen after font reflow","true",js(view!!,"""(()=>{let offset=$destination;const walk=document.createTreeWalker(document.getElementById('miuing-content'),NodeFilter.SHOW_TEXT);let node;while(node=walk.nextNode()){if(offset<node.length){const range=document.createRange();range.setStart(node,offset);range.setEnd(node,offset+1);const r=range.getBoundingClientRect(),b=document.getElementById('miuing-scroll').getBoundingClientRect();return r.left>=b.left-1&&r.right<=b.right+1&&r.top>=b.top-1&&r.bottom<=b.bottom+1;}offset-=node.length;}return false;})()"""))
                js(view!!,"window.miuingAdvance(1)")
                val next=System.currentTimeMillis()+3000
                while(bridge.writes.get()<=writes+1 && System.currentTimeMillis()<next) Thread.sleep(50)
                assertTrue("Reader did not accept navigation after font completion",bridge.writes.get()>writes+1)
            } finally {
                release.countDown()
                instrument.runOnMainSync {view?.let {(it.parent as? ViewGroup)?.removeView(it);it.destroy()}}
            }
        }
    }
    @Test fun failedBodyFontFallsBackAndRestoresItsPassage()=delayedBodyFont(broken=true,navigateWhileLoading=false)
    @Test fun navigationSupersedesPendingBodyFontRestoration()=delayedBodyFont(broken=false,navigateWhileLoading=true)
}
