package com.miuing.reader

import kotlinx.coroutines.*
import kotlin.coroutines.resume

data class TextSearchHit(val chapter:Int,val ordinal:Int,val pre:String,val match:String,val post:String)
internal data class BookSearchResults(val hits:List<TextSearchHit>,val limited:Boolean)

/** Borrows the page counter's single auxiliary renderer; all methods run on Main. */
class BookSearchEngine internal constructor(private val host:ReadingTurnHost,private val binding:ReadingLayoutBinding) {
    internal suspend fun search(query:String,chapters:Int,progress:(List<TextSearchHit>)->Unit):BookSearchResults {
        val hits=mutableListOf<TextSearchHit>()
        for(chapter in 0 until chapters) {
            currentCoroutineContext().ensureActive()
            val result=withTimeout(20000) {scan(chapter,query,1001-hits.size)}
            val array=result.getJSONArray("hits")
            for(i in 0 until array.length()) {
                if(hits.size==1000) return BookSearchResults(hits.toList(),true)
                val hit=array.getJSONObject(i)
                hits.add(TextSearchHit(chapter,hit.getInt("index"),hit.getString("pre"),hit.getString("match"),hit.getString("post")))
            }
            progress(hits.toList())
            if(result.getBoolean("more") || hits.size==1000) return BookSearchResults(hits.toList(),true)
        }
        return BookSearchResults(hits.toList(),false)
    }
    private suspend fun scan(chapter:Int,query:String,limit:Int):org.json.JSONObject {
        while(true) {
            while(binding.layout==null || host.busy) delay(30)
            val revision=host.layoutRevision
            val spec=binding.layout!!.preview(chapter)
            val result=suspendCancellableCoroutine<org.json.JSONObject?> {continuation->
                val done:(org.json.JSONObject?)->Unit={if(continuation.isActive) continuation.resume(it)}
                if(!host.search(spec,query,limit,done)) continuation.resume(null)
                continuation.invokeOnCancellation {host.cancelSearch(done)}
            }
            currentCoroutineContext().ensureActive()
            if(result!=null) return result
            if(host.layoutRevision==revision) error("第${chapter+1}章搜索未完成，请重试")
            // Let the layout invalidation finish before borrowing its next preview.
            yield()
        }
    }
}
