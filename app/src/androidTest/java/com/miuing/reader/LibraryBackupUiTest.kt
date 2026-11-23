package com.miuing.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.net.Uri
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class LibraryBackupUiTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrument)
    private fun choose(action: String) {
        val root=File(instrument.targetContext.cacheDir,"backup-ui-${System.nanoTime()}")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    MaterialTheme {
                        val store=remember {LibraryStore(activity,root)}
                        var busy by remember {mutableStateOf(false)}
                        val snack=remember {SnackbarHostState()}
                        Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                            LibraryBackupMenu(store,busy,snack) {busy=it}
                        }
                    }
                } }
                device.wait(Until.findObject(By.desc("书库备份")),5000)!!.click()
                assertNotNull(device.wait(Until.findObject(By.textContains("不含字体和阅读外观设置")),3000))
                device.findObject(By.text(action)).click()
                val deadline=System.currentTimeMillis()+10000
                while(System.currentTimeMillis()<deadline && !device.currentPackageName.orEmpty().endsWith("documentsui")) Thread.sleep(100)
                assertTrue("Action did not open Android document picker",device.currentPackageName.orEmpty().endsWith("documentsui"))
                device.pressBack()
                assertNotNull(device.wait(Until.findObject(By.desc("书库备份")),5000))
            }
        } finally {root.deleteRecursively()}
    }
    @Test fun exportUsesSystemCreateDocumentPicker()=choose("导出备份")
    @Test fun restoreUsesSystemOpenDocumentPicker()=choose("恢复备份")

    @Test fun systemPickerExportsAndRestoresAnActualPortableLibrary()=runBlocking {
        val root=File(instrument.targetContext.cacheDir,"backup-saf-${System.nanoTime()}").apply {mkdirs()}
        val name="miuing-backup-test-${System.nanoTime()}.zip"
        val a=LibraryStore(instrument.targetContext,File(root,"a"))
        val b=LibraryStore(instrument.targetContext,File(root,"b"))
        val source=File(root,"SAF测试.txt").apply {writeText("第一章\n系统文件备份与恢复。")}
        val book=a.import(Uri.fromFile(source));a.update(book.copy(position=.6f,notes=listOf(Note(0,"备份","保存的笔记"))))
        var displayed by mutableStateOf(a)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {scenario ->
                scenario.onActivity {activity -> activity.setContent {MaterialTheme {
                    var busy by remember {mutableStateOf(false)}
                    val snack=remember {SnackbarHostState()}
                    Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                        LibraryBackupMenu(displayed,busy,snack) {busy=it}
                    }
                }} }
                device.wait(Until.findObject(By.desc("书库备份")),5000)!!.click()
                device.wait(Until.findObject(By.text("导出备份")),5000)!!.click()
                val field=device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000)!!
                field.text=name
                // CreateDocument's default writable root is Downloads on the project AVDs.
                device.findObject(By.res("android","button1")).click()
                val ready=device.wait(Until.findObject(By.desc("书库备份").enabled(true)),15000)
                assertNotNull("SAF export did not complete",ready)
                val descriptor=instrument.uiAutomation.executeShellCommand("cat /sdcard/Download/$name")
                val bytes=android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {it.readBytes()}
                assertTrue("System picker did not save a ZIP",bytes.size>100 && bytes[0]==0x50.toByte())
                // A new store represents a different installation; restore through the actual picker.
                scenario.onActivity {displayed=b}
                instrument.waitForIdleSync()
                device.findObject(By.desc("书库备份")).click()
                device.wait(Until.findObject(By.text("恢复备份")),5000)!!.click()
                val saved=device.wait(Until.findObject(By.text(name)),10000)
                assertNotNull("Saved archive not visible in system picker",saved)
                saved!!.click()
                val deadline=System.currentTimeMillis()+15000
                while(System.currentTimeMillis()<deadline && b.books.isEmpty()) Thread.sleep(100)
                assertEquals(1,b.books.size);assertEquals(.6f,b.books.single().position)
                assertEquals("保存的笔记",b.books.single().notes.single().note)
                assertTrue(b.open(b.books.single()).chapters.isNotEmpty())
            }
        } finally {
            root.deleteRecursively()
            instrument.uiAutomation.executeShellCommand("rm -f /sdcard/Download/$name").close()
        }
    }
}
