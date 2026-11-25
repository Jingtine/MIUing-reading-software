package com.miuing.reader

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.UUID

/** Small real publication with an exact number of physical columns per chapter. */
internal class BookReaderHarness(counts:List<Int> = listOf(3,2,4),holdCounting:Boolean=false,
    transform:(Int,String)->String={_,html->html},extras:Map<String,String> = emptyMap(),
    private val requireTextAnchor:Boolean=true,sourceFactory:((File,String)->File)?=null):AutoCloseable {
    val instrument=InstrumentationRegistry.getInstrumentation()
    val device=UiDevice.getInstance(instrument)
    private val context=instrument.targetContext
    private val title="全书分页测试-${UUID.randomUUID().toString().take(8)}"
    private val file=sourceFactory?.invoke(context.cacheDir,title) ?: File(context.cacheDir,"whole-book.epub")
    val book:Book
    private val chapterCount=counts.size
    var scenario:ActivityScenario<MainActivity>
        private set
    lateinit var root:View
        private set
    var maximumWebViews=0
        private set
    private var countingHeld=holdCounting
    private val blocker=android.view.ViewTreeObserver.OnGlobalLayoutListener {
        if(countingHeld) (find {it is ReadingTurnHost} as? ReadingTurnHost)?.overlay?.visibility=View.VISIBLE
    }
    init {
        device.setOrientationNatural()
        context.getSharedPreferences("reading",0).edit().clear().commit()
        if(sourceFactory==null) ZipOutputStream(file.outputStream()).use {zip->
            val entries=linkedMapOf("META-INF/container.xml" to "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                "book.opf" to "<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>$title</dc:title></metadata><manifest>"+counts.indices.joinToString("") {"<item id='c$it' href='c$it.xhtml' media-type='application/xhtml+xml'/>"}+"</manifest><spine>"+counts.indices.joinToString("") {"<itemref idref='c$it'/>"}+"</spine></package>")
            counts.forEachIndexed {chapter,total->entries["c$chapter.xhtml"]="<html><body>"+(1..total).joinToString("") {"<div style='height:calc(100vh - 50px);break-after:column;break-inside:avoid' id='c${chapter}p$it'>Chapter $chapter page $it. 中文正文。</div>"}+"</body></html>"}
            counts.indices.forEach {i->entries["c$i.xhtml"]=transform(i,entries.getValue("c$i.xhtml"))}
            entries.putAll(extras)
            entries.forEach {(name,text)->zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray());zip.closeEntry()}
        }
        book=runBlocking {LibraryStore(context).import(Uri.fromFile(file))}
        scenario=ActivityScenario.launch(MainActivity::class.java)
        if(holdCounting) scenario.onActivity {root=it.window.decorView;root.viewTreeObserver.addOnGlobalLayoutListener(blocker)}
        try {open()} catch(error:Throwable) {close();throw error}
    }
    fun find(predicate:(View)->Boolean):View? {
        fun walk(v:View):View? {if(predicate(v)) return v;if(v is ViewGroup) repeat(v.childCount) {walk(v.getChildAt(it))?.let {return it}};return null}
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()) return walk(root)
        var result:View?=null;instrument.runOnMainSync {result=walk(root)};return result
    }
    private fun open() {
        device.wait(Until.findObject(By.text(book.title)),10000)!!.click()
        scenario.onActivity {root=it.window.decorView}
        val ready = if(requireTextAnchor) "window.miuingAnchor()>=0" else "window.miuingPageMetrics()!==null"
        await {runCatching {js("!!window.miuingConfig && $ready")=="true"}.getOrDefault(false)}
        if(requireTextAnchor) await {saved().anchor>=0}
    }
    fun reopen() {scenario.close();scenario=ActivityScenario.launch(MainActivity::class.java);open()}
    fun saved():Book {lateinit var result:Book;instrument.runOnMainSync {result=LibraryStore(context).books.first {it.id==book.id}};return result}
    fun cachedCounts():IntArray {
        val key=js("window.miuingConfig.paginationKey").trim('"')
        val identity=book.id+"|"+(0 until chapterCount).joinToString("|") {"c$it.xhtml"}
        return runBlocking {PaginationCache(File(context.cacheDir,"pagination")).read(identity,key,chapterCount)}
    }
    fun js(code:String):String {
        if(!root.isAttachedToWindow) scenario.onActivity {root=it.window.decorView}
        val done=CountDownLatch(1);var result=""
        instrument.runOnMainSync {
            val web=find {it is WebView && it.importantForAccessibility!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS} as? WebView
            if(web==null) {done.countDown();return@runOnMainSync}
            web.evaluateJavascript(code) {result=it;done.countDown()}
        }
        assertTrue(done.await(10,TimeUnit.SECONDS));return result
    }
    fun await(condition:()->Boolean) {
        val deadline=System.currentTimeMillis()+20000
        while(System.currentTimeMillis()<deadline) {
            if(::root.isInitialized) instrument.runOnMainSync {
                fun count(v:View):Int=(if(v is WebView) 1 else 0)+(if(v is ViewGroup) (0 until v.childCount).sumOf {count(v.getChildAt(it))} else 0)
                maximumWebViews=maxOf(maximumWebViews,count(root));assertTrue("More than two live WebViews",maximumWebViews<=2)
            }
            if(condition()) return;Thread.sleep(50)
        }
        val source=runCatching {js("JSON.stringify({config:window.miuingConfig,pages:window.miuingPageMetrics()})")}.getOrNull()
        val host=find {it is ReadingTurnHost} as? ReadingTurnHost
        val cache=File(context.cacheDir,"pagination").walkTopDown().filter {it.extension=="json"}.take(5).map {runCatching {it.readText()}.getOrDefault("unreadable")}.joinToString()
        fail("Whole-book condition timed out: footer=${footer()}, starts=${host?.measurementStarts}, busy=${host?.busy}, source=$source cache=$cache")
    }
    fun footer()=device.findObject(By.descStartsWith("全书第 "))?.contentDescription
    fun releaseCounting() {
        if(!countingHeld) return
        instrument.runOnMainSync {
            countingHeld=false
            if(root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnGlobalLayoutListener(blocker)
            (find {it is ReadingTurnHost} as? ReadingTurnHost)?.overlay?.clear()
        }
    }
    override fun close() {
        releaseCounting()
        scenario.close()
        instrument.runOnMainSync {LibraryStore(context).delete(book.id)}
        file.delete();context.getSharedPreferences("reading",0).edit().clear().commit()
    }
}
