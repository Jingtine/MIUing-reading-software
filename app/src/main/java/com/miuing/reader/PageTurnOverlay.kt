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
    private var animation:ValueAnimator?=null
    private var fraction=0f
    private var direction=1
    private var style="curl"
    private var paper=Color.WHITE
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    init {visibility=GONE;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}

    fun turn(web:WebView,mode:String,forward:Int,paperColor:Int,advance:(()->Unit)->Unit) {
        if(snapshot!=null) return
        if(!ValueAnimator.areAnimatorsEnabled() || web.width<=0 || web.height<=0) {advance {};return}
        val scale=min(1f,min(1200f/web.width,min(2000f/web.height,sqrt(2_000_000f/(web.width.toFloat()*web.height)))))
        var captured:Bitmap?=null
        try {
            captured=Bitmap.createBitmap((web.width*scale).toInt().coerceAtLeast(1),(web.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
            web.draw(Canvas(captured).apply {scale(scale,scale)})
        } catch(_:RuntimeException) {captured?.recycle();advance {};return}
        catch(_:OutOfMemoryError) {captured?.recycle();advance {};return}
        val frame=captured!!
        snapshot=frame;fraction=0f;direction=forward;style=mode;paper=paperColor
        visibility=VISIBLE;invalidate()
        advance {
            // Give Chromium two frames to paint the destination behind the snapshot.
            postOnAnimation {postOnAnimation {
                if(snapshot!==frame) return@postOnAnimation
                animation=ValueAnimator.ofFloat(0f,1f).apply {
                    duration=if(style=="fade") 180L else 420L
                    addUpdateListener {fraction=it.animatedValue as Float;invalidate()}
                    addListener(object:AnimatorListenerAdapter() {override fun onAnimationEnd(animation:Animator) {clear()}})
                    start()
                }
            }}
        }
    }
    fun clear() {
        animation?.let {it.removeAllListeners();it.cancel()};animation=null
        snapshot?.recycle();snapshot=null;visibility=GONE;invalidate()
    }
    override fun onDetachedFromWindow() {clear();super.onDetachedFromWindow()}
    override fun onDraw(canvas:Canvas) {
        val image=snapshot ?: return
        val w=width.toFloat();val h=height.toFloat();val target=RectF(0f,0f,w,h)
        paint.shader=null
        if(style=="fade") {paint.alpha=((1-fraction)*255).toInt();canvas.drawBitmap(image,null,target,paint);paint.alpha=255;return}
        val saved=canvas.save()
        if(direction<0) {canvas.translate(w,0f);canvas.scale(-1f,1f)}
        fun drawOld(alpha:Int=255) {
            val state=canvas.save()
            if(direction<0) {canvas.translate(w,0f);canvas.scale(-1f,1f)}
            paint.alpha=alpha;canvas.drawBitmap(image,null,target,paint);paint.alpha=255;canvas.restoreToCount(state)
        }
        val edge=w*(1-fraction);val fold=min(w*.28f*sin(Math.PI.toFloat()*fraction),w-edge)
        val old=canvas.save();canvas.clipRect(0f,0f,edge,h);drawOld();canvas.restoreToCount(old)
        if(fold>1f) {
            val path=Path().apply {
                moveTo(edge,0f);quadTo(edge+fold*.65f,h*.015f,edge+fold,h*.06f)
                lineTo(edge+fold,h*.94f);quadTo(edge+fold*.65f,h*.985f,edge,h);close()
            }
            val back=canvas.save();canvas.clipPath(path);canvas.drawColor(paper)
            canvas.save();canvas.translate(2*edge,0f);canvas.scale(-1f,1f);drawOld(45);canvas.restore()
            paint.shader=LinearGradient(edge,0f,edge+fold,0f,intArrayOf(0x50000000,0x08000000,0x28000000),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP)
            canvas.drawPath(path,paint);paint.shader=null;canvas.restoreToCount(back)
            paint.shader=LinearGradient(edge+fold,0f,edge+fold+w*.035f,0f,0x40000000,Color.TRANSPARENT,Shader.TileMode.CLAMP)
            canvas.drawRect(edge+fold,0f,edge+fold+w*.035f,h,paint);paint.shader=null
        }
        canvas.restoreToCount(saved)
    }
}
