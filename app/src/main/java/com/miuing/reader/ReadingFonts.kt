package com.miuing.reader

import org.json.JSONObject
import org.json.JSONArray

internal data class ReadingFontSource(val asset:String,val unicodeRange:String?=null,val sample:String=" ")
internal data class ReadingFont(val id:String,val label:String,val asset:String?=null,val family:String?=null,
    val sources:List<ReadingFontSource> = asset?.let {listOf(ReadingFontSource(it))} ?: emptyList())

/** Body choices only. Interface typography continues to use the Android system. */
internal object ReadingFonts {
    val choices=listOf(
        ReadingFont("book","原始（书籍字体）"),
        ReadingFont("serif","衬线字体"),
        ReadingFont("sans","无衬线字体"),
        ReadingFont("mono","等宽字体"),
        ReadingFont("buming","不明体","buming.ttf","MIUingBuMing"),
        ReadingFont("source-han-serif","思源宋体","source-han-serif.otf","MIUingSourceHanSerif"),
        ReadingFont("fz-jingsong","方正晶宋","fz-jingsong.ttf","MIUingFZJingSong"),
        ReadingFont("kinghwa","京華老宋体",family="MIUingKingHwa",sources=listOf(
            ReadingFontSource("kinghwa-text.ttf","U+0-4DFF","A"),
            ReadingFontSource("kinghwa-cjk.ttf","U+4E00-9FFF","中"),
            ReadingFontSource("kinghwa-extended.ttf","U+A000-10FFFF","，"))),
        ReadingFont("source-han-sans","思源黑体","source-han-sans.otf","MIUingSourceHanSans"),
        ReadingFont("lxgw-wenkai","霞鹜文楷","lxgw-wenkai.ttf","MIUingLXGWWenKai")
    ) + if(BuildConfig.PERSONAL_FONTS) listOf(
        ReadingFont("st-kaiti","华文楷体","st-kaiti.ttf","MIUingSTKaiti"),
        ReadingFont("kai-ti","楷体","kai-ti.ttf","MIUingKaiTi")
    ) else emptyList()
    fun find(id:String)=choices.firstOrNull {it.id==id}
    fun face(id:String):JSONObject?=find(id)?.takeIf {it.sources.isNotEmpty()}?.let {font->
        JSONObject().put("family",font.family).put("sample",font.sources.joinToString("") {it.sample}).put("sources",JSONArray(font.sources.map {
            JSONObject().put("url","https://appassets.androidplatform.net/reader-assets/fonts/${it.asset}").put("unicodeRange",it.unicodeRange)
        }))
    }
}
