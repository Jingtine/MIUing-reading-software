package com.miuing.reader

import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

/** Capture the visible passage, including scrolling not yet written by the debounce. */
class ReadingLocationCapture {
    private var owner:WebView?=null
    internal fun bind(view:WebView) {owner=view}
    internal fun unbind(view:WebView) {if(owner===view) owner=null}
    internal fun capture(result:(Float,Int)->Unit) {
        val view=owner ?: return
        view.evaluateJavascript("JSON.stringify(window.miuingLocation())") {value->
            if(owner!==view) return@evaluateJavascript
            val location=runCatching {JSONObject(JSONTokener(value).nextValue() as String)}.getOrNull() ?: return@evaluateJavascript
            val position=location.optDouble("position",Double.NaN)
            val anchor=location.optInt("anchor",-1)
            if(position.isFinite() && position in 0.0..1.0 && anchor>=-1) result(position.toFloat(),anchor)
        }
    }
}
