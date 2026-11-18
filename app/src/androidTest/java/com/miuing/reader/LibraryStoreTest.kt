package com.miuing.reader

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryStoreTest {
    @Test fun largeCoverIsDecodedAtThumbnailSize() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"large-cover.png")
        val image=android.graphics.Bitmap.createBitmap(2048,2048,android.graphics.Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            val decoded=CoverDecoder.decode(file.path)!!
            assertTrue("Cover bitmap can exhaust memory",decoded.width<=512 && decoded.height<=512)
            decoded.recycle()
        } finally { image.recycle();file.delete() }
    }
    @Test fun importedCopySurvivesOriginalRemovalAndProgressSurvivesReload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = File(context.cacheDir, "test-${System.nanoTime()}")
        val original = File(context.cacheDir, "sample-${System.nanoTime()}.txt")
        original.writeText("第一章\n这是测试正文。")
        try {
            val store = LibraryStore(context, scope)
            val book = store.import(Uri.fromFile(original))
            assertEquals(book.id, store.import(Uri.fromFile(original)).id)
            assertEquals(1, store.books.size)
            original.delete()
            assertTrue(File(book.source).exists())
            store.update(book.copy(position = 0.4f))
            // Simulate an interrupted write on an Android AtomicFile implementation
            // that keeps its last good content in the .bak sibling.
            File(scope,"index.json").copyTo(File(scope,"index.json.bak"),overwrite=true)
            File(scope,"index.json").writeText("{broken")
            val reloaded = LibraryStore(context, scope)
            assertEquals(0.4f, reloaded.books.single().position)
            assertTrue(reloaded.open(reloaded.books.single()).chapters.isNotEmpty())
            reloaded.delete(book.id)
            assertTrue(LibraryStore(context, scope).books.isEmpty())
        } finally { original.delete(); scope.deleteRecursively() }
    }
}
