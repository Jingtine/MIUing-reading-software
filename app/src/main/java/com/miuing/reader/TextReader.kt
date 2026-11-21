package com.miuing.reader

import android.annotation.SuppressLint
import android.webkit.*
import android.animation.ValueAnimator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import com.miuing.core.Publication
import com.miuing.core.PublicationParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal fun loadReaderScript(context:android.content.Context):String =
    listOf("reader-text.js","reader-gestures.js","reader.js").joinToString("\n") { asset ->
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
    val host=remember(root) {ReadingTurnHost(context)}
    DisposableEffect(host) {onDispose {host.close()}}
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
        LaunchedEffect(path,jump,scrolling) {host.invalidateLayout();latestLoadError()}
        Text("无法读取此章节：$readError");return
    }
    val loader=remember(root) {readingLoader(context,root)}
    val bridge:ReadingBridge=remember(path,jump,scrolling) { ReadingBridge({p,a->latestLocation(p,a)},{latestTap()},{latestSelection(it)},{latestChapter(chapter+it)},{latestPages(it)}) }
    val webResult=remember(path,jump,scrolling) {runCatching {WebView(context).also {configureReadingWeb(it,bridge)}}}
    val web=webResult.getOrNull()
    if(web==null) {
        LaunchedEffect(path,jump,scrolling) {latestLoadError()}
        Text("系统网页组件不可用，请启用或更新 Android System WebView。");return
    }
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
        put("hasPreviousChapter",chapter>0);put("hasNextChapter",chapter<publication.chapters.lastIndex)
        put("dragEnabled",ValueAnimator.areAnimatorsEnabled())
        put("fontFace",ReadingFonts.face(fontFamily))
        put("notes",JSONArray(notes.filter { it.chapter==chapter }.map { JSONObject().put("text",it.text).put("start",it.start).put("end",it.end) }))
    }.toString()
    fun script(view:WebView):String {
        val config=JSONObject(configuration)
        val density=view.resources.displayMetrics.density
        config.put("width",view.width/density);config.put("height",view.height/density)
        return "window.miuingConfigure($config);"
    }
    var loaded by remember(web) { mutableStateOf(false) }
    bridge.turnPage={direction->if(loaded) host.overlay.turn(web,pageTurn,direction,theme.paper.toArgb()) {ready->web.evaluateJavascript("window.miuingAdvance($direction)") {ready()}}}
    bridge.onReady={host.ready(web)}
    bridge.onDragBegin={id,direction,delta,targetPosition->
        val targetChapter=chapter+delta
        if(loaded && targetChapter in publication.chapters.indices && !scrolling && pageTurn=="curl") {
            val targetPath=publication.chapters[targetChapter].path.toFile()
            val config=JSONObject(configuration).apply {
                put("position",0);put("anchor",0);put("fragment","");put("dragEnabled",false)
                put("hasPreviousChapter",targetChapter>0);put("hasNextChapter",targetChapter<publication.chapters.lastIndex)
                if(delta!=0) put("search","")
                put("notes",JSONArray(notes.filter {it.chapter==targetChapter}.map {JSONObject().put("text",it.text).put("start",it.start).put("end",it.end)}))
                val density=web.resources.displayMetrics.density
                put("width",web.width/density);put("height",web.height/density)
            }
            host.begin(web,id,direction,targetPosition,ReadingPreviewSpec(root,targetPath,config.toString(),applicationScript,loader,if(delta==0) html else null),theme.paper.toArgb())
        }
    }
    bridge.onDragMove={id,p->host.move(web,id,p)}
    bridge.onDragEnd={id,commit->host.end(web,id,commit)}
    bridge.onDragAbort={id->host.abort(web,id)}
    DisposableEffect(web) {onDispose {host.unbind(web);destroyReadingWeb(web,bridge)}}
    LaunchedEffect(configuration,loaded) {if(loaded) {host.configure(web);web.evaluateJavascript(script(web),null)}}
    key(host) {AndroidView(factory={host},modifier=Modifier.fillMaxSize().onSizeChanged {
        host.invalidateLayout()
        if(loaded) web.post { if(bridge.active && web.width>0 && web.height>0) web.evaluateJavascript(script(web),null) }
    },update={ _ ->
        host.bind(web,path.path)
        val view=web
        view.setBackgroundColor(theme.paper.toArgb())
        if(html!=null && view.tag!=path.path) {
            view.tag=path.path
            view.webViewClient=readingClient(loader,{v->loaded=true;v.evaluateJavascript(applicationScript+"\n"+script(v),null)},{uri->
                    if(uri.host=="appassets.androidplatform.net") {
                        val relative=uri.path?.removePrefix("/book/") ?: ""
                        val target=File(root,relative).canonicalFile
                        val index=publication.chapters.indexOfFirst { it.path.toFile().canonicalFile==target }
                        if(index>=0) latestLink(index,uri.fragment ?: "")
                    }
            })
            loadReadingMarkup(view,root,path,html!!)
        }
    }) }
}
