package com.miuing.reader
import android.graphics.Bitmap
import android.graphics.BitmapFactory

object CoverDecoder {
    fun decode(path:String):Bitmap? {
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(path,options)
        if(options.outWidth<=0 || options.outHeight<=0 || options.outWidth>100000 || options.outHeight>100000) return null
        var sample=1
        while(options.outWidth/sample>512 || options.outHeight/sample>512) sample*=2
        options.inJustDecodeBounds=false;options.inSampleSize=sample
        return BitmapFactory.decodeFile(path,options)
    }
}
