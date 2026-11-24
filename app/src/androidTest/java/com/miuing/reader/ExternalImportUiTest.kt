package com.miuing.reader

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ExternalImportUiTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val context=instrument.targetContext
    private fun uri(id:String)=Uri.parse("content://com.miuing.reader.test.books/$id")
    private fun request(id:String)=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri(id),"text/plain")
    private fun await(block:()->Boolean){val end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(block())return;Thread.sleep(50)};fail("External import timed out")}
    private fun books():List<Book> {
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()) return LibraryStore(context).books.toList()
        var result=emptyList<Book>()
        // AtomicFile readers must not recover .bak concurrently with a Main-thread writer.
        instrument.runOnMainSync {result=LibraryStore(context).books.toList()}
        return result
    }
    private fun hasWeb(v:android.view.View):Boolean = v is WebView || v is ViewGroup && (0 until v.childCount).any {hasWeb(v.getChildAt(it))}
    @Test fun coldOpenWarmShareAndRotationKeepPrivateBooksAndReadingRecords() {
        val tag=UUID.randomUUID().toString();val first="cold-$tag";val second="once-warm-$tag"
        try {
            ActivityScenario.launch<MainActivity>(request(first).setDataAndType(uri(first).buildUpon().appendQueryParameter("slow","true").build(),"text/plain")).use {scenario->
                scenario.recreate()
                await {books().any {it.title=="external-$first"}}
                assertEquals(1,books().count {it.title=="external-$first"})
                var web=false
                await {scenario.onActivity {a->web=hasWeb(a.window.decorView)};web}
                val original=books().first {it.title=="external-$first"}
                scenario.onActivity {a->androidx.lifecycle.ViewModelProvider(a)[LibrarySession::class.java].store.update(original.copy(notes=listOf(Note(0,"外部分享","保留批注"))))}
                scenario.onActivity {a->a.startActivity(Intent(context,MainActivity::class.java).setAction(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM,uri(second)).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))}
                await {books().any {it.title=="external-$second"}}
                scenario.onActivity {a->a.startActivity(request(first).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))}
                await {books().first {it.id==original.id}.opened>original.opened}
                assertEquals(2,books().count {it.title.contains(tag)})
                assertEquals("保留批注",books().first {it.id==original.id}.notes.single().note)
                assertTrue(LibraryStore(context).open(books().first {it.id==original.id}).chapters.isNotEmpty())
            }
        }finally {val store=LibraryStore(context);store.books.filter {it.title.contains(tag)}.toList().forEach {store.delete(it.id)}}
    }
    @Test fun multipleShareContinuesAfterDeniedProviderAndShowsLibrary() {
        val tag=UUID.randomUUID().toString()
        val intent=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM,arrayListOf(uri("denied-$tag"),uri("multi-a-$tag"),uri("multi-b-$tag")))
        try {ActivityScenario.launch<MainActivity>(intent).use {
            await {books().count {it.title.contains(tag)}==2}
            assertTrue(books().none {it.title=="external-denied-$tag"})
        }}finally {val store=LibraryStore(context);store.books.filter {it.title.contains(tag)}.toList().forEach {store.delete(it.id)}}
    }
}
