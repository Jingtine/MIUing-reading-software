package com.miuing.reader

import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EpubCorpusTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private fun corpus():Pair<File,JSONObject> {
        val path=InstrumentationRegistry.getArguments().getString("epubCorpusDirectory")
        assumeTrue("Optional pinned EPUB corpus",path!=null)
        val root=File(path!!)
        val manifest=JSONObject(File(root,"manifest.json").readText())
        assertEquals("7651e2002b631e6577fadf7e9e0692fa6efb8746",manifest.getString("revision"))
        return root to manifest
    }
    private fun hash(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input->val buffer=ByteArray(65536);while(true) {val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n)}}
        return digest.digest().joinToString("") {"%02x".format(it)}
    }
    @Test fun pinnedPublicBooksImportAndKeepMetadataNavigationAndOriginalFiles()=runBlocking {
        val (corpus,manifest)=corpus()
        val root=File(instrument.targetContext.cacheDir,"epub-corpus-${System.nanoTime()}")
        try {
            val store=LibraryStore(instrument.targetContext,root)
            val samples=manifest.getJSONArray("books")
            assertEquals(9,samples.length())
            repeat(samples.length()) {i->
                val sample=samples.getJSONObject(i);val file=File(corpus,sample.getString("file"))
                assertEquals(sample.getString("sha256"),hash(file))
                val started=android.os.SystemClock.elapsedRealtime()
                val book=store.import(Uri.fromFile(file));val publication=store.open(book)
                assertTrue(book.title.isNotBlank());assertTrue(publication.chapters.isNotEmpty())
                assertTrue(publication.chapters.all {it.path.toFile().isFile})
                assertTrue(publication.navigation.isNotEmpty())
                assertTrue("No navigable entries: ${sample.getString("name")}",publication.navigation.any {it.chapter in publication.chapters.indices})
                // EPUB navigation also contains non-clickable grouping headings (chapter=-1).
                assertTrue(publication.navigation.all {it.chapter==-1 || it.chapter in publication.chapters.indices})
                assertEquals(sample.getString("sha256"),hash(File(book.source)))
                Log.i("EpubCorpus","${sample.getString("name")}: chapters=${publication.chapters.size},toc=${publication.navigation.size},importAndReopenMs=${android.os.SystemClock.elapsedRealtime()-started}")
            }
            assertEquals(9,store.books.size)
        } finally {root.deleteRecursively()}
    }
    @Test fun sixDecodedFontFilesEqualIndependentPublishedPlainCounterparts()=runBlocking {
        val (corpus,manifest)=corpus();val pairs=manifest.getJSONArray("fontPairs")
        assertEquals(6,pairs.length())
        val root=File(instrument.targetContext.cacheDir,"epub-font-pairs-${System.nanoTime()}")
        try {
            val store=LibraryStore(instrument.targetContext,root)
            val imported=mutableMapOf<String,Book>()
            repeat(pairs.length()) {i->
                val pair=pairs.getJSONObject(i);val name=pair.getString("book")
                val book=imported[name]?:store.import(Uri.fromFile(File(corpus,"$name.epub"))).also {imported[name]=it}
                val font=File(File(book.source).parentFile,"content/${pair.getString("path")}")
                assertEquals(pair.getLong("bytes"),font.length())
                assertEquals("Decoded font differs: ${pair.getString("path")}",pair.getString("sha256"),hash(font))
            }
        } finally {root.deleteRecursively()}
    }
    @Test fun actualW3cOtfAndWoffFontsLoadInReader() {
        val (corpus,_)=corpus()
        for(name in listOf("wasteland-otf-obf","wasteland-woff-obf")) {
            BookReaderHarness(counts=listOf(1),sourceFactory={root,_->File(corpus,"$name.epub").copyTo(File(root,"$name.epub"),true)}).use {h->
                assertEquals("Publisher font was overridden before rendering","true",h.js("window.miuingConfig.fontFamily==='book' && getComputedStyle(document.body).fontFamily.includes('OldStandard')"))
                h.await {h.js("!!document.fonts && document.fonts.size===3")=="true"}
                h.js("window.corpusFonts='pending';Promise.all(Array.from(document.fonts).map(f=>document.fonts.load(f.style+' '+f.weight+' 16px '+f.family))).then(x=>window.corpusFonts=x.every(a=>a.length>0 && a.every(f=>f.status==='loaded'))?'loaded':'fallback').catch(e=>window.corpusFonts='error')")
                h.await {h.js("window.corpusFonts!=='pending'")=="true"}
                assertEquals("Actual $name font failed","\"loaded\"",h.js("window.corpusFonts"))
                h.await {h.js("window.miuingPageMetrics()!==null")=="true"}
                val anchor=h.saved().anchor
                val shadow="document.getElementById('miuing-footnote') && document.getElementById('miuing-footnote').shadowRoot"
                h.js("document.querySelector('a.noteref').click()")
                h.await {h.js("!!($shadow) && ($shadow).querySelector('.content').textContent.includes('Ezekiel 2:1')")=="true"}
                assertEquals("true",h.js("getComputedStyle(($shadow).querySelector('.content')).fontFamily===getComputedStyle(document.body).fontFamily"))
                h.js("($shadow).querySelector('[aria-label=关闭脚注]').click()")
                h.await {h.js("document.getElementById('miuing-footnote')===null")=="true"}
                assertEquals("Real-book note changed the original passage",anchor,h.saved().anchor)
                val paint=CountDownLatch(1)
                instrument.runOnMainSync {
                    val web=h.find {it is WebView && it.importantForAccessibility!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS} as WebView
                    web.afterReadingPaint {h.root.postOnAnimation {h.root.postOnAnimation {paint.countDown()}}}
                }
                assertTrue(paint.await(10,TimeUnit.SECONDS))
                h.device.takeScreenshot(File(instrument.targetContext.getExternalFilesDir(null),"$name-reader.png"))
            }
        }
    }

    @Test fun wastelandBacklinkJumpsWithinTheSameChapterAndReturnsToItsSource() {
        val (corpus, _) = corpus()
        BookReaderHarness(counts = listOf(1), sourceFactory = { root, _ ->
            File(corpus, "wasteland-woff.epub").copyTo(File(root, "wasteland-links.epub"), overwrite = true)
        }).use { h ->
            h.js("""(() => {
                const link = document.querySelector('a[href="#ln20"]');
                const index = new MIUingTextIndex(document.getElementById('miuing-content'));
                window.miuingJumpAnchor(index.offsetOf(link.firstChild, 0));
            })()""")
            h.await { h.saved().anchor > 1000 }
            val source = h.saved()
            h.js("document.querySelector('a[href=\"#ln20\"]').click()")
            val back = h.device.wait(Until.findObject(By.desc("返回跳转前位置")), 6000)
            assertNotNull("The real fragment backlink did not create a reader jump", back)
            h.await { h.saved().chapter == source.chapter && h.saved().anchor in 0 until source.anchor }
            assertEquals("true", h.js("""(() => {
                const target = document.getElementById('ln20').getBoundingClientRect();
                return target.right > 0 && target.left < window.miuingWidth &&
                    target.bottom > 0 && target.top < window.miuingHeight;
            })()"""))
            back!!.click()
            h.await { h.saved().chapter == source.chapter && h.saved().anchor == source.anchor }
        }
    }

    @Test fun xmlLanguageInThePublishedXhtmlAppliesToReadingText() {
        val (corpus, _) = corpus()
        BookReaderHarness(counts = listOf(1), requireTextAnchor = false, sourceFactory = { root, _ ->
            File(corpus, "internallinks.epub").copyTo(File(root, "language-sample.epub"), overwrite = true)
        }).use { h ->
            val publication = LibraryStore(instrument.targetContext).open(h.book)
            val entry = publication.navigation.first {
                it.fragment.isEmpty() && publication.chapters.getOrNull(it.chapter)?.path?.fileName?.toString() == "0001.xhtml"
            }
            h.device.readerAction("目录与书签").click()
            h.device.wait(Until.findObject(By.text(entry.title)), 6000)!!.click()
            h.await { h.saved().chapter == entry.chapter && h.saved().anchor >= 0 }
            assertEquals("The Japanese chapter must have a recognized HTML language", "true",
                h.js("document.documentElement.getAttribute('xml:lang')==='ja' && document.body.matches(':lang(ja)')"))
        }
    }

    @Test fun illustratedSampleRetainsPublisherBackgroundsAndResponsiveStyles() {
        val (corpus, _) = corpus()
        BookReaderHarness(counts = listOf(1), sourceFactory = { root, _ ->
            File(corpus, "childrens-media-query.epub").copyTo(File(root, "media-query-sample.epub"), overwrite = true)
        }).use { h ->
            if (InstrumentationRegistry.getArguments().getString("expectLandscapeSample") == "true") {
                assertEquals("The sample must open in a wide viewport", "true",
                    h.js("window.miuingConfig.width > window.miuingConfig.height"))
            }
            assertEquals("The EPUB namespaced selector must apply both illustrations", "true", h.js("""(() => {
                const chapter = document.getElementById('miuing-content').querySelector('div');
                const styles = getComputedStyle(chapter);
                const landscape = window.miuingConfig.width > window.miuingConfig.height;
                return styles.backgroundImage.includes('childrens-book-swans.jpg') &&
                    styles.backgroundImage.includes('childrens-book-flowers.jpg') &&
                    (!landscape || (matchMedia('(orientation: landscape)').matches && styles.columnCount === '2'));
            })()"""))
            h.await { h.js("""(() => {
                const resources = performance.getEntriesByType('resource');
                return ['swans', 'flowers'].every(name =>
                    resources.some(entry => entry.name.endsWith('childrens-book-' + name + '.jpg')));
            })()""") == "true" }
        }
    }
}
