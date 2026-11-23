package com.miuing.reader

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Import-time adapter only; the reader continues using its existing local EPUB pipeline. */
internal object BookFormatImporter {
    val formats=setOf("mobi","azw","azw3","fb2")
    private const val host="appassets.androidplatform.net"
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun convert(context:Context,source:File,destination:File,format:String)=withContext(Dispatchers.IO) {
        require(format in formats)
        require(source.length()<=64L*1024*1024) {"此格式目前支持64MiB以内的文件"}
        val writer=ConvertedBookWriter(File(destination.parentFile,"convert-${UUID.randomUUID()}"))
        val active=AtomicBoolean(true);var web:WebView?=null
        try {
            val metadata=withTimeout(60_000) {withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<String> {continuation->
                    fun fail(message:String) {
                        if(active.compareAndSet(true,false) && continuation.isActive) continuation.resumeWithException(IOException(message.take(2048)))
                    }
                    val bridge=ImportBridge(format,writer,active,{json->
                        if(json.length>2*1024*1024) fail("图书目录信息过大")
                        else if(active.compareAndSet(true,false) && continuation.isActive) continuation.resume(json)
                    },::fail)
                    continuation.invokeOnCancellation {active.set(false);writer.close()}
                    val loader=WebViewAssetLoader.Builder().addPathHandler("/import/",WebViewAssetLoader.AssetsPathHandler(context))
                        .addPathHandler("/source/") {path->
                            if(path=="book" && active.get()) WebResourceResponse("application/octet-stream",null,source.inputStream()) else null
                        }.build()
                    web=WebView(context.applicationContext).apply {
                        settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false
                        settings.blockNetworkLoads=true;settings.domStorageEnabled=false
                        addJavascriptInterface(bridge,"MIUingImport")
                        webViewClient=object:WebViewClient() {
                            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true
                            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse {
                                val url=request.url
                                return (if(url.scheme=="https" && url.host==host) loader.shouldInterceptRequest(url) else null)
                                    ?: WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
                            }
                            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {fail("图书过大或内容损坏，导入未完成");return true}
                        }
                        loadUrl("https://$host/import/book-import.html")
                    }
                }
            }}
            ensureActive();writer.complete(JSONObject(metadata),destination)
        } catch(e:TimeoutCancellationException) {throw IOException("图书解析超时，请检查文件是否损坏",e)}
        finally {
            active.set(false);writer.close()
            withContext(NonCancellable+Dispatchers.Main.immediate) {web?.let {it.stopLoading();it.removeJavascriptInterface("MIUingImport");it.destroy()};web=null}
            withContext(NonCancellable+Dispatchers.IO) {writer.directory.deleteRecursively()}
        }
    }
    private class ImportBridge(
        private val sourceFormat:String,private val writer:ConvertedBookWriter,private val active:AtomicBoolean,
        private val completed:(String)->Unit,private val failed:(String)->Unit
    ) {
        @JavascriptInterface fun format()=sourceFormat
        @JavascriptInterface fun write(name:String,data:String,last:Boolean)=active.get() && writer.write(name,data,last)
        @JavascriptInterface fun complete(metadata:String)=completed(metadata)
        @JavascriptInterface fun fail(message:String)=failed("无法导入：$message")
    }
}
