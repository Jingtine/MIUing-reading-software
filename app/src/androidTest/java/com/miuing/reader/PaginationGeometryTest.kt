package com.miuing.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PaginationGeometryTest {
    @Test fun delayedIntrinsicImageRefreshesPagesAndRestoresTheNewerPassage() {
        val requested=CountDownLatch(1);val release=CountDownLatch(1)
        try {
            val html="<div style='height:452px;break-after:column'>第一页</div><img id='late' loading='lazy' style='display:block'><p id='destination' style='height:80px;margin:0;break-inside:avoid'>图片后面的目标段落，应当在加载后仍可见。</p>"
            RendererHarness(html,config(360.0),"document.getElementById('late').src='https://appassets.androidplatform.net/late.svg';",{uri->
                if(uri.path=="/late.svg") {
                    requested.countDown();check(release.await(15,TimeUnit.SECONDS))
                    WebResourceResponse("image/svg+xml","UTF-8",ByteArrayInputStream("<svg xmlns='http://www.w3.org/2000/svg' width='200' height='400'></svg>".toByteArray()))
                } else null
            }).use {r->
                assertTrue(requested.await(5,TimeUnit.SECONDS));assertEquals(2,metrics(r).getInt("total"))
                r.js("window.destinationOffset=new MIUingTextIndex(document.getElementById('miuing-content')).offsetOf(document.getElementById('destination').firstChild,0);window.miuingJumpAnchor(window.destinationOffset)")
                release.countDown();r.await {r.js("document.getElementById('late').complete")=="true"}
                r.await {metrics(r).getInt("total")==3}
                assertEquals("true",r.js("(()=>{const r=document.getElementById('destination').getBoundingClientRect();return r.left>=23 && r.left<30 && r.top>=20 && r.top<100})()"))
            }
        } finally {release.countDown()}
    }
    private fun config(width:Double)=JSONObject().put("width",width).put("height",500).put("margin",24).put("scrolling",false)
    private fun columnPages(count:Int)= (1..count).joinToString("") {"<p id='p$it' style='margin:0;height:452px;break-after:column;break-inside:avoid'>第${it}页 Physical column $it.</p>"}
    private fun metrics(r:RendererHarness)=JSONObject(org.json.JSONTokener(r.js("JSON.stringify(window.miuingPageMetrics())")).nextValue() as String)
    @Test fun fractionalSingleColumnHasNoPhantomPageAndPreservesText() {
        RendererHarness(columnPages(3),config(411.42857)).use {r->
            assertEquals(3,metrics(r).getInt("total"))
            r.js("window.miuingAdvance(1)")
            assertEquals(2,metrics(r).getInt("first"))
            r.js("window.miuingAdvance(1)")
            assertEquals(3,metrics(r).getInt("last"))
            assertEquals("true",r.js("document.getElementById('miuing-content').textContent.includes('第3页')"))
        }
    }
    @Test fun oddDoubleColumnFinishesWithoutRepeatingThePreviousColumn() {
        RendererHarness(columnPages(3),config(1200.0)).use {r->
            assertEquals(3,metrics(r).getInt("total"));assertEquals(2,metrics(r).getInt("last"))
            r.js("window.miuingAdvance(1)")
            val last=metrics(r);assertEquals(3,last.getInt("first"));assertEquals(3,last.getInt("last"))
            assertEquals(1.0,r.js("document.getElementById('miuing-scroll').scrollLeft/window.miuingWidth").toDouble(),.002)
            r.js("window.miuingAdvance(-1)");assertEquals(1,metrics(r).getInt("first"))
        }
    }
    @Test fun emptyAndIllustratedChaptersHaveNavigablePageCounts() {
        RendererHarness("",config(1200.0)).use {r->assertEquals(1,metrics(r).getInt("total"))}
        RendererHarness("<div style='height:452px;break-after:column'>第一页</div><img alt='' width='200' height='300' src='data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22200%22 height=%22300%22/%3E'>",config(1200.0)).use {r->
            assertEquals(2,metrics(r).getInt("total"))
        }
    }
}
