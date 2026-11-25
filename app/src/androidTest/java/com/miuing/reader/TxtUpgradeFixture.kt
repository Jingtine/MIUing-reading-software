package com.miuing.reader

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.*
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Host installs the released APK before seed and the candidate APK before verify. */
class TxtUpgradeFixture {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root get() = File(context.cacheDir,"txt-upgrade-fixture")
    @Test fun seed() = runBlocking {
        check(!root.exists()) { "Previous fixture needs cleanup" }
        root.mkdirs()
        val store=LibraryStore(context,File(root,"library"))
        val text="第一章 开始\r"+(1..150).joinToString("\r") {"第${it}段，阅读测试正文，验证跨页之后的位置。"}+"\r第二章 继续\r第二段正文"
        val offset=text.indexOf("第二段正文")
        val source=File(root,"upgrade.txt").apply {writeText(text)}
        val imported=store.import(Uri.fromFile(source))
        val json=BookJson.encode(imported)
        assertFalse(json.has("txtLayoutVersion"))
        json.put("chapter",0).put("position",.9).put("anchor",offset)
        json.put("marks",JSONArray().put(JSONObject().put("chapter",0).put("position",.9).put("label","升级书签").put("anchor",offset)))
        json.put("notes",JSONArray().put(JSONObject().put("chapter",0).put("text","第二段正文").put("note","升级笔记").put("start",offset).put("end",offset+5)))
        val saved=BookJson.decode(json)
        store.update(saved)
        val publication=store.open(saved)
        assertEquals(1,publication.chapters.size)
        File(root,"expected.html").writeBytes(publication.chapters.single().path.toFile().readBytes())
        File(root,"expected.json").writeText(BookJson.encode(saved).toString())
        File(root,"old-backup.zip").outputStream().use {LibraryBackup(store).export(it)}
    }
    @Test fun verify() = runBlocking {
        val store=LibraryStore(context,File(root,"library"))
        val expected=JSONObject(File(root,"expected.json").readText())
        fun checkStore(target:LibraryStore) {
            val book=target.books.single()
            val json=BookJson.encode(book)
            assertEquals(1,json.getInt("txtLayoutVersion"))
            for(key in listOf("chapter","position","anchor","marks","notes"))
                assertEquals(key,expected.get(key).toString(),json.get(key).toString())
            val publication=target.open(book)
            assertEquals(1,publication.chapters.size)
            assertArrayEquals(File(root,"expected.html").readBytes(),publication.chapters.single().path.toFile().readBytes())
        }
        checkStore(store)
        val oldRestored=LibraryStore(context,File(root,"old-restored"))
        File(root,"old-backup.zip").inputStream().use {LibraryBackup(oldRestored).restore(it)}
        checkStore(oldRestored)
        val bytes=ByteArrayOutputStream().also {LibraryBackup(store).export(it)}.toByteArray()
        val newRestored=LibraryStore(context,File(root,"new-restored"))
        LibraryBackup(newRestored).restore(bytes.inputStream())
        checkStore(newRestored)
    }
    @Test fun verifyReader() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val device=UiDevice.getInstance(instrument)
        val store=LibraryStore(context,File(root,"library"))
        val book=store.books.single()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var decor:View
            scenario.onActivity { activity ->
                decor=activity.window.decorView
                activity.setContent {MaterialTheme {ReaderScreen(book,store,{_,_->},false,{})}}
            }
            fun js(code:String):String {
                var value="";val latch=CountDownLatch(1)
                instrument.runOnMainSync {
                    fun find(v:View):WebView? {
                        if(v is WebView && v.importantForAccessibility!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) return v
                        if(v is ViewGroup) repeat(v.childCount) {find(v.getChildAt(it))?.let {return it}}
                        return null
                    }
                    val web=find(decor)
                    if(web==null) latch.countDown() else web.evaluateJavascript(code) {value=it;latch.countDown()}
                }
                assertTrue(latch.await(10,TimeUnit.SECONDS));return value
            }
            fun visible():Boolean = js("(()=>{const m=document.querySelector('mark[data-miuing]');if(!m||m.textContent!=='第二段正文')return false;return Array.from(m.getClientRects()).some(r=>r.width>0&&r.height>0&&r.right>0&&r.left<innerWidth&&r.bottom>0&&r.top<innerHeight)})()") == "true"
            fun awaitVisible() {
                val deadline=System.currentTimeMillis()+20000
                while(System.currentTimeMillis()<deadline) {if(visible()) return;Thread.sleep(50)}
                fail("Saved highlight is not visible at the original passage")
            }
            awaitVisible()
            js("window.miuingJumpAnchor(0)")
            val deadline=System.currentTimeMillis()+10000
            while(visible() && System.currentTimeMillis()<deadline) Thread.sleep(50)
            assertFalse("Fixture must leave the target page before bookmark navigation",visible())
            device.readerAction("目录与书签").click()
            device.wait(Until.findObject(By.text("书签")),5000)!!.click()
            device.wait(Until.findObject(By.text("升级书签")),5000)!!.click()
            awaitVisible()
        }
    }
    @Test fun cleanup() { root.deleteRecursively() }
}
