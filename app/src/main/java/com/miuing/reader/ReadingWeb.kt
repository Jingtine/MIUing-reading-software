package com.miuing.reader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File

/** All bridge callbacks execute on the UI thread and stop with their owning document. */
internal class ReadingBridge(
    private val location:(Float,Int)->Unit={_,_->},private val tap:()->Unit={},
    private val selection:(SelectedText)->Unit={},private val chapter:(Int)->Unit={},
    private val pages:(ReaderPageInfo)->Unit={}
) {
    private val main=Handler(Looper.getMainLooper())
    var active=true
    var turnPage:(Int)->Unit={}
    var onReady:()->Unit={}
    var onDragBegin:(Int,Int,Int,Double)->Unit={_,_,_,_->}
    var onDragMove:(Int,Float)->Unit={_,_->}
    var onDragEnd:(Int,Boolean)->Unit={_,_->}
    var onDragAbort:(Int)->Unit={}
    private fun dispatch(block:()->Unit) {main.post {if(active) block()}}
    @JavascriptInterface fun progress(p:Float,anchor:Int)=dispatch {if(p.isFinite()) location(p,anchor)}
    @JavascriptInterface fun pages(current:Int,total:Int)=dispatch {if(total>0 && current in 1..total) pages(ReaderPageInfo(current,total))}
    @JavascriptInterface fun tap()=dispatch {tap.invoke()}
    @JavascriptInterface fun selection(text:String,start:Int,end:Int)=dispatch {selection(SelectedText(text,start,end))}
    @JavascriptInterface fun chapter(direction:Int)=dispatch {if(direction==-1 || direction==1) chapter.invoke(direction)}
    @JavascriptInterface fun turn(direction:Int)=dispatch {if(direction==-1 || direction==1) turnPage(direction)}
    @JavascriptInterface fun ready()=dispatch {onReady()}
    @JavascriptInterface fun dragBegin(id:Int,direction:Int,chapterDelta:Int,position:Double)=dispatch {
        if(id>0 && (direction==-1 || direction==1) && chapterDelta in -1..1 && position.isFinite() && position in 0.0..1.0) onDragBegin(id,direction,chapterDelta,position)
    }
    @JavascriptInterface fun dragMove(id:Int,progress:Float)=dispatch {if(progress.isFinite()) onDragMove(id,progress.coerceIn(0f,1f))}
    @JavascriptInterface fun dragEnd(id:Int,commit:Boolean)=dispatch {onDragEnd(id,commit)}
    @JavascriptInterface fun dragAbort(id:Int)=dispatch {onDragAbort(id)}
}

/** Use a concrete interface type so Android lint can validate the JS boundary. */
@SuppressLint("SetJavaScriptEnabled")
internal fun configureReadingWeb(view:WebView,bridge:ReadingBridge) {
    val providerMajor=WebView.getCurrentWebViewPackage()?.versionName?.substringBefore('.')?.toIntOrNull()
    if(providerMajor!=null && providerMajor<=69) view.setLayerType(View.LAYER_TYPE_SOFTWARE,null)
    view.settings.apply {
        javaScriptEnabled=true;allowFileAccess=false;allowContentAccess=false;blockNetworkLoads=true
        domStorageEnabled=false;useWideViewPort=true;loadWithOverviewMode=false;standardFontFamily="serif"
    }
    view.isVerticalScrollBarEnabled=false;view.isHorizontalScrollBarEnabled=false
    view.addJavascriptInterface(bridge,"MIUing")
}

internal fun readingLoader(context:Context,root:File)=WebViewAssetLoader.Builder()
    .addPathHandler("/book/",WebViewAssetLoader.InternalStoragePathHandler(context,root))
    .addPathHandler("/reader-assets/",WebViewAssetLoader.AssetsPathHandler(context)).build()

internal fun readingClient(loader:WebViewAssetLoader,finished:(WebView)->Unit,link:(android.net.Uri)->Unit={}):WebViewClient = object:WebViewClient() {
    override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse = loader.shouldInterceptRequest(r.url)
        ?: WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
    override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean {link(r.url);return true}
    override fun onPageFinished(v:WebView,url:String) {finished(v)}
}

internal fun loadReadingMarkup(view:WebView,root:File,path:File,html:String) {
    val directory=root.toPath().relativize(path.parentFile!!.toPath()).toString().replace('\\','/')
    val base="https://appassets.androidplatform.net/book/"+if(directory.isEmpty()) "" else "$directory/"
    val csp="<meta name='viewport' content='width=device-width,initial-scale=1.0'><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self'; script-src 'none'; connect-src 'none'\">"
    view.loadDataWithBaseURL(base,csp+html,"text/html","UTF-8",null)
}

internal fun WebView.afterReadingPaint(block:()->Unit) {
    postVisualStateCallback(0,object:WebView.VisualStateCallback() {override fun onComplete(requestId:Long) {block()}})
}

internal fun destroyReadingWeb(view:WebView,bridge:ReadingBridge) {
    bridge.active=false;view.stopLoading();view.removeJavascriptInterface("MIUing")
    (view.parent as? ViewGroup)?.removeView(view);view.destroy()
}
