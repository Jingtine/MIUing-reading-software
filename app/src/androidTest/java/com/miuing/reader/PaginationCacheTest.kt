package com.miuing.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PaginationCacheTest {
    @Test fun closingBeforeDebouncePersistsTheCompletedImmutableMap()=fixture {root->
        val instrument=InstrumentationRegistry.getInstrumentation()
        instrument.runOnMainSync {
            val host=ReadingTurnHost(instrument.targetContext)
            val coordinator=BookPagination(root,host) {}
            val plan=PaginationPlan("immediate-book","layout",1,1) {error("Known chapter must not need another renderer")}
            coordinator.source(plan,0,ChapterPageRange(1,1,3));coordinator.close();host.close()
        }
        val cache=PaginationCache(root)
        val end=System.currentTimeMillis()+2000
        while(System.currentTimeMillis()<end && cache.read("immediate-book","layout",1)[0]!=3) delay(25)
        assertArrayEquals("Closing before debounce lost the completed map",intArrayOf(3),cache.read("immediate-book","layout",1))
    }
    private fun fixture(block:suspend (File)->Unit)=runBlocking {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"pagination-cache-test-${UUID.randomUUID()}").apply {mkdirs()}
        try {block(root)} finally {withContext(Dispatchers.IO) {root.deleteRecursively()}}
    }
    @Test fun partialCountsSurviveReopenAndWrongLayoutOrCorruptionIsAMiss()=fixture {root->
        PaginationCache(root).write("book-a","layout-a",intArrayOf(3,0,4))
        assertArrayEquals(intArrayOf(3,0,4),PaginationCache(root).read("book-a","layout-a",3))
        assertArrayEquals(IntArray(3),PaginationCache(root).read("book-a","layout-b",3))
        assertArrayEquals(IntArray(2),PaginationCache(root).read("book-a","layout-a",2))
        withContext(Dispatchers.IO) {root.walkTopDown().first {it.extension=="json"}.writeText("broken cache")}
        assertArrayEquals(IntArray(3),PaginationCache(root).read("book-a","layout-a",3))
    }
    @Test fun layoutsAreBoundedPerBookAndWritesCannotClobberConcurrentReads()=fixture {root->
        val first=PaginationCache(root);val second=PaginationCache(root)
        coroutineScope {repeat(12) {i->launch {first.write("book-a","layout-$i",intArrayOf(i+1,2));second.read("book-a","layout-$i",2)}}}
        assertEquals(8,withContext(Dispatchers.IO) {root.walkTopDown().count {it.extension=="json"}})
        first.write("book-a","final",intArrayOf(5,8));assertArrayEquals(intArrayOf(5,8),second.read("book-a","final",2))
        first.write("book-b","layout",intArrayOf(1))
        assertArrayEquals(intArrayOf(5,8),second.read("book-a","final",2))
    }
}
