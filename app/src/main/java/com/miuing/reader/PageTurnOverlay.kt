package com.miuing.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.webkit.WebView
import kotlin.math.*

/** A temporary visual layer; the WebView remains the source of text and location. */
class PageTurnOverlay(context:Context):View(context) {
    private var snapshot:Bitmap?=null
    private var destination:Bitmap?=null
    private var interactive=false
    private var animation:ValueAnimator?=null
    private var fraction=0f
    private var direction=1
    private var style="curl"
    private var paper=Color.WHITE
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val target=RectF()
    private val foldPath=Path()
    private val shaderMatrix=Matrix()
    // Normalized shaders are repositioned with a matrix, not rebuilt per frame.
    private val foldShade=LinearGradient(0f,0f,1f,0f,intArrayOf(0x50000000,0x08000000,0x28000000),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP)
    private val edgeShade=LinearGradient(0f,0f,1f,0f,0x40000000,Color.TRANSPARENT,Shader.TileMode.CLAMP)
    init {visibility=GONE;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}

    internal fun capture(web:WebView):Bitmap? {
        if(web.width<=0 || web.height<=0) return null
        val scale=min(1f,min(1200f/web.width,min(2000f/web.height,sqrt(2_000_000f/(web.width.toFloat()*web.height)))))
        var captured:Bitmap?=null
        return try {
            captured=Bitmap.createBitmap((web.width*scale).toInt().coerceAtLeast(1),(web.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
            web.draw(Canvas(captured).apply {scale(scale,scale)});captured
        } catch(_:RuntimeException) {captured?.recycle();null}
        catch(_:OutOfMemoryError) {captured?.recycle();null}
    }
    internal fun beginDrag(source:Bitmap,forward:Int,paperColor:Int,mode:String="curl") {
        clear();snapshot=source;direction=forward;paper=paperColor;style=mode;fraction=0f;interactive=true
        visibility=VISIBLE;invalidate()
    }
    internal fun targetFrame(frame:Bitmap) {destination?.recycle();destination=frame;invalidate()}
    internal fun dragTo(progress:Float) {fraction=progress.coerceIn(0f,1f);postInvalidateOnAnimation()}
    internal fun settle(commit:Boolean,finished:()->Unit) {
        val end=if(commit) 1f else 0f
        if(!ValueAnimator.areAnimatorsEnabled() || abs(end-fraction)<.001f) {fraction=end;invalidate();finished();return}
        animation=ValueAnimator.ofFloat(fraction,end).apply {
            duration=(320*abs(end-fraction)).toLong().coerceAtLeast(90)
            addUpdateListener {fraction=it.animatedValue as Float;invalidate()}
            addListener(object:AnimatorListenerAdapter() {override fun onAnimationEnd(animation:Animator) {finished()}})
            start()
        }
    }

    fun turn(web:WebView,mode:String,forward:Int,paperColor:Int,advance:(()->Unit)->Unit) {
        // A fresh turn ends the old visual transition instead of dropping input
        // or allocating another snapshot during rapid navigation.
        if(snapshot!=null) {clear();advance {};return}
        if(!ValueAnimator.areAnimatorsEnabled() || web.width<=0 || web.height<=0) {advance {};return}
        val frame=capture(web) ?: run {advance {};return}
        snapshot=frame;fraction=0f;direction=forward;style=mode;paper=paperColor
        visibility=VISIBLE;invalidate()
        advance {
            // A slide composites both pages, so its target must be paint-confirmed.
            if(style=="slide") web.afterReadingPaint {animateTurn(frame,web)}
            else postOnAnimation {postOnAnimation {animateTurn(frame,web)}}
        }
    }
    private fun animateTurn(frame:Bitmap,web:WebView) {
        if(snapshot!==frame) return
        if(style=="slide") {
            destination=capture(web)
            if(destination==null) {clear();return}
        }
        animation=ValueAnimator.ofFloat(0f,1f).apply {
            duration=when(style) {"fade"->180L;"slide"->300L;else->420L}
            addUpdateListener {fraction=it.animatedValue as Float;invalidate()}
            addListener(object:AnimatorListenerAdapter() {override fun onAnimationEnd(animation:Animator) {clear()}})
            start()
        }
    }
    fun clear() {
        animation?.let {it.removeAllListeners();it.cancel()};animation=null
        snapshot?.recycle();snapshot=null;destination?.recycle();destination=null;interactive=false;visibility=GONE;invalidate()
    }
    override fun onDetachedFromWindow() {clear();super.onDetachedFromWindow()}
    private fun drawSnapshot(canvas:Canvas,image:Bitmap,alpha:Int=255) {
        val state=canvas.save()
        if(direction<0) {canvas.translate(width.toFloat(),0f);canvas.scale(-1f,1f)}
        paint.alpha=alpha;canvas.drawBitmap(image,null,target,paint);paint.alpha=255;canvas.restoreToCount(state)
    }
    private fun placeShade(shader:Shader,left:Float,span:Float) {
        shaderMatrix.setScale(span,1f);shaderMatrix.postTranslate(left,0f)
        shader.setLocalMatrix(shaderMatrix);paint.shader=shader
    }
    override fun onDraw(canvas:Canvas) {
        val image=snapshot ?: return
        val w=width.toFloat();val h=height.toFloat();target.set(0f,0f,w,h)
        paint.shader=null
        if((interactive || style=="slide") && destination==null) {canvas.drawBitmap(image,null,target,paint);return}
        if(style=="slide") {drawSlide(canvas,image,destination!!);return}
        destination?.let {canvas.drawBitmap(it,null,target,paint)}
        if(style=="fade") {paint.alpha=((1-fraction)*255).toInt();canvas.drawBitmap(image,null,target,paint);paint.alpha=255;return}
        val saved=canvas.save()
        if(direction<0) {canvas.translate(w,0f);canvas.scale(-1f,1f)}
        val edge=w*(1-fraction);val fold=min(w*.28f*sin(Math.PI.toFloat()*fraction),w-edge)
        val old=canvas.save();canvas.clipRect(0f,0f,edge,h);drawSnapshot(canvas,image);canvas.restoreToCount(old)
        if(fold>1f) {
            foldPath.rewind()
            foldPath.apply {
                moveTo(edge,0f);quadTo(edge+fold*.65f,h*.015f,edge+fold,h*.06f)
                lineTo(edge+fold,h*.94f);quadTo(edge+fold*.65f,h*.985f,edge,h);close()
            }
            val back=canvas.save();canvas.clipPath(foldPath);canvas.drawColor(paper)
            canvas.save();canvas.translate(2*edge,0f);canvas.scale(-1f,1f);drawSnapshot(canvas,image,45);canvas.restore()
            placeShade(foldShade,edge,fold)
            canvas.drawPath(foldPath,paint);paint.shader=null;canvas.restoreToCount(back)
            placeShade(edgeShade,edge+fold,w*.035f)
            canvas.drawRect(edge+fold,0f,edge+fold+w*.035f,h,paint);paint.shader=null
        }
        canvas.restoreToCount(saved)
    }

    /** iBook cover/slide geometry adapted from Peyilo/libreadview (MIT; assets/licenses). */
    private fun drawSlide(canvas:Canvas,source:Bitmap,next:Bitmap) {
        val w=width.toFloat();val h=height.toFloat()
        val travel=if(direction>0) 1-fraction else fraction
        val primary=if(direction>0) source else next
        val bottom=if(direction>0) next else source
        canvas.drawColor(paper)
        fun page(bitmap:Bitmap,dx:Float) {
            val state=canvas.save();canvas.translate(dx,0f)
            canvas.drawBitmap(bitmap,null,target,paint);canvas.restoreToCount(state)
        }
        page(bottom,w*.25f*travel)
        page(primary,w*(travel-1))
        paint.color=Color.argb((80*travel).toInt(),0,0,0)
        canvas.drawRect(w*travel,0f,w,h,paint)
        paint.color=Color.WHITE
    }
}
