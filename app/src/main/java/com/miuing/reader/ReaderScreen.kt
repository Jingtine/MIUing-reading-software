package com.miuing.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.miuing.core.Publication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class ReadingTheme(val name:String,val paper:Color,val ink:Color)
val ReadingThemes=listOf(
    ReadingTheme("原始",Color(0xFFFFFCF7),Color(0xFF24211E)),
    ReadingTheme("安静",Color(0xFF252525),Color(0xFFE1DED8)),
    ReadingTheme("纸张",Color(0xFFF1E4CA),Color(0xFF3A3025)),
    ReadingTheme("粗体",Color.White,Color.Black),
    ReadingTheme("平静",Color(0xFFE2E6DA),Color(0xFF30382F)),
    ReadingTheme("专注",Color(0xFFE9E7E3),Color(0xFF272523))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReaderScreen(initial:Book,store:LibraryStore,onClose:()->Unit) {
    val book=store.books.first { it.id==initial.id }
    var publication by remember(initial.id) { mutableStateOf<Publication?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var chapter by rememberSaveable(initial.id) { mutableIntStateOf(initial.chapter) }
    var position by rememberSaveable(initial.id) { mutableFloatStateOf(initial.position) }
    var anchor by rememberSaveable(initial.id) { mutableIntStateOf(initial.anchor) }
    var fragment by remember { mutableStateOf("") }
    var jump by remember { mutableIntStateOf(0) }
    var controls by rememberSaveable { mutableStateOf(true) }
    var panel by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<Int,String>>>(emptyList()) }
    var selection by remember { mutableStateOf("") }
    var selectedRange by remember { mutableStateOf(SelectedText()) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var noteText by remember { mutableStateOf("") }
    val context=androidx.compose.ui.platform.LocalContext.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val scope=rememberCoroutineScope()
    val snackbar=remember {SnackbarHostState()}
    var pdfService by remember(initial.id) {mutableStateOf<PdfDocumentService?>(null)}
    var pdfOutline by remember(initial.id) {mutableStateOf<List<PdfOutline>>(emptyList())}
    var pdfHits by remember {mutableStateOf<List<PdfHit>>(emptyList())}
    var pdfHit by remember {mutableStateOf<PdfHit?>(null)}
    var pdfProblem by remember {mutableStateOf<String?>(null)}
    var inkMode by rememberSaveable {mutableStateOf(false)}
    val exportState by PdfExports.state.collectAsState()
    var searching by remember {mutableStateOf(false)}
    var searchProblem by remember {mutableStateOf<String?>(null)}
    val exportPdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {uri->
        if(uri!=null) PdfExports.start(context,store.current(book.id),uri)
    }
    LaunchedEffect(exportState.revision,exportState.message) {
        if(exportState.bookId==book.id && exportState.message!=null) {snackbar.showSnackbar(exportState.message!!);PdfExports.consume(exportState.revision)}
    }
    LaunchedEffect(initial.id) {
        if(book.format=="PDF") {
            var created:PdfDocumentService?=null
            try {
                withContext(Dispatchers.IO) {created=PdfDocumentService(java.io.File(book.source),context)}
                pdfService=created;pdfOutline=created!!.outline()
            } catch(e:CancellationException) {created?.close();throw e} catch(e:Exception) {pdfProblem="无法读取 PDF 文字或目录：${e.localizedMessage}"}
        }
    }
    DisposableEffect(initial.id) {onDispose {pdfService?.close()}}
    val preferences=remember { context.getSharedPreferences("reading",0) }
    var themeIndex by rememberSaveable { mutableIntStateOf(preferences.getInt("theme",0).coerceIn(0,5)) }
    var fontSize by rememberSaveable { mutableFloatStateOf(preferences.getFloat("size",20f)) }
    var scrolling by rememberSaveable { mutableStateOf(preferences.getBoolean("scroll",false)) }
    var pageTurn by rememberSaveable {mutableStateOf(preferences.getString("pageTurn","curl").let {if(it=="fade") "fade" else "curl"})}
    var serif by rememberSaveable { mutableStateOf(preferences.getBoolean("serif",true)) }
    var lineHeight by rememberSaveable { mutableFloatStateOf(preferences.getFloat("lineHeight",1.85f)) }
    var margin by rememberSaveable { mutableFloatStateOf(preferences.getFloat("margin",24f)) }
    var letterSpacing by rememberSaveable { mutableFloatStateOf(preferences.getFloat("letterSpacing",0f)) }
    var brightness by rememberSaveable { mutableFloatStateOf(preferences.getFloat("brightness",-1f)) }
    val window=(context as? android.app.Activity)?.window
    DisposableEffect(window,brightness) {
        val original=window?.attributes?.screenBrightness ?: -1f
        window?.attributes=window?.attributes?.apply { screenBrightness=brightness }
        onDispose { window?.attributes=window?.attributes?.apply { screenBrightness=original } }
    }
    val theme=ReadingThemes[themeIndex]
    LaunchedEffect(initial.id) {
        try { publication=withContext(Dispatchers.IO) { store.open(initial) } }
        catch(e:Exception) { error=e.localizedMessage ?: "无法打开图书" }
    }
    LaunchedEffect(search,publication,pdfService) {
        results=emptyList();pdfHits=emptyList();pdfHit=null;searchProblem=null
        if(search.isNotBlank() && publication!=null) {
            searching=true
            try {
                if(book.format=="PDF") pdfHits=pdfService?.search(search).orEmpty()
                else results=withContext(Dispatchers.IO) {publication!!.chapters.mapIndexedNotNull { i,c ->
                    val markup=com.miuing.core.PublicationParser.readMarkup(c.path)
                    val plain=android.text.Html.fromHtml(markup,android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                    val at=plain.indexOf(search,ignoreCase=true)
                    if(at<0) null else i to plain.substring((at-30).coerceAtLeast(0),(at+search.length+75).coerceAtMost(plain.length)).trim()
                }.take(50)}
            } catch(e:CancellationException) {throw e} catch(e:Exception) {searchProblem=e.localizedMessage ?: "搜索失败"}
            finally {searching=false}
        }
    }
    LaunchedEffect(themeIndex,fontSize,scrolling,serif) { preferences.edit().putInt("theme",themeIndex).putFloat("size",fontSize).putBoolean("scroll",scrolling).putBoolean("serif",serif).apply() }
    LaunchedEffect(pageTurn) {preferences.edit().putString("pageTurn",pageTurn).apply()}
    LaunchedEffect(lineHeight,margin,letterSpacing,brightness) { preferences.edit().putFloat("lineHeight",lineHeight).putFloat("margin",margin).putFloat("letterSpacing",letterSpacing).putFloat("brightness",brightness).apply() }
    fun saveLocated(c:Int,p:Float,a:Int) {
        fragment=""
        chapter=c;position=p.coerceIn(0f,1f);anchor=a
        val b=store.current(initial.id)
        store.update(b.copy(chapter=c,position=position,anchor=a,opened=System.currentTimeMillis()))
    }
    val savePosition:(Int,Float)->Unit={c,p ->
        fragment="";saveLocated(c,p,-1);selection=""
    }
    Box(Modifier.fillMaxSize()) { Surface(color=theme.paper,contentColor=theme.ink) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            if(controls) Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onClose) { Icon(Icons.Default.Close,"返回书库") }
                Text(book.title,Modifier.weight(1f),fontSize=14.sp,maxLines=1)
                IconButton(onClick={
                    val b=store.current(book.id)
                    val existing=b.marks.firstOrNull { it.chapter==chapter && kotlin.math.abs(it.position-position)<.015f }
                    store.update(b.copy(marks=if(existing!=null) b.marks-existing else b.marks+Mark(chapter,position,"${chapter+1} · ${(position*100).toInt()}%",anchor)))
                }) { Icon(if(book.marks.any { it.chapter==chapter && kotlin.math.abs(it.position-position)<.015f }) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,"书签") }
                IconButton(onClick={panel="目录"}) { Icon(Icons.AutoMirrored.Filled.List,"目录与书签") }
                IconButton(onClick={panel="主题与设置"}) { Text("aA",fontSize=20.sp) }
                if(book.format=="PDF") {
                    IconButton(onClick={inkMode=!inkMode;selection=""}) {Icon(Icons.Default.Edit,"标记 PDF",tint=if(inkMode) Accent else theme.ink)}
                    IconButton(onClick={exportPdf.launch("${book.title}-标记.pdf")},enabled=!exportState.running) {Icon(Icons.Default.FileUpload,"导出 PDF")}
                }
            }
            if(inkMode && book.format=="PDF") Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("画笔",Modifier.weight(1f).padding(start=16.dp),color=Accent)
                IconButton(onClick={val b=store.current(book.id);val at=b.pdfInk.indexOfLast {it.page==chapter};if(at>=0) store.update(b.copy(pdfInk=b.pdfInk.filterIndexed {i,_->i!=at}))},enabled=book.pdfInk.any {it.page==chapter}) {Icon(Icons.AutoMirrored.Filled.Undo,"撤销标记")}
                TextButton(onClick={inkMode=false}) {Text("完成标记")}
            }
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                if(error!=null) Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(error!!); TextButton(onClick=onClose) { Text("返回书库") } }
                else publication?.let { p ->
                    if(book.format=="PDF") PdfReader(book,chapter,jump,onPosition={savePosition(it,0f)},onTap={controls=!controls},service=pdfService,hit=pdfHit,inkMode=inkMode,
                        onSelection={page,range->chapter=page;selectedRange=range;selection=range.text;if(range.text.isNotBlank()) controls=true},
                        onInk={stroke->val b=store.current(book.id);chapter=stroke.page;store.update(b.copy(chapter=stroke.page,pdfInk=b.pdfInk+stroke))},
                        onMessage={message->scope.launch {snackbar.showSnackbar(message)}})
                    else if(p.chapters.isNotEmpty()) {
                        val safeChapter=chapter.coerceIn(0,p.chapters.size-1)
                        TextReader(p,java.io.File(java.io.File(book.source).parentFile,"content"),safeChapter,position,jump,theme,fontSize,scrolling,serif,search,book.notes,
                            onPosition={savePosition(safeChapter,it)},onTap={controls=!controls},onSelection={selection=it},
                            onChapter={next -> if(next in p.chapters.indices) { savePosition(next,if(next<safeChapter) 1f else 0f);jump++ } },
                            anchor=anchor,fragment=fragment,lineHeight=lineHeight,margin=margin,letterSpacing=letterSpacing,pageTurn=pageTurn,
                            onLocation={progress,at->saveLocated(safeChapter,progress,at)},
                            onSelectionRange={ if(panel!="添加笔记" && panel!="编辑笔记") {selectedRange=it;selection=it.text} },
                            onLink={next,target->savePosition(next,0f);fragment=target;jump++})
                    }
                } ?: CircularProgressIndicator(color=Accent)
            }
            if(controls) {
                if(selection.isNotBlank()) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                    TextButton(onClick={val b=store.current(book.id);store.update(b.copy(notes=b.notes+Note(chapter,selection,start=selectedRange.start,end=selectedRange.end)));selection=""}) { Text("高亮标记") }
                    TextButton(onClick={editingNote=null;noteText="";panel="添加笔记"}) { Text("添加笔记") }
                    if(book.format=="PDF") TextButton(onClick={
                        val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(book.title,selection));selection=""
                        scope.launch {snackbar.showSnackbar("已拷贝")}
                    }) {Text("拷贝")}
                }
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(book.format!="PDF") {
                        IconButton(onClick={if(chapter>0) {savePosition(chapter-1,0f);jump++}},enabled=chapter>0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,"上一章") }
                        Text("${chapter+1} / ${publication?.chapters?.size ?: 1}",fontSize=12.sp)
                        Slider(position,{position=it},onValueChangeFinished={savePosition(chapter,position);jump++},modifier=Modifier.weight(1f))
                        IconButton(onClick={val count=publication?.chapters?.size ?: 0;if(chapter+1<count) {savePosition(chapter+1,0f);jump++}},enabled=chapter+1<(publication?.chapters?.size ?: 0)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,"下一章") }
                    } else Text("第 ${chapter+1} / ${pdfService?.pageCount ?: 1} 页",Modifier.weight(1f),fontSize=12.sp)
                    IconButton(onClick={panel="搜索"}) { Icon(Icons.Default.Search,"在图书中搜索") }
                }
            }
        }
    }
    SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) }
    panel?.let { title ->
        ModalBottomSheet(onDismissRequest={
            if(panel=="添加笔记" || panel=="编辑笔记") {selection="";selectedRange=SelectedText();editingNote=null;noteText=""}
            panel=null
        },containerColor=theme.paper,contentColor=theme.ink) {
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).navigationBarsPadding()) {
                Text(title,fontSize=23.sp,modifier=Modifier.padding(bottom=20.dp))
                when(title) {
                    "主题与设置" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        if(book.format!="PDF") {
                            Row(verticalAlignment=Alignment.CenterVertically) { Text("A",fontSize=15.sp);Slider(fontSize,{fontSize=it},valueRange=14f..34f,modifier=Modifier.weight(1f));Text("A",fontSize=27.sp) }
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("连续滚动",Modifier.weight(1f)); Switch(scrolling,{scrolling=it},modifier=Modifier.semantics { contentDescription="连续滚动" }) }
                            Text("翻页样式",Modifier.padding(top=8.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("curl" to "卷页","fade" to "快速淡入淡出").forEach {(value,label)->
                                FilterChip(selected=!scrolling && pageTurn==value,onClick={pageTurn=value;scrolling=false},label={Text(label)})
                            }}
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("衬线字体",Modifier.weight(1f)); Switch(serif,{serif=it}) }
                            Text("行距",Modifier.padding(top=16.dp));Slider(lineHeight,{lineHeight=it},valueRange=1.2f..2.4f,modifier=Modifier.semantics { contentDescription="行距" })
                            Text("页边距");Slider(margin,{margin=it},valueRange=12f..48f,modifier=Modifier.semantics { contentDescription="页边距" })
                            Text("字间距");Slider(letterSpacing,{letterSpacing=it},valueRange=0f..0.15f,modifier=Modifier.semantics { contentDescription="字间距" })
                        }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("使用系统亮度",Modifier.weight(1f));Switch(brightness<0,{brightness=if(it) -1f else .6f}) }
                        if(brightness>=0) Slider(brightness,{brightness=it},valueRange=.05f..1f,modifier=Modifier.semantics { contentDescription="阅读亮度" })
                        ReadingThemes.chunked(3).forEachIndexed { row, themes -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) { themes.forEachIndexed { col,t ->
                            Column(Modifier.weight(1f).padding(vertical=5.dp).clip(RoundedCornerShape(12.dp)).background(t.paper).border(if(themeIndex==row*3+col) 2.dp else 1.dp,if(themeIndex==row*3+col) Accent else Color.Gray.copy(alpha=.3f),RoundedCornerShape(12.dp)).clickable { themeIndex=row*3+col }.padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                Text("Aa",fontSize=26.sp,color=t.ink);Text(t.name,fontSize=12.sp,color=t.ink)
                            }
                        } } }
                        Spacer(Modifier.height(24.dp))
                    }
                    "目录","书签","高亮标记和笔记" -> {
                        Row { listOf("目录","书签","高亮标记和笔记").forEach { TextButton(onClick={panel=it}) { Text(it,fontSize=12.sp) } } }
                        LazyColumn(Modifier.heightIn(max=420.dp)) {
                            if(title=="目录") {
                                if(book.format=="PDF") {
                                    item {TextButton(onClick={panel="前往页码"}) {Text("前往页码")}}
                                    itemsIndexed(pdfOutline) {_,entry->TextButton(onClick={savePosition(entry.page,0f);jump++;panel=null},modifier=Modifier.padding(start=(entry.depth*16).dp)) {Text(entry.title)}}
                                    if(pdfOutline.isEmpty()) item {Text(pdfProblem ?: "此 PDF 没有内嵌目录",Modifier.padding(vertical=12.dp),fontSize=13.sp)}
                                }
                                else itemsIndexed(publication?.navigation ?: emptyList()) { _,entry -> TextButton(onClick={if(entry.chapter>=0) {savePosition(entry.chapter,0f);fragment=entry.fragment;jump++;panel=null}},enabled=entry.chapter>=0,modifier=Modifier.padding(start=(entry.depth*16).dp)) { Text(entry.title,color=if(entry.chapter==chapter) Accent else theme.ink) } }
                            } else if(title=="书签") {
                                if(book.marks.isEmpty()) item { Text("没有书签",Modifier.padding(16.dp)) }
                                itemsIndexed(book.marks) { _,mark -> Row(verticalAlignment=Alignment.CenterVertically) { TextButton(onClick={savePosition(mark.chapter,mark.position);saveLocated(mark.chapter,mark.position,mark.anchor);jump++;panel=null},modifier=Modifier.weight(1f)) { Text(mark.label) };IconButton(onClick={store.update(store.current(book.id).copy(marks=book.marks-mark))}) { Icon(Icons.Default.DeleteOutline,"移除书签") } } }
                            } else {
                                if(book.notes.isEmpty()) item { Text("选择正文中的文字以添加高亮标记或笔记。",Modifier.padding(16.dp)) }
                                itemsIndexed(book.notes) { _,note -> Column(Modifier.padding(vertical=12.dp)) { Text(note.text,Modifier.background(Color(0xFFEBCF67).copy(alpha=.35f)).padding(8.dp));if(note.note.isNotBlank()) Text(note.note);Row { TextButton(onClick={savePosition(note.chapter,0f);saveLocated(note.chapter,0f,note.start);jump++;panel=null}) { Text("前往原文") };TextButton(onClick={editingNote=note;selection=note.text;selectedRange=SelectedText(note.text,note.start,note.end);noteText=note.note;panel="编辑笔记"}) { Text("编辑") };TextButton(onClick={store.update(store.current(book.id).copy(notes=book.notes-note))}) { Text("移除") } } } }
                            }
                        }
                    }
                    "搜索" -> {
                        OutlinedTextField(search,{search=it},label={Text("在图书中搜索")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        LazyColumn(Modifier.heightIn(max=360.dp)) {
                            if(book.format=="PDF") itemsIndexed(pdfHits) {_,hit->TextButton(onClick={keyboard?.hide();savePosition(hit.page,0f);pdfHit=hit;jump++;panel=null},modifier=Modifier.semantics {contentDescription="PDF 搜索结果 第 ${hit.page+1} 页"}) {Text("第 ${hit.page+1} 页 · ${hit.snippet}")}}
                            else itemsIndexed(results) { _,result -> TextButton(onClick={keyboard?.hide();savePosition(result.first,0f);jump++;panel=null}) { Text(result.second) } }
                            if(searching) item {CircularProgressIndicator(Modifier.padding(16.dp))}
                            if(searchProblem!=null || (book.format=="PDF" && pdfProblem!=null)) item {Text(searchProblem ?: pdfProblem!!,Modifier.padding(vertical=16.dp))}
                            else if(!searching && search.isNotBlank() && results.isEmpty() && pdfHits.isEmpty()) item { Text("没有找到匹配内容",Modifier.padding(vertical=20.dp)) }
                        }
                    }
                    "前往页码" -> {
                        var page by remember {mutableStateOf("")}
                        OutlinedTextField(page,{page=it.filter(Char::isDigit)},label={Text("页码")},singleLine=true)
                        TextButton(onClick={page.toIntOrNull()?.let {n->keyboard?.hide();savePosition((n-1).coerceIn(0,(pdfService?.pageCount ?: 1)-1),0f);jump++;panel=null}},enabled=pdfService!=null) {Text("前往")}
                    }
                    "添加笔记","编辑笔记" -> {
                        Text(selection.take(300),fontSize=14.sp)
                        OutlinedTextField(noteText,{noteText=it},label={Text("笔记")},modifier=Modifier.fillMaxWidth())
                        TextButton(onClick={
                            val b=store.current(book.id)
                            val edited=editingNote?.copy(note=noteText)
                            val updated=if(edited!=null) b.notes.map { if(it==editingNote) edited else it } else b.notes+Note(chapter,selection,noteText,selectedRange.start,selectedRange.end)
                            store.update(b.copy(notes=updated));editingNote=null;noteText="";selection="";panel=null
                        }) { Text("完成") }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
