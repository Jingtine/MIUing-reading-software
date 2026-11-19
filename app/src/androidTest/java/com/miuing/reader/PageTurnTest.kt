package com.miuing.reader

import android.net.Uri
import android.animation.ValueAnimator
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
class PageTurnTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrument)
    private fun web(v:View):WebView? {
        if(v is WebView) return v
        if(v is ViewGroup) repeat(v.childCount) {web(v.getChildAt(it))?.let {w->return w}}
        return null
    }
    private fun js(s:ActivityScenario<MainActivity>,code:String):String {
        val latch=CountDownLatch(1);var value=""
        s.onActivity {web(it.window.decorView)!!.evaluateJavascript(code) {result->value=result;latch.countDown()}}
        check(latch.await(10,TimeUnit.SECONDS));return value
    }
    private fun awaitJs(s:ActivityScenario<MainActivity>,code:String) {
        val end=System.currentTimeMillis()+15000
        while(System.currentTimeMillis()<end) {
            if(runCatching {js(s,code)=="true"}.getOrDefault(false)) {Thread.sleep(100);return}
            Thread.sleep(60)
        }
        fail("Reader condition timed out: $code")
    }
    private fun tap(s:ActivityScenario<MainActivity>,fraction:Float) {
        var x=0;var y=0
        s.onActivity {val w=web(it.window.decorView)!!;val p=IntArray(2);w.getLocationOnScreen(p);x=p[0]+(w.width*fraction).toInt();y=p[1]+w.height/2}
        device.click(x,y)
    }
    private fun fixture(chapters:Int=2,block:(ActivityScenario<MainActivity>,Book)->Unit)=runBlocking {
        val context=instrument.targetContext
        val prefs=context.getSharedPreferences("reading",0);prefs.edit().clear().commit()
        val file=File(context.cacheDir,"turns.epub")
        ZipOutputStream(file.outputStream()).use {zip->
            val entries=linkedMapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                "book.opf" to "<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>翻页行为测试</dc:title></metadata><manifest>"+(0 until chapters).joinToString("") {"<item id='c$it' href='c$it.xhtml' media-type='application/xhtml+xml'/>"}+"</manifest><spine>"+(0 until chapters).joinToString("") {"<itemref idref='c$it'/>"}+"</spine></package>"
            )
            for(i in 0 until chapters) entries["c$i.xhtml"]="<html><body><h1 id='chapter$i'>Chapter $i</h1>"+(0..120).joinToString("") {"<p id='p$it'>第${it}段。This is a readable passage for testing a single page turn and preserving the reading position. 阅读正文。</p>"}+"</body></html>"
            entries.forEach {(path,text)->zip.putNextEntry(ZipEntry(path));zip.write(text.toByteArray());zip.closeEntry()}
        }
        var id:String?=null
        try {
            val book=LibraryStore(context).import(Uri.fromFile(file));id=book.id
            ActivityScenario.launch(MainActivity::class.java).use {s->
                device.wait(Until.findObject(By.text("翻页行为测试")),10000)!!.click()
                awaitJs(s,"!!window.miuingConfig && typeof window.miuingAnchor==='function'")
                block(s,book)
            }
        } finally {id?.let {LibraryStore(context).delete(it)};file.delete();prefs.edit().clear().commit()}
    }
    private fun withAnimationScale(scale:String,block:()->Unit) {
        val previous=device.executeShellCommand("settings get global animator_duration_scale").trim()
        try {device.executeShellCommand("settings put global animator_duration_scale $scale");block()}
        finally {device.executeShellCommand(if(previous=="null") "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $previous")}
    }
    private fun rawJs(view:WebView,code:String):String {
        val latch=CountDownLatch(1);var result=""
        instrument.runOnMainSync {view.evaluateJavascript(code) {result=it;latch.countDown()}}
        assertTrue("JavaScript did not respond",latch.await(10,TimeUnit.SECONDS));return result
    }
    @Test fun slowHorizontalSwipeTurnsButStationaryLongPressDoesNot()=withAnimationScale("0") {
        fixture {s,_->
            js(s,"window.chromeTouch=(name,x)=>{const e=new Event(name,{bubbles:true,cancelable:true});Object.defineProperty(e,'changedTouches',{value:[{clientX:window.miuingWidth*x,clientY:window.miuingHeight*.5}]});document.body.dispatchEvent(e)};window.chromeTouch('touchstart',.8)")
            Thread.sleep(700)
            js(s,"window.chromeTouch('touchend',.2);document.body.dispatchEvent(new MouseEvent('click',{bubbles:true,detail:1,clientX:window.miuingWidth*.2,clientY:window.miuingHeight*.5}))")
            awaitJs(s,"window.miuingProgress()>0")
            assertEquals("A deliberate slow swipe was treated as a long press",1.0,js(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
            js(s,"window.chromeTouch('touchstart',.92)")
            Thread.sleep(700)
            js(s,"window.chromeTouch('touchend',.92);document.body.dispatchEvent(new MouseEvent('click',{bubbles:true,detail:1,clientX:window.miuingWidth*.92,clientY:window.miuingHeight*.5}))")
            Thread.sleep(200)
            assertEquals("A stationary long press must not turn the page",1.0,js(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
        }
    }
    @Test fun freshTapAfterSwipeIsAcceptedWithoutItsSyntheticClick()=withAnimationScale("0") {
        fixture {s,_->
            js(s,"""(()=>{
                const target=document.body,w=window.miuingWidth,h=window.miuingHeight;
                function touch(name,x) {const e=new Event(name,{bubbles:true,cancelable:true});Object.defineProperty(e,'changedTouches',{value:[{clientX:x,clientY:h*.5}]});target.dispatchEvent(e)}
                function click() {target.dispatchEvent(new MouseEvent('click',{bubbles:true,detail:1,clientX:w*.92,clientY:h*.5}))}
                touch('touchstart',w*.8);touch('touchend',w*.2);click();
                touch('touchstart',w*.92);touch('touchend',w*.92);click();
            })()""")
            awaitJs(s,"window.miuingProgress()>0")
            assertEquals("A swipe and a distinct fresh tap must count twice, without the swipe's synthetic click",2.0,js(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
        }
    }
    @Test fun rapidTurnDuringAnimationIsNotDropped()=withAnimationScale("3") {
        fixture {s,_->
            var view:WebView?=null;var root:View?=null
            s.onActivity {view=web(it.window.decorView);root=it.window.decorView}
            val click="document.body.dispatchEvent(new MouseEvent('click',{bubbles:true,clientX:window.miuingWidth*.92,clientY:window.miuingHeight*.5}))"
            rawJs(view!!,click)
            var active=false;val by=System.currentTimeMillis()+3000
            while(!active && System.currentTimeMillis()<by) {instrument.runOnMainSync {active=overlay(root!!)!!.visibility==View.VISIBLE};if(!active) Thread.sleep(10)}
            assertTrue("The interruption must happen during an actual animation",active)
            rawJs(view!!,click)
            // Inspect without ActivityScenario's idle barrier, while the old turn
            // would otherwise still be running at three times the system duration.
            var turns=0.0;val advancedBy=System.currentTimeMillis()+3000
            while(turns<1.9 && System.currentTimeMillis()<advancedBy) {
                turns=rawJs(view!!,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble()
                if(turns<1.9) Thread.sleep(20)
            }
            assertEquals("A new turn was lost while the previous snapshot was active",2.0,turns,.02)
            instrument.runOnMainSync {assertEquals("Interrupted animation retained its snapshot",View.GONE,overlay(root!!)!!.visibility)}
        }
    }
    @Test fun delayedSwipeClickIsIgnoredButKeyboardClickWorks()=withAnimationScale("0") {
        fixture {s,_->
            js(s,"""(()=>{
                const w=window.miuingWidth,h=window.miuingHeight,target=document.body;
                for(const pair of [['touchstart',w*.8],['touchend',w*.2]]) {
                    const e=new Event(pair[0],{bubbles:true,cancelable:true});
                    Object.defineProperty(e,'changedTouches',{value:[{clientX:pair[1],clientY:h*.5}]});target.dispatchEvent(e);
                }
            })()""")
            awaitJs(s,"window.miuingProgress()>0")
            Thread.sleep(500)
            js(s,"document.body.dispatchEvent(new MouseEvent('click',{bubbles:true,detail:1,clientX:window.miuingWidth*.2,clientY:window.miuingHeight*.5}))")
            Thread.sleep(250)
            assertEquals("A delayed synthetic swipe click must not turn backward",1.0,js(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
            js(s,"document.body.dispatchEvent(new MouseEvent('click',{bubbles:true,detail:0,clientX:window.miuingWidth*.92,clientY:window.miuingHeight*.5}))")
            awaitJs(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth>1.9")
            assertEquals("Touch suppression must not block keyboard/accessibility activation",2.0,js(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
            js(s,"document.body.dispatchEvent(new Event('pointerdown',{bubbles:true}));document.body.dispatchEvent(new MouseEvent('click',{bubbles:true,detail:1,clientX:window.miuingWidth*.92,clientY:window.miuingHeight*.5}))")
            awaitJs(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth>2.9")
            assertEquals("A fresh mouse/pen pointer must start a new gesture",3.0,js(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
        }
    }
    @Test fun longChapterKeepsItsFirstAndLastPagesReachable()=fixture(chapters=1) {s,_->
        js(s,"(()=>{const page='<p style=\"margin:0;height:'+ (window.miuingConfig.height-2*window.miuingConfig.margin)+'px;break-after:column\">A complete page in a long chapter.</p>';document.getElementById('miuing-content').insertAdjacentHTML('beforeend',page.repeat(420));window.miuingJump(0)})()")
        assertEquals("Fixture must exceed 202 viewports","true",js(s,"document.getElementById('miuing-scroll').scrollWidth/window.miuingWidth>202"))
        js(s,"(()=>{const e=document.getElementById('miuing-scroll');window.miuingJump(1);e.scrollLeft-=window.miuingWidth})()")
        Thread.sleep(250);tap(s,.92f);Thread.sleep(650)
        val lastReached=js(s,"(()=>{const e=document.getElementById('miuing-scroll');return e.scrollWidth-e.clientWidth-e.scrollLeft<=1})()")
        js(s,"document.getElementById('miuing-scroll').scrollLeft=window.miuingWidth")
        Thread.sleep(250);tap(s,.08f);Thread.sleep(650)
        val firstReached=js(s,"document.getElementById('miuing-scroll').scrollLeft<=1")
        assertTrue("Long chapter boundary pages were skipped: last=$lastReached, first=$firstReached",lastReached=="true" && firstReached=="true")
    }
    @Test fun oneTurnPerGestureAndReverseChapterLanding()=fixture {s,_->
        js(s,"window.gestureTrace=[];['touchstart','touchend','click'].forEach(name=>document.addEventListener(name,e=>window.gestureTrace.push({name,time:Date.now(),x:e.changedTouches?e.changedTouches[0].clientX:e.clientX,detail:e.detail,selection:getSelection().toString(),page:document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth})))")
        tap(s,.08f);Thread.sleep(400)
        assertEquals("First chapter should remain at its beginning",0.0,js(s,"window.miuingProgress()").toDouble(),.001)
        tap(s,.92f)
        awaitJs(s,"window.miuingProgress()>0")
        assertEquals("Tap must advance exactly one viewport",1.0,js(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
        var x=0;var y=0;var width=0
        s.onActivity {val w=web(it.window.decorView)!!;val p=IntArray(2);w.getLocationOnScreen(p);x=p[0];y=p[1]+w.height/2;width=w.width}
        device.swipe(x+width*4/5,y,x+width/5,y,20);Thread.sleep(650)
        assertEquals("Swipe must advance once without native scrolling or a synthetic click; "+js(s,"JSON.stringify(window.gestureTrace)"),2.0,js(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
        js(s,"window.miuingJump(0)");Thread.sleep(200)
        js(s,"(()=>{const r=document.createRange();r.selectNodeContents(document.getElementById('chapter0'));getSelection().removeAllRanges();getSelection().addRange(r)})()")
        tap(s,.92f);Thread.sleep(400)
        assertEquals("Text selection should suppress turning",0.0,js(s,"window.miuingProgress()").toDouble(),.001)
        js(s,"getSelection().removeAllRanges()");Thread.sleep(300)
        device.readerAction("下一章").click()
        awaitJs(s,"!!document.getElementById('chapter1') && !!window.miuingConfig")
        tap(s,.08f)
        awaitJs(s,"!!document.getElementById('chapter0') && !!window.miuingConfig")
        awaitJs(s,"window.miuingProgress()>.99")
        assertTrue("Backward chapter navigation must reach the previous chapter's end",js(s,"window.miuingProgress()").toDouble()>.99)
        device.readerAction("下一章").click()
        awaitJs(s,"!!document.getElementById('chapter1') && !!window.miuingConfig")
        js(s,"window.miuingJump(1)");Thread.sleep(250);tap(s,.92f);Thread.sleep(400)
        assertEquals("Final chapter must remain at its end","true",js(s,"!!document.getElementById('chapter1') && window.miuingProgress()>.99"))
    }
    @Test fun effectsPersistAndReleaseTheirSnapshots() {
        val old=device.executeShellCommand("settings get global animator_duration_scale").trim()
        try {
            // Slow real system animations to capture an intermediate rendered frame.
            device.executeShellCommand("settings put global animator_duration_scale 3")
            fixture {s,_->
        device.openReaderThemes()
        val style=device.wait(Until.findObject(By.desc("翻页样式")),5000)
        assertNotNull("Page turn style choices are missing",style);style.click()
        val fade=device.wait(Until.findObject(By.text("快速淡入淡出")),5000)
        assertNotNull(fade);fade.click();device.wait(Until.findObject(By.desc("关闭主题与设置")),5000)!!.click()
        awaitJs(s,"window.miuingConfig.pageTurn==='fade'")
        scenarioTurnAndCapture(s,"fade-turn.png")
        s.recreate();awaitJs(s,"!!window.miuingConfig && window.miuingConfig.pageTurn==='fade'")
        assertTrue("Page turn preference was lost",instrument.targetContext.getSharedPreferences("reading",0).getString("pageTurn","")=="fade")
        device.openReaderThemes()
        device.wait(Until.findObject(By.desc("翻页样式")),5000)!!.click()
        device.wait(Until.findObject(By.text("卷页")),5000)!!.click();device.wait(Until.findObject(By.desc("关闭主题与设置")),5000)!!.click()
        awaitJs(s,"window.miuingConfig.pageTurn==='curl'")
        scenarioTurnAndCapture(s,"curl-turn.png")
        var oldOverlay:View?=null;var activity:MainActivity?=null
        s.onActivity {oldOverlay=overlay(it.window.decorView);activity=it}
        js(s,"document.body.dispatchEvent(new MouseEvent('click',{clientX:window.miuingWidth*.92,clientY:window.miuingHeight*.5,bubbles:true}))")
        Thread.sleep(120)
        instrument.runOnMainSync {assertEquals("Recreation test did not interrupt an active turn",View.VISIBLE,oldOverlay!!.visibility);activity!!.recreate()}
        val disposedBy=System.currentTimeMillis()+5000
        while(oldOverlay!!.visibility!=View.GONE && System.currentTimeMillis()<disposedBy) Thread.sleep(30)
        assertEquals("Recreation retained the old snapshot",View.GONE,oldOverlay!!.visibility)
        awaitJs(s,"!!window.miuingConfig && window.miuingProgress()>0")
        device.openReaderThemes()
        device.wait(Until.findObject(By.desc("翻页样式")),5000)!!.click()
        device.wait(Until.findObject(By.desc("连续滚动")),5000)!!.click();device.wait(Until.findObject(By.desc("关闭主题与设置")),5000)!!.click()
        awaitJs(s,"window.miuingConfig.scrolling")
        assertEquals("Scroll mode must not turn horizontally","true",js(s,"(()=>{const a=window.miuingProgress();window.miuingAdvance(1);return a===window.miuingProgress()})()"))
            }
        } finally {device.executeShellCommand(if(old=="null") "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $old")}
    }
    @Test fun disabledSystemMotionAdvancesWithoutSnapshot() {
        val old=device.executeShellCommand("settings get global animator_duration_scale").trim()
        try {
            device.executeShellCommand("settings put global animator_duration_scale 0")
            fixture {s,_->
                var enabled=true;val deadline=System.currentTimeMillis()+5000
                while(enabled && System.currentTimeMillis()<deadline) {s.onActivity {enabled=ValueAnimator.areAnimatorsEnabled()};if(enabled) Thread.sleep(100)}
                assertFalse("Test device did not disable system animations",enabled)
                tap(s,.92f)
                awaitJs(s,"window.miuingProgress()>0")
                s.onActivity {assertEquals("Disabled motion should bypass the snapshot",View.GONE,overlay(it.window.decorView)!!.visibility)}
                assertEquals("Disabled motion did not advance one page",1.0,js(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
            }
        } finally {device.executeShellCommand(if(old=="null") "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $old")}
    }
    private fun overlay(v:View):View? {
        if(v.javaClass.simpleName=="PageTurnOverlay") return v
        if(v is ViewGroup) repeat(v.childCount) {overlay(v.getChildAt(it))?.let {o->return o}}
        return null
    }
    private fun scenarioTurnAndCapture(s:ActivityScenario<MainActivity>,name:String) {
        js(s,"window.miuingJump(0)");Thread.sleep(200)
        var root:View?=null
        s.onActivity {root=it.window.decorView}
        // Dispatch a real reader click without UiAutomator's post-click idle wait,
        // so the temporary native overlay can be inspected before it completes.
        js(s,"document.body.dispatchEvent(new MouseEvent('click',{clientX:window.miuingWidth*.92,clientY:window.miuingHeight*.5,bubbles:true}))")
        var visible=false
        val until=System.currentTimeMillis()+1500
        while(!visible && System.currentTimeMillis()<until) {
            // ActivityScenario.onActivity waits for UI idle, which can wait until
            // the animation ends. Inspect on the main thread without that barrier.
            instrument.runOnMainSync {visible=overlay(root!!)!!.visibility==View.VISIBLE}
            if(!visible) Thread.sleep(10)
        }
        var details=""
        instrument.runOnMainSync {val o=overlay(root!!);details="motion=${ValueAnimator.areAnimatorsEnabled()}, overlay=${o?.visibility}/${o?.width}x${o?.height}"}
        assertTrue("Native page turn snapshot did not appear: $details",visible)
        Thread.sleep(if(name.startsWith("curl")) 600 else 250)
        device.takeScreenshot(File(instrument.targetContext.getExternalFilesDir(null),name))
        val end=System.currentTimeMillis()+5000
        while(visible && System.currentTimeMillis()<end) {
            instrument.runOnMainSync {visible=overlay(root!!)!!.visibility==View.VISIBLE}
            if(visible) Thread.sleep(30)
        }
        assertFalse("Animation did not release its snapshot",visible)
        assertEquals("Animation did not advance the live page",1.0,js(s,"document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.02)
    }
}
