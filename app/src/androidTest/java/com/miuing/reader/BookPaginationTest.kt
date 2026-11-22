package com.miuing.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import android.os.SystemClock
import android.view.MotionEvent

@RunWith(AndroidJUnit4::class)
class BookPaginationTest {
    @Test fun systemTypographyChangesUseSeparateCacheIdentity() {
        BookReaderHarness().use {r->
            r.await {r.footer()!=null}
            val key=r.js("window.miuingConfig.paginationKey");val before=r.saved()
            val old=r.device.executeShellCommand("settings get system font_scale").trim()
            val next=if(old.toFloatOrNull()==1.1f) "1.2" else "1.1"
            try {
                r.device.executeShellCommand("settings put system font_scale $next")
                r.await {r.js("!!window.miuingConfig && window.miuingConfig.paginationKey!==$key")=="true"}
                r.await {r.footer()!=null};assertEquals(before.anchor,r.saved().anchor)
            } finally {
                r.device.executeShellCommand(if(old=="null") "settings delete system font_scale" else "settings put system font_scale $old")
                r.device.waitForIdle()
            }
        }
    }
    @Test fun menuKeepsChapterScopeWhenWholeBookFinishesWhileOpen() {
        BookReaderHarness(List(8) {3},holdCounting=true).use {r->
            r.device.readerAction("阅读菜单").click()
            val scope=By.textStartsWith("第 1 / 8 章 · 本章 ")
            assertNotNull("Cold menu should state its chapter scope",r.device.wait(Until.findObject(scope),5000))
            r.releaseCounting()
            r.await {r.cachedCounts().all {it>0}}
            assertNotNull("Background completion changed the open slider's meaning",r.device.findObject(scope))
            val slider=r.device.findObject(By.desc("阅读进度"))!!.visibleBounds
            r.device.click(slider.left+slider.width()*3/4,slider.centerY())
            r.await {r.saved().anchor>0}
            assertEquals("Chapter preview unexpectedly became a whole-book jump",0,r.saved().chapter)
        }
    }
    private fun label(first:Int,last:Int,total:Int)=if(first==last) "全书第 $first 页，共 $total 页" else "全书第 $first 至 $last 页，共 $total 页"
    private fun columns(r:BookReaderHarness)=r.js("window.miuingPageMetrics() && window.miuingPageMetrics().columns").toIntOrNull() ?: 0
    @Test fun completedPaginationIsWarmEvenWhenClosedImmediately() {
        BookReaderHarness().use {r->
            val columns=columns(r)
            r.await {r.footer()==label(1,columns,9)}
            r.device.takeScreenshot(java.io.File(r.instrument.targetContext.getExternalFilesDir(null),"whole-book-pages.png"))
            r.reopen();r.await {r.footer()==label(1,columns,9)}
            assertEquals("Warm reopen unnecessarily launched background WebViews",0,(r.find {it is ReadingTurnHost} as ReadingTurnHost).measurementStarts)
        }
    }
    @Test fun wholeBookCountsRealChaptersWithoutMovingSourceAndCachesReopen() {
        BookReaderHarness().use {r->
            val before=r.saved()
            val columns=columns(r)
            r.await {r.footer()==label(1,columns,9)}
            assertEquals(before.chapter,r.saved().chapter);assertEquals(before.position,r.saved().position);assertEquals(before.anchor,r.saved().anchor)
            r.js("window.miuingAdvance(1)");r.await {r.footer()==label(1+columns,minOf(3,2*columns),9)}
            r.js("MIUing.chapter(1)")
            r.await {r.footer()==label(4,3+columns,9)}
            assertEquals(1,r.saved().chapter)
            r.reopen();r.await {r.footer()==label(4,3+columns,9)}
        }
    }
    @Test fun globalSliderOnlyNavigatesOnReleaseAndReturnsToSource() {
        BookReaderHarness().use {r->
            val columns=columns(r);r.await {r.footer()==label(1,columns,9)}
            val before=r.saved();r.device.readerAction("阅读菜单").click()
            val slider=r.device.wait(Until.findObject(By.desc("阅读进度")),5000)!!.visibleBounds
            val down=SystemClock.uptimeMillis()
            fun touch(action:Int,x:Int) {val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x.toFloat(),slider.centerY().toFloat(),0);try {r.instrument.sendPointerSync(e)} finally {e.recycle()}}
            try {
                touch(MotionEvent.ACTION_DOWN,slider.left+slider.width()/8)
                repeat(10) {step->
                    SystemClock.sleep(20)
                    touch(MotionEvent.ACTION_MOVE,slider.left+slider.width()*(step+3)/16)
                }
                assertEquals(before.chapter,r.saved().chapter);assertEquals(before.anchor,r.saved().anchor)
                touch(MotionEvent.ACTION_UP,slider.left+slider.width()*3/4)
            } finally {touch(MotionEvent.ACTION_CANCEL,slider.left+slider.width()*3/4)}
            r.await {r.saved().chapter==2}
            val first=if(columns==1) 7 else 6
            r.await {r.footer()==label(first,7,9)}
            r.device.readerAction("返回跳转前位置").click();r.await {r.footer()==label(1,columns,9)}
            assertEquals(before.anchor,r.saved().anchor)
        }
    }
    @Test fun fontAndViewportChangesInvalidateLayoutWhileKeepingPassage() {
        BookReaderHarness().use {r->
            r.await {r.footer()!=null}
            val key=r.js("window.miuingConfig.paginationKey");val before=r.saved()
            r.device.openReaderThemes();r.device.wait(Until.findObject(By.desc("增大字号")),5000)!!.click()
            r.device.wait(Until.findObject(By.desc("关闭主题与设置")),5000)!!.click()
            r.await {r.js("window.miuingConfig.paginationKey")!=key};r.await {r.footer()!=null}
            assertEquals(before.chapter,r.saved().chapter);assertEquals(before.anchor,r.saved().anchor)
            val width=r.js("window.miuingWidth");val nextKey=r.js("window.miuingConfig.paginationKey")
            try {
                r.device.setOrientationLeft();r.await {r.js("window.miuingWidth")!=width && r.js("window.miuingConfig.paginationKey")!=nextKey}
                r.await {r.footer()==label(1,columns(r),9)}
                assertEquals(before.anchor,r.saved().anchor)
            } finally {r.device.setOrientationNatural()}
        }
    }
    @Test fun countingYieldsToSwipesAndStopsWhileBackgrounded() {
        BookReaderHarness(List(12) {3}).use {r->
            val host=r.find {it is ReadingTurnHost} as ReadingTurnHost
            r.await {host.measurementStarts>0}
            val web=r.find {it is android.webkit.WebView && it.importantForAccessibility!=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}!!
            var x=0;var y=0;var width=0
            r.instrument.runOnMainSync {val p=IntArray(2);web.getLocationOnScreen(p);x=p[0];y=p[1]+web.height/2;width=web.width}
            r.device.swipe(x+width*4/5,y,x+width/5,y,15)
            r.await {r.js("window.miuingPageMetrics().first").toInt()>1}
            val before=r.saved()
            r.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            val started=host.measurementStarts;Thread.sleep(500);assertEquals(started,host.measurementStarts)
            r.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            r.await {r.footer()?.endsWith("共 36 页")==true}
            assertEquals(before.chapter,r.saved().chapter);assertEquals(before.anchor,r.saved().anchor)
            assertTrue(r.maximumWebViews<=2)
        }
    }
}
