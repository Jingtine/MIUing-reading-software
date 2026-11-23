package com.miuing.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SlideTurnTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrument)

    @Test fun tapSlideWaitsForDestinationVisualState() {
        val old=device.executeShellCommand("settings get global animator_duration_scale").trim()
        device.executeShellCommand("settings put global animator_duration_scale 1")
        val enabledBy=SystemClock.uptimeMillis()+5000
        var enabled=false
        while(!enabled && SystemClock.uptimeMillis()<enabledBy) {
            instrument.runOnMainSync {enabled=android.animation.ValueAnimator.areAnimatorsEnabled()}
            if(!enabled) Thread.sleep(25)
        }
        assertTrue("Visual state test requires enabled system motion",enabled)
        var overlay:PageTurnOverlay?=null;var view:WebView?=null;var acknowledged=false;var requested=false
        var callback:WebView.VisualStateCallback?=null
        try {
            instrument.runOnMainSync {
                view=object:WebView(instrument.targetContext) {
                    override fun postVisualStateCallback(requestId:Long,cb:VisualStateCallback) {requested=true;callback=cb}
                    override fun onDraw(canvas:Canvas) {canvas.drawColor(if(acknowledged) Color.GREEN else Color.RED)}
                }.apply {layout(0,0,400,300)}
                overlay=PageTurnOverlay(instrument.targetContext).apply {layout(0,0,400,300)}
                overlay!!.turn(view!!,"slide",1,Color.WHITE) {ready->ready()}
            }
            Thread.sleep(150)
            instrument.runOnMainSync {
                assertTrue("Slide capture never requested a WebView visual state; motion=${android.animation.ValueAnimator.areAnimatorsEnabled()}",requested)
                val f=PageTurnOverlay::class.java.getDeclaredField("destination");f.isAccessible=true
                assertNull("Unpainted destination was captured",f.get(overlay))
                acknowledged=true;callback!!.onComplete(0)
                val target=f.get(overlay) as? Bitmap
                assertNotNull("Paint acknowledgement must capture the target",target)
                assertEquals(Color.GREEN,target!!.getPixel(10,10))
            }
        } finally {
            instrument.runOnMainSync {overlay?.clear();view?.destroy()}
            device.executeShellCommand(if(old=="null") "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $old")
        }
    }

    @Test fun slideLayersHaveParallaxWithoutMirroredTextAndReleaseFrames() {
        instrument.runOnMainSync {
            val overlay=PageTurnOverlay(instrument.targetContext);overlay.layout(0,0,400,300)
            for(direction in listOf(1,-1)) {
                val source=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.RED);Canvas(this).drawRect(100f,0f,140f,300f,Paint().apply {color=Color.YELLOW})
                }
                val destination=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.GREEN);Canvas(this).drawRect(300f,0f,340f,300f,Paint().apply {color=Color.BLUE})
                }
                overlay.beginDrag(source,direction,Color.WHITE,"slide");overlay.targetFrame(destination)
                val frame=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888)
                try {
                    fun draw(t:Float) {overlay.dragTo(t);frame.eraseColor(Color.MAGENTA);overlay.draw(Canvas(frame))}
                    draw(0f);assertEquals(Color.YELLOW,frame.getPixel(110,100));assertEquals(Color.RED,frame.getPixel(310,100))
                    draw(.25f)
                    if(direction>0) {
                        assertEquals("Outgoing text must translate left, not mirror",Color.YELLOW,frame.getPixel(10,100))
                        assertTrue("Bottom page needs its own shade",Color.green(frame.getPixel(350,100)) in 1..254)
                        assertEquals(0,Color.red(frame.getPixel(350,100)))
                    } else {
                        assertEquals("Incoming previous page must retain text orientation",Color.BLUE,frame.getPixel(10,100))
                        val bottom=frame.getPixel(150,100)
                        assertTrue("Current page must follow at quarter speed",Color.red(bottom) in 1..254 && Color.green(bottom)>0)
                    }
                    draw(1f);assertEquals(Color.GREEN,frame.getPixel(110,100));assertEquals(Color.BLUE,frame.getPixel(310,100))
                } finally {frame.recycle();overlay.clear()}
                assertTrue(source.isRecycled);assertTrue(destination.isRecycled);assertEquals(View.GONE,overlay.visibility)
            }
        }
    }

    private fun select(h:BookReaderHarness) {
        device.openReaderThemes();device.wait(Until.findObject(By.desc("翻页样式")),5000)!!.click()
        val choice=device.wait(Until.findObject(By.text("平移")),3000)
        assertNotNull("iBook slide choice is missing",choice);choice!!.click()
        device.wait(Until.findObject(By.desc("关闭主题与设置")),5000)!!.click()
        h.await {h.js("window.miuingConfig.pageTurn==='slide'")=="true"}
    }
    private fun overlay(h:BookReaderHarness)=h.find {it is PageTurnOverlay} as PageTurnOverlay
    private fun ready(h:BookReaderHarness):Boolean {
        var result=false
        instrument.runOnMainSync {
            val o=overlay(h);result=o.visibility==View.VISIBLE && listOf("snapshot","destination").all {
                val f=o.javaClass.getDeclaredField(it);f.isAccessible=true;val b=f.get(o) as? Bitmap
                b!=null && !b.isRecycled && b.width.toLong()*b.height<=2_000_000
            }
        };return result
    }
    private fun idle(h:BookReaderHarness) {h.await {var idle=false;instrument.runOnMainSync {idle=overlay(h).visibility==View.GONE};idle}}
    private inner class Finger(h:BookReaderHarness,start:Float):AutoCloseable {
        private val down=SystemClock.uptimeMillis();private var x=0f;private var y=0f;private var left=0;private var width=0;private var ended=false
        init {
            instrument.runOnMainSync {
                val w=h.find {it is WebView && it.importantForAccessibility!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}!!
                val p=IntArray(2);w.getLocationOnScreen(p);left=p[0];width=w.width;y=p[1]+w.height*.5f
            };x=left+width*start;send(MotionEvent.ACTION_DOWN)
        }
        private fun send(action:Int) {val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try {instrument.sendPointerSync(e)} finally {e.recycle()}}
        fun move(to:Float) {val end=left+width*to;val from=x;for(i in 1..8) {x=from+(end-from)*i/8;send(MotionEvent.ACTION_MOVE);Thread.sleep(12)}}
        fun release() {send(MotionEvent.ACTION_UP);ended=true}
        override fun close() {if(!ended) send(MotionEvent.ACTION_CANCEL)}
    }
    @Test fun selectionPersistsAndHeldDragCancelsThenCommits() {
        BookReaderHarness(listOf(8,4,4)).use {h->
            select(h);h.scenario.recreate()
            h.await {h.js("!!window.miuingConfig && window.miuingConfig.pageTurn==='slide'")=="true"}
            assertEquals("slide",instrument.targetContext.getSharedPreferences("reading",0).getString("pageTurn",""))
            h.js("document.body.dispatchEvent(new MouseEvent('click',{clientX:window.miuingWidth*.92,clientY:window.miuingHeight*.5,bubbles:true}))")
            h.await {h.js("window.miuingPageMetrics().spread===2")=="true"};idle(h)
            h.await {h.saved().anchor==h.js("window.miuingAnchor()").toInt()}
            val anchor=h.saved().anchor
            Finger(h,.82f).use {f->
                f.move(.4f);h.await {ready(h)}
                assertEquals("2",h.js("window.miuingPageMetrics().spread"));assertEquals(anchor,h.saved().anchor)
                device.takeScreenshot(java.io.File(instrument.targetContext.getExternalFilesDir(null),"slide-held.png"))
            }
            idle(h);assertEquals("2",h.js("window.miuingPageMetrics().spread"))
            Finger(h,.82f).use {f->f.move(.3f);h.await {ready(h)};f.release()}
            idle(h);h.await {h.js("window.miuingPageMetrics().spread===3")=="true"}
            Finger(h,.18f).use {f->f.move(.73f);h.await {ready(h)};f.release()}
            idle(h);h.await {h.js("window.miuingPageMetrics().spread===2")=="true"}
            val old=overlay(h)
            Finger(h,.82f).use {f->
                f.move(.4f);h.await {ready(h)};h.scenario.recreate()
                instrument.runOnMainSync {assertEquals("Recreation retained the old overlay",View.GONE,old.visibility)}
            }
            h.await {h.js("!!window.miuingConfig && window.miuingConfig.pageTurn==='slide' && window.miuingPageMetrics().spread===2")=="true"}
        }
    }
    @Test fun crossChapterSlidePreservesBothBoundaryPages() {
        BookReaderHarness().use {h->
            select(h);h.js("window.miuingJump(1)");h.await {h.js("window.miuingProgress()>.99")=="true"};idle(h)
            Finger(h,.82f).use {f->f.move(.3f);h.await {ready(h)};f.release()}
            h.await {h.saved().chapter==1 && h.js("!!document.getElementById('c1p1') && window.miuingProgress()<.001")=="true"};idle(h)
            Finger(h,.18f).use {f->f.move(.73f);h.await {ready(h)};f.release()}
            h.await {h.saved().chapter==0 && h.js("!!document.getElementById('c0p1') && window.miuingProgress()>.99")=="true"};idle(h)
        }
    }
    @Test fun disabledSystemMotionSkipsSlideSnapshots() {
        val old=device.executeShellCommand("settings get global animator_duration_scale").trim()
        try {
            BookReaderHarness().use {h->
                select(h);device.executeShellCommand("settings put global animator_duration_scale 0")
                h.await {var disabled=false;instrument.runOnMainSync {disabled=!android.animation.ValueAnimator.areAnimatorsEnabled()};disabled}
                h.js("document.body.dispatchEvent(new MouseEvent('click',{clientX:window.miuingWidth*.92,clientY:window.miuingHeight*.5,bubbles:true}))")
                h.await {h.js("window.miuingPageMetrics().spread===2")=="true"};idle(h)
                instrument.runOnMainSync {
                    for(name in listOf("snapshot","destination")) {
                        val f=overlay(h).javaClass.getDeclaredField(name);f.isAccessible=true
                        assertNull("Disabled motion retained $name",f.get(overlay(h)))
                    }
                }
            }
        } finally {device.executeShellCommand(if(old=="null") "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $old")}
    }
}
