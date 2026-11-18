package com.miuing.reader

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onSizeChanged
import androidx.webkit.WebViewAssetLoader
import com.miuing.core.Publication
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class ReadingBridge(val position:(Float)->Unit,val tap:()->Unit,val selection:(String)->Unit,val chapter:(Int)->Unit) {
    private val main=Handler(Looper.getMainLooper())
    @JavascriptInterface fun progress(p:Float) { main.post { position(p) } }
    @JavascriptInterface fun tap() { main.post { tap.invoke() } }
    @JavascriptInterface fun selection(text:String) { main.post { selection.invoke(text) } }
    @JavascriptInterface fun chapter(direction:Int) { main.post { chapter.invoke(direction) } }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable fun TextReader(publication:Publication,root:File,chapter:Int,position:Float,jump:Int,theme:ReadingTheme,fontSize:Float,scrolling:Boolean,serif:Boolean,search:String,notes:List<Note>,onPosition:(Float)->Unit,onTap:()->Unit,onSelection:(String)->Unit,onChapter:(Int)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val latestPosition by rememberUpdatedState(onPosition)
    val latestTap by rememberUpdatedState(onTap)
    val latestSelection by rememberUpdatedState(onSelection)
    val latestChapter by rememberUpdatedState(onChapter)
    val initialPosition=remember(chapter,jump,scrolling) { position }
    val path=publication.chapters[chapter].path.toFile()
    var html by remember(path) { mutableStateOf<String?>(null) }
    var readError by remember(path) { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        try { html=withContext(Dispatchers.IO) { com.miuing.core.PublicationParser.readMarkup(path.toPath()) } }
        catch(e:java.io.IOException) { readError=e.localizedMessage ?: "章节读取失败" }
    }
    if(readError!=null) { androidx.compose.material3.Text("无法读取此章节：$readError");return }
    val loader=remember(root) { WebViewAssetLoader.Builder().addPathHandler("/book/",WebViewAssetLoader.InternalStoragePathHandler(context,root)).build() }
    val web=remember(path,jump,scrolling) { WebView(context).apply {
        settings.javaScriptEnabled=true
        settings.allowFileAccess=false;settings.allowContentAccess=false
        settings.blockNetworkLoads=true;settings.domStorageEnabled=false
        settings.useWideViewPort=true;settings.loadWithOverviewMode=false
        isVerticalScrollBarEnabled=false;isHorizontalScrollBarEnabled=false
        setBackgroundColor(theme.paper.toArgb())
        addJavascriptInterface(ReadingBridge({latestPosition(it)},{latestTap()},{latestSelection(it)},{latestChapter(chapter+it)}),"MIUing")
    } }
    val foreground="#%06x".format(theme.ink.toArgb() and 0xffffff)
    val background="#%06x".format(theme.paper.toArgb() and 0xffffff)
    val script=remember(chapter,jump,theme,fontSize,scrolling,serif,notes) {
        val highlight=org.json.JSONArray(notes.filter { it.chapter==chapter }.map { it.text }).toString()
        """
        (()=>{
          const previous=window.miuingProgress?window.miuingProgress():$initialPosition;
          const old=document.getElementById('miuing-style');if(old)old.remove();
          const style=document.createElement('style');style.id='miuing-style';
          style.textContent=`html{background:$background!important;color:$foreground!important;scroll-behavior:auto}body{margin:24px!important;font-size:${fontSize}px!important;line-height:1.85!important;font-family:${if(serif) "serif" else "sans-serif"}!important;color:$foreground!important;background:$background!important;overflow-wrap:break-word}img,svg{max-width:100%!important;max-height:80vh!important;object-fit:contain}p{font-size:inherit!important;line-height:inherit!important;color:inherit!important}mark{background:#e8ce6c;color:inherit} ${if(scrolling) "body{max-width:760px;margin:24px auto!important;padding:0 24px} " else "body{height:calc(100vh - 48px);column-width:calc(100vw - 48px);column-gap:48px;column-fill:auto}html{overflow-y:hidden}"}`;
          document.head.appendChild(style);
          const W=window.miuingWidth||screen.width,H=window.miuingHeight||innerHeight;
          let flow=document.getElementById('miuing-content');
          if(!flow){flow=document.createElement('main');flow.id='miuing-content';while(document.body.firstChild)flow.appendChild(document.body.firstChild);const shell=document.createElement('div');shell.id='miuing-scroll';shell.appendChild(flow);document.body.appendChild(shell);}
          const scroller=document.getElementById('miuing-scroll');
          const column=W>=900?(W-96)/2:W-48;
          style.textContent+='body{margin:0!important;padding:0!important;width:'+W+'px!important;height:auto!important;max-width:none!important;column-width:auto!important;column-count:auto!important}#miuing-content{box-sizing:content-box;width:'+(W-48)+'px;margin:24px;'+(${scrolling}?'max-width:760px;margin:24px auto;padding:0 24px;':'height:'+(H-48)+'px;column-width:'+column+'px;column-gap:48px;column-fill:auto;')+'}';
          style.textContent+='html,body{overflow:hidden!important;width:'+W+'px!important;height:'+H+'px!important}#miuing-scroll{width:'+W+'px;height:'+H+'px;overflow-x:'+(${scrolling}?'hidden':'auto')+';overflow-y:'+(${scrolling}?'auto':'hidden')+';scrollbar-width:none}';
          if(${theme.name=="粗体"})document.body.style.fontWeight='700';else document.body.style.fontWeight='normal';
          const scroll=${scrolling};
          function range(){return Math.max(0,scroll?scroller.scrollHeight-H:scroller.scrollWidth-W)}
          function progress(){return range()>0?(scroll?scroller.scrollTop:scroller.scrollLeft)/range():0}
          window.miuingJump=p=>scroller.scrollTo(scroll?0:p*range(),scroll?p*range():0);
          window.miuingProgress=progress;
          requestAnimationFrame(()=>requestAnimationFrame(()=>window.miuingJump(previous)));
          if(!window.miuingBound){
            window.miuingBound=true;
            let timer;scroller.addEventListener('scroll',()=>{clearTimeout(timer);timer=setTimeout(()=>MIUing.progress(window.miuingProgress()),250)});
            document.addEventListener('selectionchange',()=>MIUing.selection(getSelection().toString()));
            let sx=0,sy=0;document.addEventListener('touchstart',e=>{sx=e.changedTouches[0].clientX;sy=e.changedTouches[0].clientY},{passive:true});
            document.addEventListener('touchend',e=>{if(scroll||getSelection().toString())return;const dx=e.changedTouches[0].clientX-sx;const dy=e.changedTouches[0].clientY-sy;if(Math.abs(dx)>55&&Math.abs(dx)>Math.abs(dy)){turn(dx<0?1:-1);e.preventDefault()}},{passive:false});
            function turn(d){if(d>0&&window.miuingProgress()>=.995)MIUing.chapter(1);else if(d<0&&window.miuingProgress()<=.005)MIUing.chapter(-1);else scroller.scrollBy(d*window.miuingWidth,0)}
            document.addEventListener('click',e=>{if(e.target.closest('a')||getSelection().toString())return;if(!scroll&&e.clientX<W*.22)turn(-1);else if(!scroll&&e.clientX>W*.78)turn(1);else MIUing.tap()});
          }
          const texts=$highlight;
          document.querySelectorAll('mark[data-miuing]').forEach(m=>m.replaceWith(document.createTextNode(m.textContent)));
          for(const text of texts){
            if(!text)continue;const nodes=[];const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let node;let full='';
            while(node=walker.nextNode()){if(node.parentElement.closest('script,style,mark'))continue;nodes.push({node,start:full.length});full+=node.textContent;}
            const at=full.indexOf(text);if(at<0)continue;const end=at+text.length;
            const a=nodes.find(x=>x.start+x.node.length>at);const b=nodes.find(x=>x.start+x.node.length>=end);
            if(!a||!b)continue;const range=document.createRange();range.setStart(a.node,at-a.start);range.setEnd(b.node,end-b.start);
            const mark=document.createElement('mark');mark.dataset.miuing='1';mark.appendChild(range.extractContents());range.insertNode(mark);
          }
        })();
        """.trimIndent()
    }
    fun sizedScript(view:WebView):String {
        val density=view.resources.displayMetrics.density
        return "window.miuingWidth=${view.width/density};window.miuingHeight=${view.height/density};"+script
    }
    var loaded by remember(web) { mutableStateOf(false) }
    DisposableEffect(web) { onDispose { web.stopLoading();web.removeJavascriptInterface("MIUing");web.destroy() } }
    LaunchedEffect(script,loaded) { if(loaded) web.evaluateJavascript(sizedScript(web),null) }
    LaunchedEffect(search,loaded) { if(loaded && search.isNotBlank()) web.findAllAsync(search) else web.clearMatches() }
    key(web) { AndroidView(factory={web},modifier=Modifier.fillMaxSize().onSizeChanged { if(loaded) web.post { web.evaluateJavascript(sizedScript(web),null) } },update={ view ->
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
                        if(index>=0 && index!=chapter) latestChapter(index)
                        else if(index==chapter) { v.evaluateJavascript("document.getElementById(${JSONObject.quote(r.url.fragment ?: "")})?.scrollIntoView()",null) }
                    }
                    return true
                }
                override fun onPageFinished(v:WebView,url:String) { loaded=true;v.evaluateJavascript(sizedScript(v),null) }
            }
            val base="https://appassets.androidplatform.net/book/"+root.toPath().relativize(path.parentFile!!.toPath()).toString().replace('\\','/').let { if(it.isEmpty()) "" else "$it/" }
            val csp="<meta name='viewport' content='width=device-width,initial-scale=1.0'><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self'; script-src 'none'; connect-src 'none'\">"
            // Prefix ensures CSP is in effect before any author supplied markup.
            view.loadDataWithBaseURL(base,csp+html,"text/html","UTF-8",null)
        }
    }) }
}
