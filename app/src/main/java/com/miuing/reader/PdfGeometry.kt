package com.miuing.reader

data class PdfGeometry(val width:Float,val height:Float,val originX:Float=0f,val originY:Float=0f,val rotation:Int=0) {
    init {require(width>0 && height>0)}
    private val turn get()=((rotation%360)+360)%360
    fun toPdf(point:PdfPoint):PdfPoint = when(turn) {
        90->PdfPoint(originX+point.y*width,originY+point.x*height)
        180->PdfPoint(originX+(1-point.x)*width,originY+point.y*height)
        270->PdfPoint(originX+(1-point.y)*width,originY+(1-point.x)*height)
        else->PdfPoint(originX+point.x*width,originY+(1-point.y)*height)
    }
    fun toDisplay(point:PdfPoint):PdfPoint {
        val x=(point.x-originX)/width;val y=(point.y-originY)/height
        return when(turn) {90->PdfPoint(y,x);180->PdfPoint(1-x,y);270->PdfPoint(1-y,1-x);else->PdfPoint(x,1-y)}
    }
}
