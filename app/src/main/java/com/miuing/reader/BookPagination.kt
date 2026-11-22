package com.miuing.reader

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume

data class BookPaginationState(val key:String,val chapter:Int,val range:ChapterPageRange,val map:BookPageMap)

/** Serial background work yields the single auxiliary renderer to foreground navigation. */
internal class BookPagination(root:File,private val host:ReadingTurnHost,private val changed:(BookPaginationState?)->Unit) {
    // Only immutable cache snapshots outlive a reader, never views or the worker.
    companion object {private val writes=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)}
    private val cache=PaginationCache(root)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val main=Handler(Looper.getMainLooper())
    private var plan:PaginationPlan?=null
    private var counts=IntArray(0)
    private var failed=mutableSetOf<Int>()
    private var chapter=0
    private var range:ChapterPageRange?=null
    private var worker:Job?=null
    private var checkpoint:Job?=null
    private var loaded=false
    private var foreground=true
    private var closed=false
    fun source(next:PaginationPlan,currentChapter:Int,currentRange:ChapterPageRange) {
        if(closed || currentChapter !in 0 until next.chapters) return
        if(plan?.key!=next.key || plan?.book!=next.book) {
            persistSnapshot()
            worker?.cancel();checkpoint?.cancel();host.cancelMeasurement()
            plan=next;counts=IntArray(next.chapters);failed=mutableSetOf();loaded=false
            changed(null)
        }
        chapter=currentChapter;range=currentRange
        if(counts[chapter]!=currentRange.total) {counts[chapter]=currentRange.total;saveLater()}
        emit();start()
    }
    private fun emit() {
        val p=plan ?: return;val r=range ?: return
        changed(BookPaginationState(p.key,chapter,r,BookPageMap(counts.toList(),p.columns)))
    }
    private fun saveLater() {
        checkpoint?.cancel()
        if(counts.all {it>0}) {persistSnapshot();return}
        checkpoint=scope.launch {delay(500);persistSnapshot()}
    }
    private fun persistSnapshot() {
        val p=plan ?: return
        if(counts.none {it>0}) return
        val disk=cache;val book=p.book;val key=p.key;val snapshot=counts.copyOf()
        // Acquire the cache gate before yielding to IO, so reopen cannot read
        // ahead of the final snapshot and an older write cannot pass a new one.
        writes.launch(start=CoroutineStart.UNDISPATCHED) {disk.write(book,key,snapshot)}
    }
    private fun start() {
        val p=plan ?: return
        if(closed || !foreground || worker?.isActive==true || (loaded && counts.indices.none {counts[it]==0 && it !in failed})) return
        worker=scope.launch {
            if(!loaded) {
                val stored=cache.read(p.book,p.key,p.chapters)
                if(plan!==p) return@launch
                counts.indices.forEach {if(counts[it]==0) counts[it]=stored[it]}
                loaded=true;emit()
            }
            delay(350)
            for(index in counts.indices) {
                if(counts[index]!=0 || index in failed) continue
                while(isActive && plan===p && counts[index]==0 && index !in failed) {
                    if(host.busy) {delay(250);continue}
                    val total=withTimeoutOrNull(8000) {suspendCancellableCoroutine<Int> {continuation->
                        val token=host.measure(p.document(index)) {value->if(continuation.isActive) continuation.resume(value ?: 0)}
                        if(token==null && continuation.isActive) continuation.resume(0)
                        continuation.invokeOnCancellation {main.post {if(token!=null) host.cancelMeasurement(token)}}
                    }}
                    if(plan!==p) return@launch
                    if(total==0) {delay(600);continue}
                    if(total==null || total<0) failed.add(index)
                    else {counts[index]=total;emit();saveLater()}
                }
            }
        }
    }
    fun foreground(value:Boolean) {
        foreground=value
        if(!value) {worker?.cancel();worker=null;host.releaseIdlePreview()}
        else start()
    }
    fun reset() {
        persistSnapshot()
        worker?.cancel();worker=null;checkpoint?.cancel();host.cancelMeasurement()
        plan=null;range=null;counts=IntArray(0);loaded=false;changed(null)
    }
    fun close() {if(closed) return;closed=true;persistSnapshot();worker?.cancel();host.cancelMeasurement();scope.cancel()}
}
