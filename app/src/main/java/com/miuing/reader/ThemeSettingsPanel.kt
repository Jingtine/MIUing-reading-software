package com.miuing.reader

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ThemeSettingsPanel(
    themeIndex:Int,options:ThemeOptions,fontSize:Float,scrolling:Boolean,pageTurn:String,brightness:Float,appearance:String,isPdf:Boolean,
    onTheme:(Int)->Unit,onOptions:(ThemeOptions)->Unit,onFontSize:(Float)->Unit,onStyle:(String)->Unit,
    onBrightness:(Float)->Unit,onAppearance:(String)->Unit,onDismiss:()->Unit
) {
    var route by rememberSaveable {mutableStateOf("main")}
    var draft by rememberSaveable(themeIndex) {mutableStateOf(options)}
    var discard by rememberSaveable {mutableStateOf(false)}
    var styleMenu by remember {mutableStateOf(false)}
    var appearanceMenu by remember {mutableStateOf(false)}
    val mainScroll=rememberScrollState()
    val customizeScroll=rememberScrollState()
    val fontScroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    val systemBrightness=remember(context) {(android.provider.Settings.System.getInt(context.contentResolver,android.provider.Settings.System.SCREEN_BRIGHTNESS,153)/255f).coerceIn(.05f,1f)}
    val displayedBrightness=if(brightness<0) systemBrightness else brightness
    val dark=appearance=="dark" || (appearance=="system" && isSystemInDarkTheme())
    val ink=if(dark) Color(0xFFF2F2F4) else Color(0xFF171719)
    val paper=if(dark) Color(0xFF2C2C2E) else Color(0xFFF3F3F3)
    val control=ink.copy(alpha=.065f)
    val dismiss:()->Unit={when(route) {
        "font"->route="custom"
        "custom"->if(draft!=options) discard=true else route="main"
        else->onDismiss()
    }}
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        ReadingSystemBars(window,readingTheme(themeIndex,appearance,isSystemInDarkTheme()).paper,paper)
        SideEffect {window?.setDimAmount(.12f);window?.attributes=window?.attributes?.apply {screenBrightness=brightness}}
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            val wide=maxWidth>=700.dp
            Box(Modifier.fillMaxSize().clickable(onClick=dismiss,indication=null,interactionSource=remember {androidx.compose.foundation.interaction.MutableInteractionSource()}))
            Surface(color=paper,contentColor=ink,shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp,bottomStart=if(wide) 20.dp else 0.dp,bottomEnd=if(wide) 20.dp else 0.dp),
                modifier=Modifier.align(if(wide) Alignment.BottomEnd else Alignment.BottomCenter).padding(if(wide) 24.dp else 0.dp)
                    .width(if(wide) 380.dp else maxWidth.coerceAtMost(600.dp)).heightIn(max=maxHeight*.92f)) {
                Column(Modifier.padding(horizontal=16.dp,vertical=12.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        if(route!="main") IconButton(onClick=dismiss,modifier=Modifier.size(36.dp)) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回主题与设置",Modifier.size(20.dp))}
                        Text(when(route) {"custom"->"自定义主题";"font"->"字体";else->"主题与设置"},fontSize=21.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                        if(route=="main") IconButton(onClick=onDismiss,modifier=Modifier.size(36.dp)) {
                            Box(Modifier.size(28.dp).background(control,CircleShape),contentAlignment=Alignment.Center) {Icon(Icons.Default.Close,"关闭主题与设置",Modifier.size(18.dp),tint=ink.copy(alpha=.5f))}
                        } else TextButton(onClick={if(route=="font") route="custom" else {onOptions(draft.normalized(themeIndex));route="main"}}) {Text("完成",color=ink,fontWeight=FontWeight.SemiBold)}
                    }
                    Column(Modifier.weight(1f,fill=false).verticalScroll(when(route) {"custom"->customizeScroll;"font"->fontScroll;else->mainScroll}).padding(top=12.dp)) {
                        when(route) {
                            "main"->{
                                Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                                    if(!isPdf) Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(control),verticalAlignment=Alignment.CenterVertically) {
                                        TextButton(onClick={onFontSize((fontSize-1).coerceAtLeast(14f))},enabled=fontSize>14f,modifier=Modifier.weight(1f).heightIn(min=42.dp).semantics {contentDescription="减小字号"}) {Text("A",fontSize=16.sp,color=ink)}
                                        Box(Modifier.width(1.dp).height(24.dp).background(ink.copy(alpha=.15f)))
                                        TextButton(onClick={onFontSize((fontSize+1).coerceAtMost(34f))},enabled=fontSize<34f,modifier=Modifier.weight(1f).heightIn(min=42.dp).semantics {contentDescription="增大字号"}) {Text("A",fontSize=25.sp,color=ink)}
                                    } else Text("页面外观",Modifier.weight(1f),fontSize=15.sp)
                                    if(!isPdf) Box {
                                        IconButton(onClick={styleMenu=true},modifier=Modifier.size(52.dp,42.dp).background(control,RoundedCornerShape(10.dp))) {Icon(Icons.AutoMirrored.Outlined.Note,"翻页样式",tint=ink)}
                                        DropdownMenu(styleMenu,{styleMenu=false}) {listOf("curl" to "卷页","slide" to "平移","fade" to "快速淡入淡出","scroll" to "滚动").forEach {(value,label)->
                                            DropdownMenuItem(text={Text(label)},leadingIcon={if((value=="scroll" && scrolling) || (!scrolling && value==pageTurn)) Icon(Icons.Default.Check,null)},onClick={onStyle(value);styleMenu=false},modifier=if(value=="scroll") Modifier.semantics {contentDescription="连续滚动"} else Modifier)
                                        }}
                                    }
                                    Box {
                                        IconButton(onClick={appearanceMenu=true},modifier=Modifier.size(52.dp,42.dp).background(control,RoundedCornerShape(10.dp))) {Icon(Icons.Default.Contrast,"外观",tint=ink)}
                                        DropdownMenu(appearanceMenu,{appearanceMenu=false}) {
                                            listOf("light" to "浅色","dark" to "深色","system" to "匹配设备").forEach {(value,label)->DropdownMenuItem(text={Text(label)},leadingIcon={if(appearance==value) Icon(Icons.Default.Check,null)},onClick={onAppearance(value);appearanceMenu=false})}
                                            HorizontalDivider()
                                            DropdownMenuItem(text={Text("使用系统亮度")},leadingIcon={if(brightness<0) Icon(Icons.Default.Check,null)},onClick={onBrightness(-1f);appearanceMenu=false})
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.LightMode,null,Modifier.size(16.dp),tint=ink.copy(alpha=.5f))
                                    val colors=SliderDefaults.colors(activeTrackColor=ink.copy(alpha=.5f),inactiveTrackColor=ink.copy(alpha=.1f))
                                    Slider(displayedBrightness,onBrightness,valueRange=.05f..1f,
                                        modifier=Modifier.weight(1f).padding(horizontal=10.dp).height(36.dp).semantics {contentDescription="阅读亮度"},colors=colors,thumb={},track={
                                            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                                                val y=size.height/2;drawLine(ink.copy(alpha=.1f),Offset(0f,y),Offset(size.width,y),size.height,StrokeCap.Round)
                                                val fraction=(displayedBrightness-.05f)/.95f
                                                drawLine(ink.copy(alpha=.5f),Offset(0f,y),Offset(size.width*fraction,y),size.height,StrokeCap.Round)
                                            }
                                        })
                                    Icon(Icons.Outlined.LightMode,null,Modifier.size(20.dp),tint=ink.copy(alpha=.5f))
                                }
                                HorizontalDivider(color=ink.copy(alpha=.1f))
                                BoxWithConstraints(Modifier.padding(top=22.dp)) {
                                    val cardHeight=maxOf((maxWidth-20.dp)/3/1.2f,(58*LocalDensity.current.fontScale+20).dp)
                                    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {ReadingThemes.chunked(3).forEachIndexed {row,themes->
                                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {themes.forEachIndexed {column,theme->
                                            val index=row*3+column
                                            Column(Modifier.weight(1f).height(cardHeight).clip(RoundedCornerShape(10.dp)).background(theme.paper)
                                                .border(if(index==themeIndex) 3.dp else 1.dp,if(index==themeIndex) {if(theme.paper.luminance()<.4f) Color.White else Color.Black} else Color.Black.copy(alpha=.08f),RoundedCornerShape(10.dp))
                                                .selectable(selected=index==themeIndex,role=Role.RadioButton,onClick={onTheme(index)}).padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                                Text("Aa",fontSize=32.sp,fontFamily=if(index in listOf(1,2,4)) FontFamily.Serif else FontFamily.SansSerif,fontWeight=if(index==3) FontWeight.Bold else FontWeight.Normal,color=theme.ink)
                                                Text(theme.name,fontSize=14.sp,color=theme.ink)
                                            }
                                        }}
                                    }}
                                }
                                if(!isPdf) Box(Modifier.fillMaxWidth().padding(top=24.dp,bottom=4.dp),contentAlignment=Alignment.Center) {
                                    FilledTonalButton(onClick={scope.launch {customizeScroll.scrollTo(0);fontScroll.scrollTo(0);draft=options;route="custom"}},colors=ButtonDefaults.filledTonalButtonColors(containerColor=control,contentColor=ink),shape=RoundedCornerShape(11.dp),contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp)) {
                                        Icon(Icons.Default.Settings,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("自定义",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                                    }
                                }
                            }
                            "font"->ReadingFonts.choices.forEach {font->
                                val value=font.id
                                Row(Modifier.fillMaxWidth().clickable {draft=draft.copy(font=value)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                                    RadioButton(selected=draft.font==value,onClick={draft=draft.copy(font=value)})
                                    Text(font.label,fontSize=17.sp)
                                }
                            }
                            "custom"->{
                                Row(Modifier.fillMaxWidth().clickable {route="font"}.semantics {contentDescription="选择字体"}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Text("字体",Modifier.weight(1f),fontSize=17.sp);Text(if(draft.font=="book") "原始" else ReadingFonts.find(draft.font)?.label ?: "原始",color=ink.copy(alpha=.6f));Icon(Icons.Default.ChevronRight,null)
                                }
                                ThemeToggle("粗体文本",draft.bold,{draft=draft.copy(bold=it)})
                                HorizontalDivider(Modifier.padding(vertical=14.dp),color=ink.copy(alpha=.1f))
                                Text("辅助功能与布局选项",fontSize=13.sp,color=ink.copy(alpha=.55f))
                                ThemeToggle("自定义",draft.customLayout,{draft=draft.copy(customLayout=it)},"自定义布局")
                                if(draft.customLayout) {
                                    ThemeSpacing("行间距","行距",draft.lineHeight,1.2f..2.4f) {draft=draft.copy(lineHeight=it)}
                                    ThemeSpacing("字符间距","字间距",draft.letterSpacing,0f..0.15f) {draft=draft.copy(letterSpacing=it)}
                                    ThemeSpacing("词间距","词间距",draft.wordSpacing,0f..0.5f) {draft=draft.copy(wordSpacing=it)}
                                    ThemeSpacing("页边空白","页边距",draft.margin,12f..48f) {draft=draft.copy(margin=it)}
                                    ThemeToggle("使文本两端对齐",draft.justify,{draft=draft.copy(justify=it)})
                                    ThemeToggle("中文排版",draft.chineseLayout,{draft=draft.copy(chineseLayout=it)})
                                    if(draft.chineseLayout) {
                                        ThemeSpacing("首行缩进","首行缩进",draft.firstLineIndent,0f..3f,valueLabel="%.1f 字".format(java.util.Locale.ROOT,draft.firstLineIndent)) {draft=draft.copy(firstLineIndent=it)}
                                        ThemeSpacing("段间距","段间距",draft.paragraphSpacing,0f..1.5f,valueLabel="%.1f 倍字号".format(java.util.Locale.ROOT,draft.paragraphSpacing)) {draft=draft.copy(paragraphSpacing=it)}
                                    }
                                }
                                TextButton(onClick={draft=ThemeOptions.defaults(themeIndex)},modifier=Modifier.fillMaxWidth().padding(top=12.dp,bottom=8.dp)) {Text("还原主题",color=ink)}
                            }
                        }
                    }
                }
            }
        }
    }
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("放弃未存储的主题更改？")},confirmButton={TextButton(onClick={draft=options;discard=false;route="main"}) {Text("放弃更改")}},dismissButton={TextButton(onClick={discard=false}) {Text("继续编辑")}})
}

@Composable private fun ThemeToggle(label:String,value:Boolean,onChange:(Boolean)->Unit,description:String=label) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f).padding(end=12.dp),fontSize=16.sp)
        Switch(value,onChange,modifier=Modifier.semantics {contentDescription=description},colors=SwitchDefaults.colors(
            checkedThumbColor=Color.White,checkedTrackColor=Color(0xFF34C759),uncheckedThumbColor=Color.White,
            uncheckedTrackColor=LocalContentColor.current.copy(alpha=.16f),uncheckedBorderColor=Color.Transparent))
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ThemeSpacing(label:String,description:String,value:Float,range:ClosedFloatingPointRange<Float>,valueLabel:String?=null,onChange:(Float)->Unit) {
    Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,fontSize=14.sp)
        if(valueLabel!=null) Text(valueLabel,fontSize=14.sp,color=LocalContentColor.current.copy(alpha=.6f))
    }
    val ink=LocalContentColor.current
    Slider(value,onChange,valueRange=range,modifier=Modifier.fillMaxWidth().semantics {contentDescription=description},
        thumb={Box(Modifier.size(18.dp).background(ink,CircleShape))},track={
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val y=size.height/2;val fraction=(value-range.start)/(range.endInclusive-range.start)
                drawLine(ink.copy(alpha=.12f),Offset(0f,y),Offset(size.width,y),size.height,StrokeCap.Round)
                drawLine(ink.copy(alpha=.55f),Offset(0f,y),Offset(size.width*fraction,y),size.height,StrokeCap.Round)
            }
        })
}
