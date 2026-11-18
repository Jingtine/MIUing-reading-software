package com.miuing.reader

import android.content.SharedPreferences
import java.io.Serializable
import androidx.compose.ui.graphics.Color

data class ReadingTheme(val name:String,val paper:Color,val ink:Color)
val ReadingThemes=listOf(
    ReadingTheme("原始",Color.White,Color(0xFF242424)),
    ReadingTheme("安静",Color(0xFF4A4A4D),Color(0xFFC7C7CC)),
    ReadingTheme("纸张",Color(0xFFEEEDED),Color(0xFF242120)),
    ReadingTheme("粗体",Color.White,Color.Black),
    ReadingTheme("平静",Color(0xFFF1E2C9),Color(0xFF392E25)),
    ReadingTheme("专注",Color(0xFFFFFCF3),Color(0xFF242424))
)
fun readingTheme(index:Int,appearance:String,systemDark:Boolean):ReadingTheme {
    val base=ReadingThemes[index]
    val dark=appearance=="dark" || (appearance=="system" && systemDark)
    return if(dark) base.copy(paper=Color(0xFF242426),ink=Color(0xFFE5E5E7)) else base
}

data class ThemeOptions(
    val font:String="book",val bold:Boolean=false,
    val lineHeight:Float=1.85f,val margin:Float=24f,val letterSpacing:Float=0f,
    val wordSpacing:Float=0f,val justify:Boolean=false,val customLayout:Boolean=false
):Serializable {
    companion object {fun defaults(index:Int)=ThemeOptions(font=when(index) {0->"book";3,5->"sans";else->"serif"},bold=index==3)}
    fun normalized(index:Int):ThemeOptions {
        val defaults=defaults(index)
        fun safe(value:Float,fallback:Float,min:Float,max:Float)=if(value.isFinite()) value.coerceIn(min,max) else fallback
        return copy(font=font.takeIf {it in listOf("book","serif","sans","mono")} ?: defaults.font,
            lineHeight=safe(lineHeight,defaults.lineHeight,1.2f,2.4f),margin=safe(margin,defaults.margin,12f,48f),
            letterSpacing=safe(letterSpacing,0f,0f,.15f),wordSpacing=safe(wordSpacing,0f,0f,.5f))
    }
    fun effective(index:Int)=if(customLayout) this else defaults(index).copy(font=font,bold=bold)
}

class ThemeOptionsStore(private val preferences:SharedPreferences) {
    init {
        if(preferences.getInt("themeOptionsVersion",0)<1) {
            val editor=preferences.edit()
            if(listOf("serif","lineHeight","margin","letterSpacing").any(preferences::contains)) {
                val index=preferences.getInt("theme",0).coerceIn(0,5)
                val defaults=ThemeOptions.defaults(index)
                write(editor,index,defaults.copy(font=if(preferences.getBoolean("serif",true)) "serif" else "sans",
                    lineHeight=preferences.getFloat("lineHeight",1.85f),margin=preferences.getFloat("margin",24f),
                    letterSpacing=preferences.getFloat("letterSpacing",0f),customLayout=true).normalized(index))
            }
            editor.putInt("themeOptionsVersion",1).apply()
        }
    }
    fun load(index:Int):ThemeOptions {
        require(index in 0..5);val d=ThemeOptions.defaults(index);val key="theme.$index."
        return runCatching {ThemeOptions(preferences.getString(key+"font",d.font) ?: d.font,preferences.getBoolean(key+"bold",d.bold),
            preferences.getFloat(key+"lineHeight",d.lineHeight),preferences.getFloat(key+"margin",d.margin),preferences.getFloat(key+"letterSpacing",d.letterSpacing),
            preferences.getFloat(key+"wordSpacing",0f),preferences.getBoolean(key+"justify",false),preferences.getBoolean(key+"customLayout",false)).normalized(index)}.getOrDefault(d)
    }
    private fun write(editor:SharedPreferences.Editor,index:Int,o:ThemeOptions) {
        val key="theme.$index."
        editor.putString(key+"font",o.font).putBoolean(key+"bold",o.bold).putFloat(key+"lineHeight",o.lineHeight)
            .putFloat(key+"margin",o.margin).putFloat(key+"letterSpacing",o.letterSpacing).putFloat(key+"wordSpacing",o.wordSpacing)
            .putBoolean(key+"justify",o.justify).putBoolean(key+"customLayout",o.customLayout)
    }
    fun save(index:Int,options:ThemeOptions) {require(index in 0..5);preferences.edit().also {write(it,index,options.normalized(index))}.apply()}
}
