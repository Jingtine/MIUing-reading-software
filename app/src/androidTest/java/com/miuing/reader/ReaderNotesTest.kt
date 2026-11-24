package com.miuing.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReaderNotesTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrument)

    @Test fun filterSearchExportAndActionsUseTheSameSelectedNotes() {
        val highlight = Note(0, "星光摘录", start = 10, end = 14)
        val annotation = Note(1, "月光摘录", "我的想法", 20, 24)
        var exported: List<Note>? = null
        var edited: Note? = null
        var navigated: Note? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { MaterialTheme {
                var notes by remember { mutableStateOf(listOf(highlight, annotation)) }
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                    ReaderNotes(notes, listOf("开篇", "结尾"), false, false,
                        onExport = { groups, _ -> exported = groups.flatMap { it.notes } },
                        onNavigate = { navigated = it }, onEdit = { edited = it }, onRemove = { notes = notes - it })
                }
            } } }
            assertNotNull(device.wait(Until.findObject(By.text("星光摘录")), 5000))
            device.findObject(By.text("有笔记")).click()
            assertNotNull(device.wait(Until.findObject(By.text("月光摘录")), 3000))
            assertNull(device.findObject(By.text("星光摘录")))
            device.takeScreenshot(File(instrument.targetContext.getExternalFilesDir(null), "notes-panel.png"))
            device.findObject(By.text("导出筛选结果")).click()
            device.wait(Until.findObject(By.text("Markdown")), 3000)!!.click()
            instrument.waitForIdleSync()
            assertEquals(listOf(annotation), exported)
            device.findObject(By.text("编辑")).click(); instrument.waitForIdleSync(); assertEquals(annotation, edited)
            device.findObject(By.text("前往原文")).click(); instrument.waitForIdleSync(); assertEquals(annotation, navigated)
            device.findObject(By.text("全部")).click()
            device.findObject(By.clazz("android.widget.EditText")).text = "开篇"
            assertNotNull(device.wait(Until.findObject(By.text("星光摘录")), 3000))
            assertNull(device.findObject(By.text("月光摘录")))
            device.findObject(By.text("移除")).click()
            assertNotNull(device.wait(Until.findObject(By.text("没有符合条件的高亮标记或笔记")), 3000))
            device.findObject(By.text("导出筛选结果")).click()
            assertNull(device.wait(Until.findObject(By.text("Markdown")), 1000))
        }
    }
}
