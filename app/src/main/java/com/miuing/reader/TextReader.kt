package com.miuing.reader

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.widget.FrameLayout
import android.view.ViewGroup
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.miuing.core.Publication
import com.miuing.core.PublicationParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File

private class ReadingBridge(val location:(Float,Int)->Unit,val tap:()->Unit,val selection:(SelectedText)->Unit,val chapter:(Int)->Unit,val pages:(ReaderPageInfo)->Unit) {
    private val main=Handler(Looper.getMainLooper())
    var active=true
    var turnPage:(Int)->Unit={}
    @JavascriptInterface fun progress(p:Float,anchor:Int) { main.post { if(active) location(p,anchor) } }
    @JavascriptInterface fun pages(current:Int,total:Int) { main.post { if(active && total>0 && current in 1..total) pages(ReaderPageInfo(current,total)) } }
    @JavascriptInterface fun tap() { main.post { if(active) tap.invoke() } }
    @JavascriptInterface fun selection(text:String,start:Int,end:Int) { main.post { if(active) selection(SelectedText(text,start,end)) } }
    @JavascriptInterface fun chapter(direction:Int) { main.post { if(active) chapter.invoke(direction) } }
    @JavascriptInterface fun turn(direction:Int) { main.post { if(active && direction in listOf(-1,1)) turnPage(direction) } }
}

// Keep a concrete parameter at the WebView boundary; lint cannot resolve
// the interface type through Compose's generic remember() initializer.
private fun attachReadingBridge(view:WebView,bridge:ReadingBridge) {
    view.addJavascriptInterface(bridge,"MIUing")
}

internal fun loadReaderScript(context:android.content.Context):String =
    listOf("reader-text.js","reader.js").joinToString("\n") { asset ->
        context.assets.open(asset).bufferedReader().use { it.readText() }
    }

@SuppressLint("SetJavaScriptEnabled")
@Composable fun TextReader(
    publication:Publication,root:File,chapter:Int,position:Float,jump:Int,theme:ReadingTheme,fontSize:Float,
    scrolling:Boolean,serif:Boolean,search:String,notes:List<Note>,onPosition:(Float)->Unit,onTap:()->Unit,
    onSelection:(String)->Unit,onChapter:(Int)->Unit,anchor:Int=-1,fragment:String="",
    lineHeight:Float=1.85f,margin:Float=24f,letterSpacing:Float=0f,pageTurn:String="curl",
    fontFamily:String=if(serif) "serif" else "sans",boldText:Boolean=theme.name=="粗体",wordSpacing:Float=0f,justify:Boolean=false,customLayout:Boolean=true,
    onLocation:(Float,Int)->Unit={p,_->onPosition(p)},
    onSelectionRange:(SelectedText)->Unit={onSelection(it.text)},onLink:(Int,String)->Unit={i,_->onChapter(i)},
    onPages:(ReaderPageInfo)->Unit={},onLoadError:()->Unit={}
) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val latestLocation by rememberUpdatedState(onLocation)
    val latestTap by rememberUpdatedState(onTap)
    val latestSelection by rememberUpdatedState(onSelectionRange)
    val latestChapter by rememberUpdatedState(onChapter)
    val latestLink by rememberUpdatedState(onLink)
    val latestPages by rememberUpdatedState(onPages)
    val latestLoadError by rememberUpdatedState(onLoadError)
    val path=publication.chapters[chapter].path.toFile()
    var html by remember(path) { mutableStateOf<String?>(null) }
    var readError by remember(path) { mutableStateOf<String?>(null) }
    val applicationScript=remember(context) { loadReaderScript(context) }
    LaunchedEffect(path) {
        try { html=withContext(Dispatchers.IO) { PublicationParser.readMarkup(path.toPath()) } }
        catch(e:java.io.IOException) { readError=e.localizedMessage ?: "章节读取失败" }
    }
    if(readError!=null) {
        LaunchedEffect(path,jump,scrolling) {latestLoadError()}
        Text("无法读取此章节：$readError");return
    }
    val loader=remember(root) { WebViewAssetLoader.Builder().addPathHandler("/book/",WebViewAssetLoader.InternalStoragePathHandler(context,root)).build() }
    val bridge:ReadingBridge=remember(path,jump,scrolling) { ReadingBridge({p,a->latestLocation(p,a)},{latestTap()},{latestSelection(it)},{latestChapter(chapter+it)},{latestPages(it)}) }
    val webResult=remember(path,jump,scrolling) { runCatching { WebView(context).apply {
        // Chromium 69 leaves black raster tiles behind modal windows on API26.
        // Keep the fallback scoped to legacy providers; current WebViews stay accelerated.
        val providerMajor=WebView.getCurrentWebViewPackage()?.versionName?.substringBefore('.')?.toIntOrNull()
        if(providerMajor!=null && providerMajor<=69) setLayerType(View.LAYER_TYPE_SOFTWARE,null)
        settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false
        settings.blockNetworkLoads=true;settings.domStorageEnabled=false
        settings.useWideViewPort=true;settings.loadWithOverviewMode=false
        isVerticalScrollBarEnabled=false;isHorizontalScrollBarEnabled=false
        attachReadingBridge(this,bridge)
    } } }
    val web=webResult.getOrNull()
    if(web==null) {
        LaunchedEffect(path,jump,scrolling) {latestLoadError()}
        Text("系统网页组件不可用，请启用或更新 Android System WebView。");return
    }
    val overlay=remember(web) {PageTurnOverlay(context)}
    val initial=remember(web) { Triple(position,anchor,fragment) }
    val initialSearch=remember(web) { search }
    val configuration=JSONObject().apply {
        put("position",initial.first);put("anchor",initial.second);put("fragment",initial.third)
        put("search",initialSearch)
        put("paper","#%06x".format(theme.paper.toArgb() and 0xffffff))
        put("ink","#%06x".format(theme.ink.toArgb() and 0xffffff))
        put("fontSize",fontSize);put("scrolling",scrolling);put("serif",serif);put("bold",boldText)
        put("lineHeight",lineHeight);put("margin",margin);put("letterSpacing",letterSpacing)
        put("pageTurn",pageTurn);put("fontFamily",fontFamily);put("wordSpacing",wordSpacing);put("justify",justify);put("customLayout",customLayout)
        put("notes",JSONArray(notes.filter { it.chapter==chapter }.map { JSONObject().put("text",it.text).put("start",it.start).put("end",it.end) }))
    }.toString()
    fun script(view:WebView):String {
        val config=JSONObject(configuration)
        val density=view.resources.displayMetrics.density
        config.put("width",view.width/density);config.put("height",view.height/density)
        return "window.miuingConfigure($config);"
    }
    var loaded by remember(web) { mutableStateOf(false) }
    bridge.turnPage={direction->if(loaded) overlay.turn(web,pageTurn,direction,theme.paper.toArgb()) {ready->web.evaluateJavascript("window.miuingAdvance($direction)") {ready()}}}
    DisposableEffect(web) { onDispose { bridge.active=false;overlay.clear();web.stopLoading();web.removeJavascriptInterface("MIUing");(web.parent as? ViewGroup)?.removeView(web);web.destroy() } }
    LaunchedEffect(configuration,loaded) { if(loaded) {overlay.clear();web.evaluateJavascript(script(web),null)} }
    key(web) { AndroidView(factory={FrameLayout(context).apply {
        addView(web,FrameLayout.LayoutParams(-1,-1));addView(overlay,FrameLayout.LayoutParams(-1,-1))
    }},modifier=Modifier.fillMaxSize().onSizeChanged {
        overlay.clear()
        if(loaded) web.post { if(bridge.active && web.width>0 && web.height>0) web.evaluateJavascript(script(web),null) }
    },update={ _ ->
        val view=web
        view.setBackgroundColor(theme.paper.toArgb())
        if(html!=null && view.tag!=path.path) {
            view.tag=path.path
            view.webViewClient=object:WebViewClient() {
                override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse = loader.shouldInterceptRequest(r.url)
                    ?: WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
                override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean {
                    if(r.url.host=="appassets.androidplatform.net") {
                        val relative=r.url.path?.removePrefix("/book/") ?: return true
                        val target=File(root,relative).canonicalFile
                        val index=publication.chapters.indexOfFirst { it.path.toFile().canonicalFile==target }
                        if(index>=0) latestLink(index,r.url.fragment ?: "")
                    }
                    return true
                }
                override fun onPageFinished(v:WebView,url:String) { loaded=true;v.evaluateJavascript(applicationScript+"\n"+script(v),null) }
            }
            val base="https://appassets.androidplatform.net/book/"+root.toPath().relativize(path.parentFile!!.toPath()).toString().replace('\\','/').let { if(it.isEmpty()) "" else "$it/" }
            val csp="<meta name='viewport' content='width=device-width,initial-scale=1.0'><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self'; script-src 'none'; connect-src 'none'\">"
            view.loadDataWithBaseURL(base,csp+html,"text/html","UTF-8",null)
        }
    }) }
}
