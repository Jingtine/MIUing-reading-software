package com.miuing.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NotesExportUiTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrument)
    private val filenameSelector = By.res("android", "title").clazz("android.widget.EditText")

    private fun filenameField(): UiObject2 = device.wait(Until.findObject(filenameSelector), 5000)
        ?: run {
            device.pressBack() // Dismiss the landscape IME; never edit its extracted/search field.
            device.wait(Until.findObject(filenameSelector), 5000) ?: error("System filename field unavailable")
        }

    private fun setNotes(h: BookReaderHarness, notes: List<Note>) {
        h.scenario.onActivity { activity ->
            val store = ViewModelProvider(activity)[LibrarySession::class.java].store
            store.update(store.current(h.book.id).copy(notes = notes))
        }
        instrument.waitForIdleSync()
    }

    private fun export(format: NotesFormat, rotate: Boolean) = BookReaderHarness(counts = listOf(1, 1)).use { h ->
        val name = "miuing-notes-test-${System.nanoTime()}.${format.extension}"
        var previousActivity: MainActivity? = null
        h.scenario.onActivity { previousActivity = it }
        setNotes(h, listOf(Note(0, "不要导出的高亮", start = 0, end = 5), Note(1, "月光 *摘录* 😀", "笔记\n第二行", 0, 4)))
        try {
            device.readerAction("高亮标记和笔记").click()
            device.wait(Until.findObject(By.text("有笔记")), 5000)!!.click()
            assertNotNull(device.wait(Until.findObject(By.text("1 / 2 条")), 3000))
            assertNotNull("Selected excerpt must be visible on the phone without manually expanding the sheet",
                device.wait(Until.findObject(By.text("月光 *摘录* 😀")), 3000))
            device.takeScreenshot(File(instrument.targetContext.getExternalFilesDir(null), "notes-reader.png"))
            device.wait(Until.findObject(By.text("导出筛选结果")), 5000)!!.click()
            device.wait(Until.findObject(By.text(format.label)), 3000)!!.click()
            var field = filenameField()
            if (rotate) {
                // ActivityScenario.recreate waits for RESUMED and cannot run under another Activity.
                // A real configuration change recreates the stopped reader when the picker returns.
                device.setOrientationLeft()
                device.waitForIdle(2000)
                field = filenameField()
            }
            setNotes(h, listOf(Note(0, "后来改动的内容")))
            field.text = name
            device.waitForIdle(1000)
            assertEquals("Filename must survive the picker configuration change", name,
                filenameField().text)
            val save = device.wait(Until.findObject(By.res("android", "button1").enabled(true)), 5000)
                ?: run {
                    // Android 8's landscape IME can temporarily cover the picker buttons.
                    device.pressBack()
                    device.wait(Until.findObject(By.res("android", "button1").enabled(true)), 5000)
                }
            assertNotNull("System picker save button unavailable", save)
            save!!.click()
            assertNotNull(device.wait(Until.findObject(By.text("已导出 1 条高亮标记和笔记")), 15000))
            if (rotate) h.scenario.onActivity { assertNotSame("Rotation did not recreate the reader", previousActivity, it) }
            val descriptor = instrument.uiAutomation.executeShellCommand("cat /sdcard/Download/$name")
            val text = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8) }
            val expectedTitle = if (format == NotesFormat.Markdown) h.book.title.replace("-", "\\-") else h.book.title
            assertTrue("Export missing book title: $text", text.contains(expectedTitle))
            assertTrue(text.contains("笔记")); assertTrue(text.contains("第二行")); assertTrue(text.contains("😀"))
            assertFalse(text.contains("不要导出")); assertFalse(text.contains("后来改动"))
            if (format == NotesFormat.Markdown) assertTrue(text.contains("> 月光 \\*摘录\\* 😀"))
            else assertTrue(text.contains("摘录：\n月光 *摘录* 😀"))
            device.takeScreenshot(File(instrument.targetContext.getExternalFilesDir(null), "notes-export-${format.extension}.png"))
        } finally {
            device.setOrientationNatural()
            instrument.uiAutomation.executeShellCommand("rm -f /sdcard/Download/$name").close()
        }
    }

    @Test fun txtPickerRetainsFilteredSnapshotAcrossRecreation() { export(NotesFormat.Text, true) }
    @Test fun markdownPickerExportsReadableEscapedNotes() { export(NotesFormat.Markdown, false) }

    @Test fun cancelPickerAllowsAnotherExportWithoutChangingNotes() = BookReaderHarness(counts = listOf(1)).use { h ->
        val notes = listOf(Note(0, "保留摘录", "保留笔记", 0, 4))
        setNotes(h, notes)
        device.readerAction("高亮标记和笔记").click()
        device.wait(Until.findObject(By.text("导出筛选结果")), 5000)!!.click()
        device.wait(Until.findObject(By.text("TXT")), 3000)!!.click()
        assertNotNull(filenameField())
        device.pressBack()
        if (device.currentPackageName.orEmpty().endsWith("documentsui")) device.pressBack()
        device.readerAction("高亮标记和笔记").click()
        assertNotNull(device.wait(Until.findObject(By.text("导出筛选结果").enabled(true)), 5000))
        assertEquals(notes, h.saved().notes)
    }

    @Test fun lostProcessSnapshotIsReportedAndInvalidDestinationFailsCleanly() = runBlocking {
        val app = instrument.targetContext.applicationContext as Application
        lateinit var restored: NoteExportSession
        instrument.runOnMainSync { restored = NoteExportSession(app, SavedStateHandle(mapOf("pending" to true))) }
        assertTrue(withTimeout(3000) { restored.notices.first() }.contains("已中断"))
        instrument.runOnMainSync {
            assertFalse(restored.busy)
            assertNotNull(restored.begin(NotesDocument("书", "", listOf(NoteChapter(0, "章", listOf(Note(0, "摘录"))))), NotesFormat.Text))
            restored.complete(Uri.parse("content://com.miuing.missing-provider/test.txt"))
        }
        assertTrue(withTimeout(10000) { restored.notices.first() }.contains("导出失败"))
        instrument.runOnMainSync { assertFalse(restored.busy) }
        assertTrue(app.cacheDir.listFiles().orEmpty().none { it.name.startsWith("document-export-") })
    }
}
