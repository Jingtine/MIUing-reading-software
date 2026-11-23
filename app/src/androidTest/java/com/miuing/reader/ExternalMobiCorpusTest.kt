package com.miuing.reader

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Optional local corpus; third-party ebook bytes are deliberately not committed or shipped. */
@RunWith(AndroidJUnit4::class)
class ExternalMobiCorpusTest {
    @Test fun realKf8HuffPalmDocAndCp1252Samples()=runBlocking<Unit> {
        val corpus=InstrumentationRegistry.getArguments().getString("mobiCorpusDirectory")
        assumeTrue("Run with -e mobiCorpusDirectory for the external sample matrix",corpus!=null)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,"mobi-corpus-${System.nanoTime()}").apply {mkdirs()}
        try {
            val store=LibraryStore(context,File(root,"library"))
            for((name,ext) in listOf("sample-obfuscated-fonts.mobi" to "azw3","sample-unicode-huffdic.mobi" to "mobi","sample-unicode-uncompressed.mobi" to "mobi","sample-cp1252.mobi" to "azw","sample-ncx.mobi" to "mobi")) {
                val input=File(corpus!!,name);assertTrue("Missing sample $name",input.isFile)
                val source=File(root,name.substringBeforeLast('.')+".$ext");input.copyTo(source)
                val book=try {store.import(Uri.fromFile(source))} catch(e:Exception) {throw AssertionError("Cannot import corpus sample $name",e)}
                val p=store.open(book)
                assertTrue("No chapters in $name",p.chapters.isNotEmpty());assertTrue(p.navigation.isNotEmpty())
                val text=p.chapters.joinToString("") {it.path.toFile().readText()}
                assertTrue("No real text in $name",text.length>200)
                assertFalse("Temporary blob reference in $name",text.contains("blob:"))
                assertEquals(ext.uppercase(),book.format)
            }
            assertEquals(5,store.books.size)
        } finally {root.deleteRecursively()}
    }
}
