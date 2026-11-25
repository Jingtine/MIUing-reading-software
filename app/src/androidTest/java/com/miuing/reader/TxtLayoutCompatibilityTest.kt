package com.miuing.reader

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class TxtLayoutCompatibilityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val text = "第一章 开始\r第一段正文\r第二章 继续\r第二段正文"

    private fun fixture(block: suspend (File, LibraryStore) -> Unit) = runBlocking {
        val root = File(context.cacheDir, "txt-layout-${System.nanoTime()}").apply { mkdirs() }
        try { block(root, LibraryStore(context, File(root, "library"))) }
        finally { root.deleteRecursively() }
    }

    @Test fun unknownLayoutIsRejectedBeforeLoadingLocations() {
        for(format in listOf("TXT","txt")) {
            val record=BookJson.encode(Book("id","title","author",format,"source.txt"))
                .put("txtLayoutVersion",99)
            assertTrue(runCatching {BookJson.decode(record)}.isFailure)
        }
    }

    @Test fun oldRecordRetainsChapterBytesAndLocationsAfterReopenAndBackup() = fixture { root, store ->
        val source = File(root, "old.txt").apply { writeText(text) }
        val imported = store.import(Uri.fromFile(source))
        // Simulate the schema saved by 0.7.8 and earlier, without a layout version.
        val record = BookJson.encode(imported.copy(chapter=0, position=.6f, anchor=20,
            marks=listOf(Mark(0,.6f,"原书签",20)), notes=listOf(Note(0,"第二段正文","原笔记",20,25))))
        record.remove("txtLayoutVersion")
        val old = BookJson.decode(record)
        store.update(old)
        val expected = "<!doctype html><html><head><meta charset='UTF-8'></head><body>" +
            "<div style='white-space:pre-wrap'>$text\n</div></body></html>"
        fun verify(target: LibraryStore) {
            val saved = target.books.single()
            val publication = target.open(saved)
            assertEquals(1, publication.chapters.size)
            assertEquals(expected, publication.chapters.single().path.toFile().readText())
            assertEquals(old.chapter, saved.chapter)
            assertEquals(old.anchor, saved.anchor)
            assertEquals(old.position, saved.position)
            assertEquals(old.marks, saved.marks)
            assertEquals(old.notes, saved.notes)
        }
        verify(store)
        verify(LibraryStore(context, store.directory))
        // Importing identical bytes must not silently opt an existing book into a new layout.
        assertEquals(old, store.import(Uri.fromFile(source)))
        val archive = ByteArrayOutputStream().also { LibraryBackup(store).export(it) }.toByteArray()
        store.directory.deleteRecursively()
        val restored = LibraryStore(context, File(root,"restored"))
        LibraryBackup(restored).restore(archive.inputStream())
        verify(restored)
    }

    @Test fun newImportRetainsCorrectedChaptersAfterReopenAndBackup() = fixture { root, store ->
        val source = File(root, "new.txt").apply { writeText(text) }
        val imported = store.import(Uri.fromFile(source))
        store.update(imported.copy(chapter=1, anchor=7, position=.4f,
            marks=listOf(Mark(1,.4f,"新书签",7)), notes=listOf(Note(1,"第二段正文","新笔记",7,12))))
        val saved = store.books.single()
        fun verify(target: LibraryStore) {
            val book = target.books.single()
            val publication = target.open(book)
            assertEquals(2, publication.chapters.size)
            assertEquals("第二章 继续", publication.chapters[1].title)
            assertTrue(publication.chapters[1].path.toFile().readText().contains("第二章 继续\n第二段正文\n"))
            assertEquals(saved.copy(source=book.source, cover=book.cover),book)
        }
        verify(store)
        verify(LibraryStore(context,store.directory))
        val archive = ByteArrayOutputStream().also { LibraryBackup(store).export(it) }.toByteArray()
        store.directory.deleteRecursively()
        val restored = LibraryStore(context,File(root,"restored"))
        LibraryBackup(restored).restore(archive.inputStream())
        verify(restored)
    }
}
