package com.miuing.reader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import android.widget.FrameLayout

/** Owns temporary visuals; the current document remains the authority for navigation. */
internal class ReadingTurnHost(context:Context):FrameLayout(context) {
    val overlay=PageTurnOverlay(context)
    private var main:WebView?=null
    private var document=""
    private var preview:ReadingPreview?=null
    private var gesture:Gesture?=null
    private var handoff:String?=null
    private var closed=false
    private var sourceReady=false
    private var viewportWidth=0
    private var viewportHeight=0
    private var layoutKey=""
    private data class Measurement(val token:Any,val done:(Int?)->Unit)
    private var measurement:Measurement?=null
    private var searchTask:((org.json.JSONObject?)->Unit)?=null
    var layoutRevision=0
        private set
    var measurementStarts=0
        private set
    val busy get()=closed || !sourceReady || main==null || gesture!=null || handoff!=null || overlay.visibility==VISIBLE
    private class Gesture(val web:WebView,val id:Int,val direction:Int,val target:ReadingPreviewSpec) {
        var progress=0f;var release:Boolean?=null;var sourceReady=false;var targetReady=false
        var targetFrame:Bitmap?=null;var settling=false;var failed=false
    }
    private val timeout=Runnable {gesture?.let {it.failed=true;preview?.cancelSeek();it.targetFrame?.recycle();it.targetFrame=null;settle(it)}}
    private val handoffTimeout=Runnable {handoff=null;reset()}
    init {isChildrenDrawingOrderEnabled=true;addView(overlay,LayoutParams(-1,-1))}
    // Logical order keeps the live reader first for accessibility/tests; raster
    // order keeps the attached, visible preview behind the opaque live reader.
    override fun getChildDrawingOrder(childCount:Int,drawingPosition:Int):Int =
        if(childCount==3 && drawingPosition<2) 1-drawingPosition else drawingPosition

    fun bind(web:WebView,path:String) {
        if(main===web) return
        cancelMeasurement()
        if(handoff!=path) {handoff=null;reset()}
        sourceReady=false
        main?.let {removeView(it)};main=web;document=path
        addView(web,0,LayoutParams(-1,-1))
    }
    fun unbind(web:WebView) {
        if(main!==web) return
        sourceReady=false;cancelMeasurement()
        if(handoff==null) reset()
        main=null;removeView(web)
    }
    fun ready(web:WebView) {
        if(main!==web) return
        sourceReady=true
        if(handoff!=document) return
        web.afterReadingPaint {if(main===web && handoff==document) {handoff=null;reset()}}
    }
    fun invalidateLayout() {
        layoutRevision++;cancelMeasurement();handoff=null;reset()
        if(searchTask!=null) cancelSearch() else {preview?.close();preview=null}
    }
    fun viewportChanged(width:Int,height:Int):Boolean {
        if(width==viewportWidth && height==viewportHeight) return false
        viewportWidth=width;viewportHeight=height;invalidateLayout();return true
    }
    fun configure(web:WebView,key:String) {
        if(main!==web) return
        if(layoutKey!=key) {sourceReady=false;layoutKey=key}
        if(handoff==null) invalidateLayout()
    }
    private fun passive(spec:ReadingPreviewSpec):ReadingPreview? {
        if(preview?.spec==spec) return preview
        preview?.close();preview=null
        preview=runCatching {ReadingPreview(context,spec) {v->
            addView(v,1,LayoutParams(width,height))
            v.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY))
            v.layout(0,0,width,height)
        }}.getOrNull()
        return preview
    }
    fun measure(spec:ReadingPreviewSpec,done:(Int?)->Unit):Any? {
        if(busy || measurement!=null || searchTask!=null) return null
        val task=Measurement(Any(),done);measurement=task;measurementStarts++
        val view=passive(spec)
        if(view==null) {completeMeasurement(task,-1);return task.token}
        view.measurePages({total->completeMeasurement(task,total)},{completeMeasurement(task,-1)})
        return task.token
    }
    private fun completeMeasurement(task:Measurement,total:Int?) {
        if(measurement!==task) return
        measurement=null;preview?.close();preview=null;task.done(total)
    }
    fun cancelMeasurement(token:Any?=null) {
        val task=measurement ?: return
        if(token!=null && task.token!==token) return
        completeMeasurement(task,null)
    }
    fun releaseIdlePreview() {cancelMeasurement();cancelSearch();if(gesture==null && handoff==null) {preview?.close();preview=null}}
    fun search(spec:ReadingPreviewSpec,query:String,limit:Int,done:(org.json.JSONObject?)->Unit):Boolean {
        if(busy || measurement!=null || searchTask!=null) return false
        searchTask=done
        val view=passive(spec)
        if(view==null) {cancelSearch();return true}
        view.search(query,limit) {result->if(searchTask===done) {
            searchTask=null;preview?.close();preview=null;done(result)
        }}
        return true
    }
    fun cancelSearch(owner:((org.json.JSONObject?)->Unit)?=null) {if(owner!=null && searchTask!==owner) return;val callback=searchTask ?: return;searchTask=null;preview?.close();preview=null;callback(null)}
    fun turn(web:WebView,mode:String,direction:Int,paper:Int,advance:(()->Unit)->Unit) {
        cancelMeasurement();overlay.turn(web,mode,direction,paper,advance)
    }
    fun begin(web:WebView,id:Int,direction:Int,position:Double,spec:ReadingPreviewSpec,paper:Int,mode:String="curl") {
        if(closed || main!==web || handoff!=null) return
        cancelMeasurement()
        reset()
        val g=Gesture(web,id,direction,spec);gesture=g
        postDelayed(timeout,3000)
        if(!ValueAnimator.areAnimatorsEnabled()) {g.failed=true;return}
        web.afterReadingPaint {
            if(gesture!==g || g.failed) return@afterReadingPaint
            val source=overlay.capture(web)
            if(source==null) {g.failed=true;settle(g);return@afterReadingPaint}
            overlay.beginDrag(source,direction,paper,mode);g.sourceReady=true
            g.targetFrame?.let {overlay.targetFrame(it);g.targetFrame=null}
            overlay.dragTo(g.progress);settle(g)
        }
        val passive=passive(spec)
        if(passive==null) {g.failed=true;settle(g);return}
        passive.seek(position,{v->
            if(gesture===g && !g.failed) {
                val frame=overlay.capture(v)
                if(frame==null) g.failed=true
                else if(g.sourceReady) overlay.targetFrame(frame) else g.targetFrame=frame
                if(frame!=null) {g.targetReady=true;overlay.dragTo(g.progress)}
                settle(g)
            }
        },{if(gesture===g) {g.failed=true;settle(g)}})
    }
    fun move(web:WebView,id:Int,progress:Float) {
        val g=gesture ?: return
        if(g.web!==web || g.id!=id || g.release!=null) return
        g.progress=progress;overlay.dragTo(progress)
    }
    fun end(web:WebView,id:Int,commit:Boolean) {
        val g=gesture ?: return
        if(g.web!==web || g.id!=id || g.release!=null) return
        g.release=commit;settle(g)
    }
    fun abort(web:WebView,id:Int) {if(gesture?.let {it.web===web && it.id==id}==true) reset()}
    private fun settle(g:Gesture) {
        if(gesture!==g || g.settling) return
        if(g.sourceReady && g.targetReady) removeCallbacks(timeout)
        val commit=g.release ?: return
        if(!commit && (!g.sourceReady || !g.targetReady)) {
            g.settling=true;removeCallbacks(timeout);preview?.cancelSeek();finish(g,false);return
        }
        if(!g.failed && (!g.sourceReady || !g.targetReady)) return
        g.settling=true;removeCallbacks(timeout)
        if(g.failed) {overlay.clear();finish(g,commit)} else overlay.settle(commit) {finish(g,commit)}
    }
    private fun finish(g:Gesture,commit:Boolean) {
        if(gesture!==g) return
        // Establish handoff before JS posts the chapter callback to Compose.
        if(commit && g.target.path.path!=document) {handoff=g.target.path.path;postDelayed(handoffTimeout,5000)}
        g.web.evaluateJavascript("window.miuingFinishDrag(${g.id},$commit)") {result->
            if(gesture!==g) return@evaluateJavascript
            if(result=="1") g.web.afterReadingPaint {if(gesture===g) reset()}
            else if(result!="2") {handoff=null;reset()}
        }
    }
    private fun reset() {
        removeCallbacks(timeout);removeCallbacks(handoffTimeout)
        gesture?.targetFrame?.recycle();gesture=null
        preview?.cancelSeek();overlay.clear()
    }
    fun close() {if(closed) return;closed=true;cancelSearch();cancelMeasurement();handoff=null;reset();preview?.close();preview=null;main=null}
}
