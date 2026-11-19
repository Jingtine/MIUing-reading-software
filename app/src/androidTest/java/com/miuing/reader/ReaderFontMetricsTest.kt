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
        val writes = AtomicInteger()
        @JavascriptInterface fun progress(position: Float, anchor: Int) { writes.incrementAndGet() }
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
        val script = instrument.targetContext.assets.open("reader.js").bufferedReader().use { it.readText() }
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
}
