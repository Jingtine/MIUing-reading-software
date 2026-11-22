package com.miuing.reader

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real application renderer, with native ready acknowledgements instead of sleeps. */
internal class RendererHarness(html:String,patch:JSONObject=JSONObject(),setup:String="",resource:((Uri)->WebResourceResponse?)?=null):AutoCloseable {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val scenario=ActivityScenario.launch(MainActivity::class.java)
    private val ready=AtomicInteger()
    private val bridge=ReadingBridge()
    private val root=File(instrument.targetContext.cacheDir,"renderer-${UUID.randomUUID()}").apply {mkdirs()}
    lateinit var view:WebView
        private set
    var configuration=JSONObject().apply {
        put("paper","#ffffff");put("ink","#222222");put("width",360);put("height",500)
        put("fontSize",20);put("margin",24);put("lineHeight",1.5);put("letterSpacing",0);put("wordSpacing",0)
        put("fontFamily","book");put("customLayout",false);put("scrolling",true);put("position",0);put("anchor",-1)
        patch.keys().forEach {put(it,patch.get(it))}
    }
        private set
    init {
        bridge.onReady={ready.incrementAndGet();Unit}
        scenario.onActivity {activity->
            view=WebView(activity);configureReadingWeb(view,bridge)
            val client=readingClient(readingLoader(activity,root),{v->v.evaluateJavascript(loadReaderScript(activity)+"\n$setup\nwindow.miuingConfigure($configuration);",null)})
            view.webViewClient=object:WebViewClient() {
                override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest)=resource?.invoke(r.url) ?: client.shouldInterceptRequest(v,r)
                override fun onPageFinished(v:WebView,url:String)=client.onPageFinished(v,url)
            }
            activity.setContentView(view)
            loadReadingMarkup(view,root,File(root,"chapter.html"),html)
        }
        await {ready.get()>0}
    }
    fun js(code:String):String {
        val done=CountDownLatch(1);var result=""
        instrument.runOnMainSync {view.evaluateJavascript(code) {result=it;done.countDown()}}
        assertTrue("Renderer did not respond: $code",done.await(10,TimeUnit.SECONDS));return result
    }
    fun configure(patch:JSONObject) {
        val before=ready.get()
        configuration=JSONObject(configuration.toString()).apply {patch.keys().forEach {put(it,patch.get(it))}}
        js("window.miuingConfigure($configuration)")
        await {ready.get()>before}
    }
    fun await(condition:()->Boolean) {
        val deadline=System.currentTimeMillis()+15000
        while(System.currentTimeMillis()<deadline) {if(condition()) return;Thread.sleep(35)}
        fail("Renderer condition timed out")
    }
    override fun close() {
        instrument.runOnMainSync {destroyReadingWeb(view,bridge)}
        scenario.close();root.deleteRecursively()
    }
}
