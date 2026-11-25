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
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.miuing.core.Publication
import com.miuing.core.PublicationParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal fun loadReaderScript(context:android.content.Context):String =
    listOf("reader-text.js","reader-gestures.js","reader-typography.js","reader-pagination.js","reader-footnotes.js","reader.js").joinToString("\n") { asset ->
        context.assets.open(asset).bufferedReader().use { it.readText() }
    }

@SuppressLint("SetJavaScriptEnabled")
@Composable fun TextReader(
    publication:Publication,root:File,chapter:Int,position:Float,jump:Int,theme:ReadingTheme,fontSize:Float,
    scrolling:Boolean,serif:Boolean,search:String,notes:List<Note>,onPosition:(Float)->Unit,onTap:()->Unit,
    onSelection:(String)->Unit,onChapter:(Int)->Unit,anchor:Int=-1,fragment:String="",
    lineHeight:Float=1.85f,margin:Float=24f,letterSpacing:Float=0f,pageTurn:String="curl",
    fontFamily:String=if(serif) "serif" else "sans",boldText:Boolean=theme.name=="粗体",wordSpacing:Float=0f,justify:Boolean=false,customLayout:Boolean=true,
    chineseLayout:Boolean=false,firstLineIndent:Float=2f,paragraphSpacing:Float=.5f,
    onLocation:(Float,Int)->Unit={p,_->onPosition(p)},
    onSelectionRange:(SelectedText)->Unit={onSelection(it.text)},onLink:(Int,String)->Unit={i,_->onChapter(i)},
    onPages:(ReaderPageInfo)->Unit={},onLoadError:()->Unit={},onPagination:(BookPaginationState?)->Unit={},
    searchIndex:Int=0,onSearchMatch:(SearchMatchInfo)->Unit={},locationCapture:ReadingLocationCapture?=null,
    dismissFootnote:Int=0,onFootnoteState:(Boolean)->Unit={},
    pauseCounting:Boolean=false,onSearchEngine:(BookSearchEngine?)->Unit={}
) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val systemTypography=systemTypographyIdentity(androidx.compose.ui.platform.LocalConfiguration.current)
    val host=remember(root) {ReadingTurnHost(context)}
    DisposableEffect(host) {onDispose {host.close()}}
    val latestPagination by rememberUpdatedState(onPagination)
    val paginator=remember(root) {BookPagination(File(context.cacheDir,"pagination"),host) {latestPagination(it)}}
    val binding=remember(root) {ReadingLayoutBinding()}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val searchEngine=remember(host,binding) {BookSearchEngine(host,binding)}
    val latestSearchEngine by rememberUpdatedState(onSearchEngine)
    DisposableEffect(searchEngine) {latestSearchEngine(searchEngine);onDispose {latestSearchEngine(null)}}
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(pauseCounting,lifecycleState) {paginator.foreground(lifecycleState.isAtLeast(Lifecycle.State.STARTED) && !pauseCounting)}
    DisposableEffect(paginator) {onDispose {paginator.close()}}
    val latestLocation by rememberUpdatedState(onLocation)
    val latestTap by rememberUpdatedState(onTap)
    val latestSelection by rememberUpdatedState(onSelectionRange)
    val latestChapter by rememberUpdatedState(onChapter)
    val latestLink by rememberUpdatedState(onLink)
    val latestPages by rememberUpdatedState(onPages)
    val latestSearchMatch by rememberUpdatedState(onSearchMatch)
    val latestFootnoteState by rememberUpdatedState(onFootnoteState)
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
        LaunchedEffect(path,jump,scrolling) {paginator.reset();host.invalidateLayout();latestLoadError()}
        Text("无法读取此章节：$readError");return
    }
    val loader=remember(root) {readingLoader(context,root)}
    val bridge:ReadingBridge=remember(path,jump,scrolling) { ReadingBridge({p,a->latestLocation(p,a)},{latestTap()},{latestSelection(it)},{latestChapter(chapter+it)},{latestPages(it)}) }
    val webResult=remember(path,jump,scrolling) {runCatching {WebView(context).also {configureReadingWeb(it,bridge)}}}
    val web=webResult.getOrNull()
    if(web==null) {
        LaunchedEffect(path,jump,scrolling) {paginator.reset();latestLoadError()}
        Text("系统网页组件不可用，请启用或更新 Android System WebView。");return
    }
    val initial=remember(web) { Triple(position,anchor,fragment) }
    val footnotes=remember(web) {ReadingFootnotes(web,root,
        {file->publication.chapters.any {it.path.toFile().canonicalFile==file}},
        {file,fragment->val index=publication.chapters.indexOfFirst {it.path.toFile().canonicalFile==file};if(index>=0) latestLink(index,fragment)})}
    DisposableEffect(footnotes) {onDispose {footnotes.close()}}
    LaunchedEffect(web,dismissFootnote) {if(dismissFootnote>0) web.evaluateJavascript("if(window.miuingDismissFootnote) window.miuingDismissFootnote()",null)}
    val configuration=JSONObject().apply {
        put("position",initial.first);put("anchor",initial.second);put("fragment",initial.third)
        put("search",search)
        put("searchIndex",searchIndex)
        put("paper","#%06x".format(theme.paper.toArgb() and 0xffffff))
        put("ink","#%06x".format(theme.ink.toArgb() and 0xffffff))
        put("fontSize",fontSize);put("scrolling",scrolling);put("serif",serif);put("bold",boldText)
        put("lineHeight",lineHeight);put("margin",margin);put("letterSpacing",letterSpacing)
        put("pageTurn",pageTurn);put("fontFamily",fontFamily);put("wordSpacing",wordSpacing);put("justify",justify);put("customLayout",customLayout)
        put("chineseLayout",chineseLayout);put("firstLineIndent",firstLineIndent);put("paragraphSpacing",paragraphSpacing)
        put("hasPreviousChapter",chapter>0);put("hasNextChapter",chapter<publication.chapters.lastIndex)
        put("dragEnabled",ValueAnimator.areAnimatorsEnabled())
        put("fontFace",ReadingFonts.face(fontFamily))
        put("notes",JSONArray(notes.filter { it.chapter==chapter }.map { JSONObject().put("text",it.text).put("start",it.start).put("end",it.end) }))
    }.toString()
    fun configure(view:WebView,install:Boolean=false) {
        view.doOnLayout {
            if(!bridge.active || view.width<=0 || view.height<=0) return@doOnLayout
            val density=view.resources.displayMetrics.density
            val layout=ReadingLayout(root,publication,chapter,configuration,notes.toList(),applicationScript,loader,html,view.width/density,view.height/density,systemTypography)
            if(binding.layout?.key!=layout.key || scrolling) paginator.reset()
            binding.owner=view;binding.layout=layout
            host.configure(view,layout.key)
            view.evaluateJavascript((if(install) "$applicationScript\n" else "")+"window.miuingConfigure(${layout.configuration});",null)
        }
    }
    var loaded by remember(web) { mutableStateOf(false) }
    bridge.turnPage={direction->if(loaded) host.turn(web,pageTurn,direction,theme.paper.toArgb()) {ready->web.evaluateJavascript("window.miuingAdvance($direction)") {ready()}}}
    bridge.onReady={host.ready(web)}
    bridge.onOpenFragment = { fragment -> latestLink(chapter, fragment) }
    bridge.onSearchMatch={latestSearchMatch(it)}
    bridge.onFootnote={footnotes.open(it)}
    bridge.onFootnoteNavigate={footnotes.original(it)}
    bridge.onFootnoteState={if(!it) footnotes.cancel();latestFootnoteState(it)}
    bridge.onPagination={key,range->
        val layout=binding.layout
        if(binding.owner===web && layout?.key==key && !scrolling) paginator.source(layout.plan,chapter,range)
    }
    bridge.onDragBegin={id,direction,delta,targetPosition->
        val targetChapter=chapter+delta
        if(loaded && targetChapter in publication.chapters.indices && !scrolling && pageTurn in listOf("curl","slide")) {
            binding.layout?.let {host.begin(web,id,direction,targetPosition,it.preview(targetChapter),theme.paper.toArgb(),pageTurn)}
        }
    }
    bridge.onDragMove={id,p->host.move(web,id,p)}
    bridge.onDragEnd={id,commit->host.end(web,id,commit)}
    bridge.onDragAbort={id->host.abort(web,id)}
    DisposableEffect(web,locationCapture) {onDispose {locationCapture?.unbind(web);host.unbind(web);destroyReadingWeb(web,bridge)}}
    LaunchedEffect(configuration,loaded,systemTypography) {if(loaded) configure(web)}
    key(host) {AndroidView(factory={host},modifier=Modifier.fillMaxSize().onSizeChanged {
        if(host.viewportChanged(it.width,it.height) && loaded) web.post {if(bridge.active) configure(web)}
    },update={ _ ->
        locationCapture?.bind(web)
        host.bind(web,path.path)
        val view=web
        view.setBackgroundColor(theme.paper.toArgb())
        if(html!=null && view.tag!=path.path) {
            view.tag=path.path
            view.webViewClient=readingClient(loader,{v->loaded=true;configure(v,true)},{uri->
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
