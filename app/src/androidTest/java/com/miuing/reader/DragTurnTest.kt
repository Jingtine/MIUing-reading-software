package com.miuing.reader

import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DragTurnTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrument)
    private lateinit var main:WebView
    private lateinit var root:View
    private lateinit var bookId:String
    private fun savedBook():Book {
        lateinit var result:Book
        // AtomicFile recovery is not a concurrent-reader API. Serialize these
        // disk assertions with the application's main-thread progress writes.
        instrument.runOnMainSync {result=LibraryStore(instrument.targetContext).books.first {it.id==bookId}}
        return result
    }
    private fun find(v:View,predicate:(View)->Boolean):View? {
        if(predicate(v)) return v
        if(v is ViewGroup) repeat(v.childCount) {find(v.getChildAt(it),predicate)?.let {found->return found}}
        return null
    }
    private fun js(code:String,view:WebView=main):String {
        val latch=CountDownLatch(1);var result=""
        instrument.runOnMainSync {view.evaluateJavascript(code) {result=it;latch.countDown()}}
        check(latch.await(10,TimeUnit.SECONDS));return result
    }
    private fun await(condition:()->Boolean) {
        val end=SystemClock.uptimeMillis()+12000
        while(SystemClock.uptimeMillis()<end) {if(condition()) return;Thread.sleep(40)}
        fail("Reader condition timed out")
    }
    private fun visible():Boolean {var result=false;instrument.runOnMainSync {result=find(root) {it is PageTurnOverlay}?.visibility==View.VISIBLE};return result}
    private fun fraction():Float {
        var result=0f
        instrument.runOnMainSync {val o=find(root) {it is PageTurnOverlay}!!;val f=o.javaClass.getDeclaredField("fraction");f.isAccessible=true;result=f.getFloat(o)}
        return result
    }
    private fun framesReady():Boolean {
        var ready=false
        instrument.runOnMainSync {
            val overlay=find(root) {it is PageTurnOverlay}!!
            val images=listOf("snapshot","destination").map {name->
                val field=overlay.javaClass.getDeclaredField(name);field.isAccessible=true;field.get(overlay) as? android.graphics.Bitmap
            }
            ready=images.all {it!=null}
            if(ready) images.forEach {assertTrue("Preview frame exceeds its memory cap",it!!.width.toLong()*it.height<=2_000_000);assertFalse(it.isRecycled)}
        }
        return ready
    }
    private fun fixture(block:(ActivityScenario<MainActivity>,Book)->Unit)=runBlocking {
        val context=instrument.targetContext;val prefs=context.getSharedPreferences("reading",0)
        prefs.edit().clear().commit();device.setOrientationNatural()
        val old=device.executeShellCommand("settings get global animator_duration_scale").trim()
        device.executeShellCommand("settings put global animator_duration_scale 1")
        val file=File(context.cacheDir,"drag-turn.epub");var imported:Book?=null
        try {
            ZipOutputStream(file.outputStream()).use {z->
                val entries=linkedMapOf("META-INF/container.xml" to "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                    "book.opf" to "<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>跟手翻页测试</dc:title></metadata><manifest><item id='a' href='a.xhtml' media-type='application/xhtml+xml'/><item id='b' href='b.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='a'/><itemref idref='b'/></spine></package>")
                for(c in listOf("a","b")) entries["$c.xhtml"]="<html><body><h1 id='$c'>Chapter $c</h1>"+(0..100).joinToString("") {"<p>第${it}段。Reading a passage with a stable location while dragging the next page. 阅读正文，保持阅读位置。</p>"}+"</body></html>"
                entries.forEach {(name,text)->z.putNextEntry(ZipEntry(name));z.write(text.toByteArray());z.closeEntry()}
            }
            val book=LibraryStore(context).import(Uri.fromFile(file));imported=book;bookId=book.id
            ActivityScenario.launch(MainActivity::class.java).use {s->
                device.wait(Until.findObject(By.text("跟手翻页测试")),10000)!!.click()
                await {var found:WebView?=null;s.onActivity {root=it.window.decorView;found=find(root) {v->v is WebView} as? WebView};found?.let {main=it};found!=null}
                await {js("!!window.miuingConfig && window.miuingAnchor()>=0")=="true"};Thread.sleep(250)
                try {block(s,book)} catch(error:Throwable) {
                    android.util.Log.e("DragTurnDiagnostic","Failed with ${savedBook()}; source="+runCatching {js("JSON.stringify({config:window.miuingConfig,progress:window.miuingProgress(),anchor:window.miuingAnchor()})")}.getOrNull())
                    throw error
                }
            }
        } finally {
            imported?.let {LibraryStore(context).delete(it.id)};file.delete();prefs.edit().clear().commit()
            device.executeShellCommand(if(old=="null") "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $old")
        }
    }
    private inner class Finger(start:Float) : AutoCloseable {
        private val down=SystemClock.uptimeMillis();private var x=0f;private var y=0f;private var left=0;private var width=0;private var ended=false
        init {instrument.runOnMainSync {val p=IntArray(2);main.getLocationOnScreen(p);left=p[0];width=main.width;y=p[1]+main.height*.5f};x=left+width*start;send(MotionEvent.ACTION_DOWN)}
        private fun send(action:Int) {val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try {instrument.sendPointerSync(e)} finally {e.recycle()}}
        fun move(to:Float) {val destination=left+width*to;val from=x;for(i in 1..8) {x=from+(destination-from)*i/8;send(MotionEvent.ACTION_MOVE);Thread.sleep(12)}}
        fun release() {send(MotionEvent.ACTION_UP);ended=true}
        override fun close() {if(!ended) {send(MotionEvent.ACTION_CANCEL);ended=true}}
    }
    @Test fun heldDragFollowsFingerWithoutWritingLocationAndRebounds()=fixture {_,book->
        val initial=savedBook()
        Finger(.82f).use {finger->
            finger.move(.64f);await {visible() && framesReady()}
            val first=fraction();assertTrue("Preview must follow a held finger",first in .12f.. .24f)
            finger.move(.48f);await {fraction()>.3f}
            assertEquals("A preview must not advance the source",0.0,js("window.miuingProgress()").toDouble(),.001)
            val stored=savedBook()
            assertEquals(initial.position,stored.position);assertEquals(initial.chapter,stored.chapter)
            device.takeScreenshot(File(instrument.targetContext.getExternalFilesDir(null),"drag-held.png"))
            finger.move(.71f);Thread.sleep(180);finger.release()
        }
        await {!visible()};assertEquals("Reversed short drag must rebound",0.0,js("window.miuingProgress()").toDouble(),.001)
    }
    @Test fun acceptedReleaseAdvancesExactlyOnePage()=fixture {_,_->
        Finger(.84f).use {finger->finger.move(.35f);await {visible()};assertEquals(0.0,js("window.miuingProgress()").toDouble(),.001);finger.release()}
        await {js("window.miuingProgress()>0")=="true"};await {!visible()}
        assertEquals(1.0,js("document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
    }
    private fun awaitDocument(id:String,position:Double=0.0) {
        await {
            var live:WebView?=null
            instrument.runOnMainSync {live=find(root) {it is WebView && it.importantForAccessibility!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS} as? WebView}
            if(live==null) return@await false
            main=live!!
            runCatching {js("!!window.miuingConfig && !!document.getElementById('$id')")=="true"}.getOrDefault(false)
        }
        // Configure exists before its font/layout animation frames have restored
        // the saved position, especially after the no-effect timeout fallback.
        val before=js("JSON.stringify({position:window.miuingProgress(),initial:window.miuingConfig.position,initialAnchor:window.miuingConfig.anchor,width:window.miuingConfig.width,height:window.miuingConfig.height,anchor:window.miuingAnchor()})")
        android.util.Log.i("DragTurnDiagnostic","Document $id before restoration: $before")
        // New chapter navigation saves anchor=-1; the first real progress
        // callback replaces it only after the renderer finishes restoration.
        await {savedBook().anchor>=0}
        await {js("window.miuingConfig.width>0 && window.miuingConfig.height>0 && Math.abs(window.miuingProgress()-$position)<.001")=="true"}
        await {!visible()}
    }
    @Test fun boundaryPreviewCancelsThenCommitsAndReturnsToPreviousChapterEnd()=fixture {_,book->
        js("window.miuingJump(1)");Thread.sleep(220)
        Finger(.82f).use {finger->finger.move(.65f);await {visible()};Thread.sleep(180);finger.release()}
        await {!visible()};assertEquals("true",js("!!document.getElementById('a') && window.miuingProgress()>.99"))
        assertEquals(0,savedBook().chapter)
        Finger(.82f).use {finger->finger.move(.3f);await {visible()};finger.release()}
        awaitDocument("b");assertEquals(0.0,js("window.miuingProgress()").toDouble(),.001)
        Finger(.18f).use {finger->finger.move(.73f);await {visible()};finger.release()}
        awaitDocument("a",1.0);assertEquals(1.0,js("window.miuingProgress()").toDouble(),.001)
    }
    @Test fun canceledTouchAndLayoutChangeNeverCommitAndReleaseSnapshots()=fixture {_,_->
        Finger(.82f).use {finger->finger.move(.3f);await {visible()}}
        await {!visible()};assertEquals(0.0,js("window.miuingProgress()").toDouble(),.001)
        Finger(.82f).use {finger->
            finger.move(.3f);await {visible()}
            js("window.miuingConfigure(Object.assign({},window.miuingConfig,{fontSize:window.miuingConfig.fontSize+1}))")
            await {!visible()};finger.release()
        }
        Thread.sleep(250);assertEquals(0.0,js("window.miuingProgress()").toDouble(),.001)
        instrument.runOnMainSync {
            val overlay=find(root) {it is PageTurnOverlay}!!
            for(name in listOf("snapshot","destination")) {val field=overlay.javaClass.getDeclaredField(name);field.isAccessible=true;assertNull("Canceled drag retained $name",field.get(overlay))}
        }
    }
    @Test fun heldDragDisposalCannotNavigateReplacement()=fixture {s,_->
        val oldRoot=root
        Finger(.82f).use {finger->finger.move(.3f);await {visible()};s.recreate()}
        s.onActivity {root=it.window.decorView}
        awaitDocument("a")
        assertEquals(0.0,js("window.miuingProgress()").toDouble(),.001)
        instrument.runOnMainSync {assertEquals(View.GONE,find(oldRoot) {it is PageTurnOverlay}!!.visibility)}
    }
    @Test fun readyPreviewSurvivesLongHoldAndStillSettles()=fixture {_,_->
        Finger(.84f).use {finger->
            finger.move(.38f);await {visible()}
            var passive:WebView?=null
            instrument.runOnMainSync {passive=find(root) {it is WebView && it!==main} as? WebView}
            assertNotNull("Drag must use one passive renderer",passive)
            await {js("!!window.miuingConfig && window.miuingProgress()>0",passive!!)=="true"}
            Thread.sleep(3400)
            assertEquals(0.0,js("window.miuingProgress()").toDouble(),.001)
            finger.release()
            await {visible() && fraction()>.58f}
        }
        await {!visible()};assertEquals(1.0,js("document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
    }
    @Test fun rapidFreshDragCountsTwiceWithoutTailClick()=fixture {_,_->
        repeat(2) {Finger(.84f).use {finger->finger.move(.35f);finger.release()}}
        await {js("document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth>1.9")=="true"}
        await {!visible()}
        js("document.body.dispatchEvent(new MouseEvent('click',{bubbles:true,detail:1,clientX:window.miuingWidth*.92,clientY:window.miuingHeight*.5}))")
        Thread.sleep(200);assertEquals(2.0,js("document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
    }
    @Test fun keyboardActivationDuringHeldDragDoesNotLeaveAnotherTurnOnRelease()=fixture {_,_->
        Finger(.84f).use {finger->
            finger.move(.35f);await {visible()}
            // End the touch in the same Chromium task, before the asynchronous
            // native keyboard activation can call miuingAdvance and clear it.
            js("""(()=>{
                document.body.dispatchEvent(new MouseEvent('click',{bubbles:true,detail:0,clientX:window.miuingWidth*.92,clientY:window.miuingHeight*.5}));
                const e=new Event('touchend',{bubbles:true,cancelable:true});
                Object.defineProperty(e,'changedTouches',{value:[{clientX:window.miuingWidth*.35,clientY:window.miuingHeight*.5}]});document.body.dispatchEvent(e);
            })()""")
            finger.release()
        }
        await {js("window.miuingProgress()>0")=="true"};await {!visible()};Thread.sleep(200)
        assertEquals(1.0,js("document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
    }
}
