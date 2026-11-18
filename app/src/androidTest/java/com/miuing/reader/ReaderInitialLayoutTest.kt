package com.miuing.reader

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReaderInitialLayoutTest {
    // This renderer test asserts DOM position, not the native persistence callback.
    class LayoutBridge {@JavascriptInterface fun progress(position:Float,anchor:Int) {}}
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private fun evaluate(view:WebView,code:String):String {
        val done=CountDownLatch(1);var result=""
        instrument.runOnMainSync {view.evaluateJavascript(code) {result=it;done.countDown()}}
        assertTrue(done.await(10,TimeUnit.SECONDS));return result
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun initial(fragment:String,position:Float,assertion:(WebView)->Unit) {
        val script=instrument.targetContext.assets.open("reader.js").bufferedReader().use {it.readText()}
        val first=JSONObject().put("paper","#ffffff").put("ink","#222222").put("width",360).put("height",500).put("fontSize",20)
            .put("margin",24).put("scrolling",true).put("serif",true).put("lineHeight",1.85).put("letterSpacing",0)
            .put("anchor",-1).put("fragment",fragment).put("position",position)
        val second=JSONObject(first.toString()).put("fontSize",21).put("height",520)
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            var view:WebView?=null
            try {
                scenario.onActivity {activity->
                    view=WebView(activity).apply {
                        settings.javaScriptEnabled=true;settings.blockNetworkLoads=true
                        addJavascriptInterface(LayoutBridge(),"MIUing")
                        webViewClient=object:WebViewClient() {override fun onPageFinished(v:WebView,url:String) {
                            v.evaluateJavascript(script+";window.miuingConfigure($first);window.miuingConfigure($second);requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>window.layoutReady=true))))",null)
                        }}
                    }
                    activity.setContentView(view)
                    view!!.loadDataWithBaseURL("https://appassets.androidplatform.net/book/test/","<meta name='viewport' content='width=device-width,initial-scale=1.0'><h1>Layout</h1>"+(0..150).joinToString("") {"<p id='${if(it==120) "destination" else "p$it"}'>Paragraph $it. This is the real reader layout restoration fixture.</p>"},"text/html","UTF-8",null)
                }
                val deadline=System.currentTimeMillis()+15000
                while(evaluate(view!!,"window.layoutReady===true")!="true" && System.currentTimeMillis()<deadline) Thread.sleep(80)
                assertEquals("Reader layout did not complete","true",evaluate(view!!,"window.layoutReady===true"));assertion(view!!)
            } finally {instrument.runOnMainSync {view?.let {(it.parent as? ViewGroup)?.removeView(it);it.destroy()}}}
        }
    }
    @Test fun consecutiveInitialConfigurationsKeepSavedPercentage()=initial("",.6f) {view->
        assertEquals("Second initial config replaced the saved destination with chapter start",.6,evaluate(view,"window.miuingProgress()").toDouble(),.002)
    }
    @Test fun consecutiveInitialConfigurationsKeepFragment()=initial("destination",0f) {view->
        assertEquals("Second initial config lost the pending fragment","true",evaluate(view,"(()=>{const r=document.getElementById('destination').getBoundingClientRect();return r.top>=20 && r.top<50})()"))
    }
}
