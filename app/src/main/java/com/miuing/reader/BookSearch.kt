package com.miuing.reader

import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.*
import kotlin.coroutines.resume

data class TextSearchHit(val chapter:Int,val ordinal:Int,val pre:String,val match:String,val post:String,val start:Int)
internal data class BookSearchResults(val hits:List<TextSearchHit>,val limited:Boolean)

/** Borrows the page counter's single auxiliary renderer; all methods run on Main. */
class BookSearchEngine internal constructor(private val host:ReadingTurnHost,private val binding:ReadingLayoutBinding) {
    internal val layoutKey = derivedStateOf { binding.layout?.searchKey }
    private data class CompletedSearch(val query: String, val layoutKey: String, val chapters: Int, val result: BookSearchResults)
    private var lastCompleted: CompletedSearch? = null

    internal suspend fun search(query:String,chapters:Int,progress:(List<TextSearchHit>)->Unit):BookSearchResults {
        while (true) {
            val layoutKey = withTimeout(20000) { readyLayout().searchKey }
            lastCompleted?.takeIf { it.query == query && it.layoutKey == layoutKey && it.chapters == chapters }?.let { return it.result }
            val result = scanBook(query, chapters, layoutKey, progress)
            if (result != null) {
                lastCompleted = CompletedSearch(query, layoutKey, chapters, result)
                return result
            }
            // A viewport or style change may alter which text is visible.
            progress(emptyList())
        }
    }

    private suspend fun scanBook(
        query: String, chapters: Int, layoutKey: String, progress: (List<TextSearchHit>) -> Unit
    ): BookSearchResults? {
        val hits = mutableListOf<TextSearchHit>()
        for (chapter in 0 until chapters) {
            val result = withTimeout(20000) { scan(chapter, query, 1001 - hits.size, layoutKey) } ?: return null
            val array = result.getJSONArray("hits")
            val countBefore = hits.size
            for (i in 0 until array.length()) {
                if (hits.size == 1000) return BookSearchResults(hits.toList(), true)
                val hit = array.getJSONObject(i)
                hits.add(TextSearchHit(chapter, hit.getInt("index"), hit.getString("pre"), hit.getString("match"), hit.getString("post"), hit.getInt("start")))
            }
            if (hits.size != countBefore) progress(hits.toList())
            if (result.getBoolean("more") || hits.size == 1000) return BookSearchResults(hits.toList(), true)
        }
        return BookSearchResults(hits.toList(), false)
    }

    private suspend fun readyLayout(): ReadingLayout {
        while (true) {
            currentCoroutineContext().ensureActive()
            val layout = binding.layout
            if (layout != null && !host.busy) return layout
            delay(30)
        }
    }

    private suspend fun scan(chapter: Int, query: String, limit: Int, layoutKey: String): org.json.JSONObject? {
        while (true) {
            val layout = readyLayout()
            if (layout.searchKey != layoutKey) return null
            val revision = host.layoutRevision
            val spec = layout.preview(chapter)
            var started = false
            val result = suspendCancellableCoroutine<org.json.JSONObject?> { continuation ->
                val done: (org.json.JSONObject?) -> Unit = { if (continuation.isActive) continuation.resume(it) }
                started = host.search(spec, query, limit, done)
                if (!started) continuation.resume(null)
                continuation.invokeOnCancellation {host.cancelSearch(done)}
            }
            currentCoroutineContext().ensureActive()
            if (binding.layout?.searchKey != layoutKey) return null
            if (result != null && host.layoutRevision == revision) return result
            if (started && result == null && host.layoutRevision == revision) error("第${chapter+1}章搜索未完成，请重试")
            // Give pagination cancellation or viewport reconfiguration time to release the preview.
            delay(30)
        }
    }
}
