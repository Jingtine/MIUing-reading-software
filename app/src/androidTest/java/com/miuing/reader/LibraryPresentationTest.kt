package com.miuing.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LibraryPresentationTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val context=instrument.targetContext
    private val device=UiDevice.getInstance(instrument)
    private fun image(path:File,width:Int,height:Int) {
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.CYAN);path.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun landscapeAndTallCoversKeepTheirEntireOriginalAspectRatio() {
        val root=File(context.cacheDir,"cover-presentation-${UUID.randomUUID()}").apply {mkdirs()}
        try {
            val wide=File(root,"wide.png");image(wide,240,120)
            val tall=File(root,"tall.png");image(tall,90,270)
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                scenario.onActivity {it.setContent {MaterialTheme {Row {
                    BookCover(Book("wide","wide-cover","author","EPUB","",wide.path),Modifier.width(120.dp).height(180.dp))
                    BookCover(Book("tall","tall-cover","author","EPUB","",tall.path),Modifier.width(120.dp).height(180.dp))
                }}}}
                val w=device.wait(Until.findObject(By.desc("wide-cover")),5000)!!.visibleBounds
                val t=device.wait(Until.findObject(By.desc("tall-cover")),5000)!!.visibleBounds
                assertEquals("Landscape cover was cropped",2.0,w.width().toDouble()/w.height(),.05)
                assertEquals("Tall cover was cropped",1.0/3,t.width().toDouble()/t.height(),.03)
            }
        } finally {root.deleteRecursively()}
    }
    @Test fun returningFromReadingKeepsLibraryListAndScrolledBook()=runBlocking<Unit> {
        val root=File(context.cacheDir,"library-presentation-${UUID.randomUUID()}").apply {mkdirs()}
        val ids=mutableListOf<String>();val prefix="书库测试-${UUID.randomUUID().toString().take(5)}"
        try {
            val store=LibraryStore(context)
            repeat(10) {index->
                val suffix=if(index==3) "一本用于检查两行省略和作者信息的长书名" else ""
                val file=File(root,"$prefix-$index$suffix.txt");file.writeText("第${index}本图书。"+"测试正文。".repeat(100))
                val book=store.import(Uri.fromFile(file));ids.add(book.id)
                if(index<2) {
                    val cover=File(root,"cover-$index.png");image(cover,if(index==0) 240 else 90,if(index==0) 120 else 270)
                    store.update(book.copy(cover=cover.path,author="封面比例测试作者"))
                }
            }
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                device.wait(Until.findObject(By.text("书库")),5000)!!.click()
                device.wait(Until.findObject(By.desc("排序方式")),5000)!!.click()
                device.wait(Until.findObject(By.text("书名")),5000)!!.click()
                device.wait(Until.findObject(By.descStartsWith("切换显示方式")),5000)!!.click()
                val title="$prefix-9"
                // API26's UiScrollable can report the end before Compose's
                // delayed scroll event. Drive real swipes and inspect each frame.
                var bookNode:UiObject2?=null
                for(step in 0 until 12) {
                    bookNode=device.findObjects(By.text(title)).firstOrNull {it.visibleBounds.height()>24}
                    if(bookNode!=null) break
                    val bounds=device.wait(Until.findObject(By.scrollable(true)),5000)!!.visibleBounds
                    device.swipe(bounds.centerX(),bounds.bottom-bounds.height()/5,bounds.centerX(),bounds.top+bounds.height()/5,25)
                    device.waitForIdle()
                }
                if(bookNode==null) {
                    device.dumpWindowHierarchy(File(context.getExternalFilesDir(null),"library-state-missing.xml"))
                    device.takeScreenshot(File(context.getExternalFilesDir(null),"library-state-missing.png"))
                    fail("Missing book after bounded swipes, mode=${device.findObject(By.descStartsWith("切换显示方式"))?.contentDescription}")
                }
                // The first tap can stop an ongoing fling. Verify delivery
                // before asserting the return flow, as the search UI tests do.
                var opened=false
                for(attempt in 0 until 4) {
                    device.findObjects(By.text(title)).firstOrNull {it.visibleBounds.height()>24}?.click()
                    opened=device.wait(Until.hasObject(By.desc("返回书库")),700)
                    if(opened) break
                }
                assertTrue("Book tap was not delivered",opened)
                var openedId:String?=null
                instrument.runOnMainSync {openedId=LibraryStore(context).books.maxByOrNull {it.opened}?.id}
                assertEquals("Tap opened a different book",ids.last(),openedId)
                device.findObject(By.desc("返回书库"))!!.click()
                assertNotNull("Reading exit lost the Library tab",device.wait(Until.findObject(By.text("${store.books.size} 本图书")),5000))
                assertNotNull("Reading exit lost list mode",device.findObject(By.desc("切换显示方式：当前为列表")))
                assertNotNull("Reading exit lost the scrolled book",device.findObject(By.text(title)))
                scenario.recreate()
                assertNotNull(device.wait(Until.findObject(By.desc("切换显示方式：当前为列表")),5000))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"library-list.png"))
                device.findObject(By.descStartsWith("切换显示方式"))!!.click()
                assertNotNull(device.wait(Until.findObject(By.desc("切换显示方式：当前为网格")),5000));device.waitForIdle()
                device.takeScreenshot(File(context.getExternalFilesDir(null),"library-grid.png"))
            }
        } finally {
            instrument.runOnMainSync {val store=LibraryStore(context);ids.forEach {store.delete(it)}}
            root.deleteRecursively()
        }
    }
}
