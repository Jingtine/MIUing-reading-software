package com.miuing.reader

import android.os.Build
import android.content.res.Configuration
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import com.miuing.core.Publication
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class PaginationPlan(val book:String,val key:String,val columns:Int,val chapters:Int,val document:(Int)->ReadingPreviewSpec)
internal class ReadingLayoutBinding {var owner:WebView?=null;var layout:ReadingLayout?=null}
internal fun systemTypographyIdentity(config:Configuration)=listOf(
    config.uiMode,config.fontScale,config.densityDpi,config.locales.toLanguageTags(),
    if(Build.VERSION.SDK_INT>=31) config.fontWeightAdjustment else 0,BuildConfig.PERSONAL_FONTS
).joinToString("|")

/** One immutable configuration factory for the live view, curl preview and page counter. */
internal class ReadingLayout(
    private val root:File,private val publication:Publication,private val chapter:Int,base:String,
    private val notes:List<Note>,private val script:String,private val loader:WebViewAssetLoader,private val markup:String?,
    width:Float,height:Float,systemTypography:String
) {
    val configuration=JSONObject(base).put("width",width).put("height",height)
    private val bookNotes=JSONArray(notes.map {JSONObject().put("chapter",it.chapter).put("text",it.text).put("start",it.start).put("end",it.end)})
    private val geometry=JSONObject(configuration.toString()).apply {
        listOf("position","anchor","fragment","paper","ink","pageTurn","dragEnabled","notes","hasPreviousChapter","hasNextChapter","paginationKey").forEach {remove(it)}
        put("bookNotes",bookNotes)
        if(optString("search").isNotEmpty()) put("searchChapter",chapter)
    }
    private val environment="physical-pages-1|${BuildConfig.VERSION_CODE}|${Build.FINGERPRINT}|${WebView.getCurrentWebViewPackage()?.versionName}|$systemTypography"
    val key=paginationDigest(environment+geometry.toString())
    val plan=PaginationPlan(root.parentFile!!.name+"|"+publication.chapters.joinToString("|") {root.toPath().relativize(it.path).toString()},key,if(width>=900) 2 else 1,publication.chapters.size,::preview)
    init {configuration.put("paginationKey",key)}
    fun preview(index:Int):ReadingPreviewSpec {
        val config=JSONObject(configuration.toString()).apply {
            put("position",0);put("anchor",0);put("fragment","");put("dragEnabled",false)
            put("hasPreviousChapter",index>0);put("hasNextChapter",index<publication.chapters.lastIndex)
            if(index!=chapter) put("search","")
            put("notes",JSONArray(notes.filter {it.chapter==index}.map {JSONObject().put("text",it.text).put("start",it.start).put("end",it.end)}))
        }
        return ReadingPreviewSpec(root,publication.chapters[index].path.toFile(),config.toString(),script,loader,if(index==chapter) markup else null)
    }
}
