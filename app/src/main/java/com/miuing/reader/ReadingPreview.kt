package com.miuing.reader

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import com.miuing.core.PublicationParser
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

internal data class ReadingPreviewSpec(val root:File,val path:File,val configuration:String,val script:String,val loader:WebViewAssetLoader,val markup:String?)

/** One bounded, passive renderer. No location callback can reach the library. */
internal class ReadingPreview(context:Context,val spec:ReadingPreviewSpec,attach:(WebView)->Unit) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val bridge=ReadingBridge()
    val view:WebView=object:WebView(context) {override fun dispatchTouchEvent(event:MotionEvent)=false}
    private var ready=false
    private var closed=false
    private var failed=false
    private var request=0
    private var pending:(()->Unit)?=null
    init {
        configureReadingWeb(view,bridge)
        view.settings.offscreenPreRaster=true
        view.isFocusable=false;view.isFocusableInTouchMode=false
        view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        view.setBackgroundColor(android.graphics.Color.parseColor(JSONObject(spec.configuration).getString("paper")))
        bridge.onReady={ready=true;pending?.invoke()}
        attach(view)
        view.webViewClient=readingClient(spec.loader,{v->
            if(!closed) v.evaluateJavascript(spec.script+"\nwindow.miuingConfigure(${spec.configuration});",null)
        })
        scope.launch {
            try {
                val html=spec.markup ?: withContext(Dispatchers.IO) {PublicationParser.readMarkup(spec.path.toPath())}
                if(!closed) loadReadingMarkup(view,spec.root,spec.path,html)
            } catch(e:CancellationException) {throw e}
            catch(_:java.io.IOException) {failed=true;pending?.invoke()}
        }
    }
    fun seek(position:Double,onFrame:(WebView)->Unit,onFailure:()->Unit) {
        val id=++request
        val action={
            if(!closed && id==request) {
                if(failed) onFailure()
                else if(ready) {
                    pending=null
                    view.evaluateJavascript("window.miuingJump($position)") {
                        if(!closed && id==request) view.afterReadingPaint {if(!closed && id==request) onFrame(view)}
                    }
                }
            }
        }
        pending=action;action()
    }
    fun cancelSeek() {request++;pending=null}
    fun close() {
        if(closed) return
        closed=true;cancelSeek();scope.cancel();destroyReadingWeb(view,bridge)
    }
}
