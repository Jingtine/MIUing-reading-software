package com.miuing.reader

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit host-driven fixture: instrumentation must end before killing the app process. */
class EpubProcessRestoreFixture {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val mode get()=InstrumentationRegistry.getArguments().getString("processFixtureMode")
    private fun ownedTitle():String {
        val token=InstrumentationRegistry.getArguments().getString("processFixtureToken").orEmpty()
        require(token.matches(Regex("[0-9a-f]{32}"))) {"A per-run fixture ownership token is required"}
        return "MIUing-process-restore-fixture-$token"
    }
    @Test fun seed()=runBlocking {
        assumeTrue(mode=="seed")
        val title=ownedTitle()
        val store=LibraryStore(context)
        require(store.books.none {it.title==title}) {"Remove previous process fixture first"}
        val source=File(context.cacheDir,"$title.epub")
        var created:Book?=null;var complete=false
        try {
            EpubCompatibilityFixtures.book(source,title)
            val book=store.import(Uri.fromFile(source))
            created=book
            check(InstrumentationRegistry.getArguments().getString("processFixtureFailAfterImport")!="true") {"Injected fixture seed failure"}
            val anchor=(0 until 320).sumOf {"兼容性原文段落 $it — Reading passage $it, reliable font and location restoration. 阅读文字不会丢失。".length}
            store.update(book.copy(anchor=anchor,position=.5f,marks=listOf(Mark(0,.5f,"进程恢复书签",anchor)),
                notes=listOf(Note(0,"Reading passage 320","进程恢复笔记"))))
            assertEquals(anchor,LibraryStore(context).current(book.id).anchor)
            complete=true
        } finally {
            try {if(!complete) created?.let {store.delete(it.id)}} finally {source.delete()}
        }
    }
    @Test fun cleanup() {
        assumeTrue(mode=="cleanup")
        val title=ownedTitle()
        val store=LibraryStore(context)
        store.books.filter {it.title==title}.forEach {store.delete(it.id)}
        File(context.cacheDir,"$title.epub").delete()
    }
}
