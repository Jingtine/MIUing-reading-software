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
        if(handoff!=path) {handoff=null;reset()}
        main?.let {removeView(it)};main=web;document=path
        addView(web,0,LayoutParams(-1,-1))
    }
    fun unbind(web:WebView) {
        if(main!==web) return
        if(handoff==null) reset()
        main=null;removeView(web)
    }
    fun ready(web:WebView) {
        if(main!==web || handoff!=document) return
        web.afterReadingPaint {if(main===web && handoff==document) {handoff=null;reset()}}
    }
    fun invalidateLayout() {handoff=null;reset();preview?.close();preview=null}
    fun configure(web:WebView) {if(main===web && handoff==null) invalidateLayout()}
    fun begin(web:WebView,id:Int,direction:Int,position:Double,spec:ReadingPreviewSpec,paper:Int) {
        if(closed || main!==web || handoff!=null) return
        reset()
        val g=Gesture(web,id,direction,spec);gesture=g
        postDelayed(timeout,3000)
        if(!ValueAnimator.areAnimatorsEnabled()) {g.failed=true;return}
        web.afterReadingPaint {
            if(gesture!==g || g.failed) return@afterReadingPaint
            val source=overlay.capture(web)
            if(source==null) {g.failed=true;settle(g);return@afterReadingPaint}
            overlay.beginDrag(source,direction,paper);g.sourceReady=true
            g.targetFrame?.let {overlay.targetFrame(it);g.targetFrame=null}
            overlay.dragTo(g.progress);settle(g)
        }
        if(preview?.spec!=spec) {
            preview?.close();preview=null
            preview=runCatching {ReadingPreview(context,spec) {v->
                addView(v,1,LayoutParams(width,height))
                v.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY))
                v.layout(0,0,width,height)
            }}.getOrNull()
        }
        val passive=preview
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
    fun close() {if(closed) return;closed=true;handoff=null;reset();preview?.close();preview=null;main=null}
}
