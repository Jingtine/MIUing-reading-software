package com.miuing.reader

import android.net.Uri
import android.webkit.WebView
import com.miuing.core.EpubFootnotes
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

/** Serial, cancellable extraction for one owning document; never creates another renderer. */
internal class ReadingFootnotes(private val view:WebView,private val root:File,
    private val canNavigate:(File)->Boolean,private val navigate:(File,String)->Unit) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null
    private var revision=0
    private var closed=false
    private fun target(href:String):Pair<File,String>?=runCatching {
        val uri=Uri.parse(href);val path=uri.path ?: return null;val fragment=uri.fragment ?: return null
        if(href.length>4096 || fragment.isBlank() || fragment.length>1024 || uri.scheme!="https" || uri.authority!="appassets.androidplatform.net" || !path.startsWith("/book/")) return null
        val file=File(root,path.removePrefix("/book/")).canonicalFile
        if(!file.toPath().startsWith(root.canonicalFile.toPath()) || !file.isFile) return null
        file to fragment
    }.getOrNull()
    fun open(href:String) {
        if(closed) return
        cancel();val token=revision;val target=target(href)
        val original=target!=null && canNavigate(target.first)
        if(target==null) {view.evaluateJavascript("window.miuingFootnoteError('无法显示脚注',false)",null);return}
        job=scope.launch {
            try {
                val html=withContext(Dispatchers.IO) {EpubFootnotes.read(target.first.toPath(),target.second)}
                if(!closed && token==revision) view.evaluateJavascript("window.miuingShowFootnote(${JSONObject.quote(html)},$original)",null)
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {if(!closed && token==revision) view.evaluateJavascript("window.miuingFootnoteError('无法显示脚注，可尝试查看原文。',$original)",null)}
        }
    }
    fun original(href:String) {if(!closed) target(href)?.takeIf {canNavigate(it.first)}?.let {navigate(it.first,it.second)}}
    fun cancel() {revision++;job?.cancel();job=null}
    fun close() {closed=true;cancel();scope.cancel()}
}
