package com.miuing.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.edit
import com.miuing.core.Publication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

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
    var history by rememberSaveable(initial.id, stateSaver = ReadingHistorySaver) { mutableStateOf(emptyList<ReadingLocation>()) }
    var controls by rememberSaveable { mutableStateOf(true) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var footnoteOpen by remember(chapter,jump) {mutableStateOf(false)}
    var dismissFootnote by remember {mutableIntStateOf(0)}
    androidx.activity.compose.BackHandler(footnoteOpen) {dismissFootnote++}
    var pages by remember(chapter) { mutableStateOf<ReaderPageInfo?>(null) }
    var pagination by remember(initial.id) {mutableStateOf<BookPaginationState?>(null)}
    var menuPageMap by remember(initial.id) {mutableStateOf<BookPageMap?>(null)}
    var menuLayoutKey by remember(initial.id) {mutableStateOf<String?>(null)}
    var menuProgress by remember(initial.id) {mutableFloatStateOf(initial.position)}
    var panel by remember { mutableStateOf<ReaderPanel?>(null) }
    var search by rememberSaveable(initial.id) { mutableStateOf("") }
    var searchTarget by rememberSaveable(initial.id) { mutableStateOf("") }
    var searchOrdinal by rememberSaveable(initial.id) {mutableIntStateOf(0)}
    var searchMatch by remember(chapter,jump,searchTarget) {mutableStateOf<SearchMatchInfo?>(null)}
    val locationCapture=remember(initial.id) {ReadingLocationCapture()}
    var results by remember { mutableStateOf<List<TextSearchHit>>(emptyList()) }
    var searchEngine by remember {mutableStateOf<BookSearchEngine?>(null)}
    var activeHits by remember {mutableStateOf<List<TextSearchHit>>(emptyList())}
    var searchGeneration by remember {mutableIntStateOf(0)}
    var searchLimited by remember {mutableStateOf(false)}
    val searchLifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
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
    var extendSleep by rememberSaveable { mutableStateOf(preferences.getBoolean("extendSleep",true)) }
    var themeIndex by rememberSaveable { mutableIntStateOf(preferences.getInt("theme",0).coerceIn(0,5)) }
    var fontSize by rememberSaveable { mutableFloatStateOf(preferences.getFloat("size",20f)) }
    var scrolling by rememberSaveable { mutableStateOf(preferences.getBoolean("scroll",false)) }
    // null while loading, true for a published location, false after a load error.
    var locationAvailable by remember(initial.id, chapter, jump, scrolling) { mutableStateOf<Boolean?>(null) }
    var themeOpen by rememberSaveable {mutableStateOf(false)}
    var pageTurn by rememberSaveable {mutableStateOf(preferences.getString("pageTurn","curl").let {if(it in listOf("fade","slide")) it!! else "curl"})}
    val optionsStore=remember(preferences) {ThemeOptionsStore(preferences)}
    var options by remember(themeIndex) {mutableStateOf(optionsStore.load(themeIndex))}
    val effectiveOptions=options.effective(themeIndex)
    var appearance by rememberSaveable {mutableStateOf(preferences.getString("appearance","system") ?: "system")}
    var brightness by rememberSaveable { mutableFloatStateOf(preferences.getFloat("brightness",-1f)) }
    val window=(context as? android.app.Activity)?.window
    DisposableEffect(window,brightness) {
        val original=window?.attributes?.screenBrightness ?: -1f
        window?.attributes=window?.attributes?.apply { screenBrightness=brightness }
        onDispose { window?.attributes=window?.attributes?.apply { screenBrightness=original } }
    }
    val theme=readingTheme(themeIndex,appearance,isSystemInDarkTheme())
    ReadingSystemBars(window,theme.paper)
    LaunchedEffect(initial.id) {
        try { publication=withContext(Dispatchers.IO) { store.open(initial) } }
        catch(e:Exception) { error=e.localizedMessage ?: "无法打开图书" }
    }
    LaunchedEffect(search,searchTarget,publication,pdfService,panel,searchEngine,searchLifecycle) {
        val generation=++searchGeneration
        searching=false
        if(!searchLifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return@LaunchedEffect
        val recovering=panel!=ReaderPanel.Search && searchTarget.isNotBlank() && activeHits.isEmpty()
        if(panel!=ReaderPanel.Search && !recovering) return@LaunchedEffect
        val query=if(recovering) searchTarget else search
        results=emptyList();pdfHits=emptyList();pdfHit=null;searchProblem=null;searchLimited=false
        if(query.isNotBlank() && publication!=null) {
            searching=true
            try {
                kotlinx.coroutines.delay(300)
                if(book.format=="PDF") pdfHits=pdfService?.search(query).orEmpty()
                else if(searchEngine!=null) {
                    val found=searchEngine!!.search(query,publication!!.chapters.size) {if(generation==searchGeneration) results=it}
                    if(generation==searchGeneration) {results=found.hits;searchLimited=found.limited
                        if(recovering) activeHits=found.hits}
                }
            } catch(e:kotlinx.coroutines.TimeoutCancellationException) {if(generation==searchGeneration) {results=emptyList();searchProblem="章节搜索超时，请缩小关键词后重试"}}
            catch(e:CancellationException) {throw e} catch(e:Exception) {if(generation==searchGeneration) {results=emptyList();searchProblem=e.localizedMessage ?: "搜索失败"}}
            finally {if(generation==searchGeneration) searching=false}
        }
    }
    LaunchedEffect(themeIndex,fontSize,scrolling) { preferences.edit().putInt("theme",themeIndex).putFloat("size",fontSize).putBoolean("scroll",scrolling).apply() }
    LaunchedEffect(pageTurn) {preferences.edit().putString("pageTurn",pageTurn).apply()}
    LaunchedEffect(brightness,appearance) {preferences.edit().putFloat("brightness",brightness).putString("appearance",appearance).apply()}
    fun saveLocated(c:Int,p:Float,a:Int) {
        fragment=""
        chapter=c;position=p.coerceIn(0f,1f);anchor=a
        val b=store.current(initial.id)
        store.update(b.copy(chapter=c,position=position,anchor=a,opened=System.currentTimeMillis()))
    }
    val savePosition:(Int,Float)->Unit={c,p ->
        fragment="";saveLocated(c,p,-1);selection=""
    }
    fun navigate(c:Int,p:Float=0f,a:Int=-1,target:String="",find:String="",match:Int=0,rememberSource:Boolean=true) {
        if(book.format!="PDF") {
            if(locationAvailable==null) return
            if(locationAvailable==true && rememberSource) {
                val source=ReadingLocation(chapter,position,anchor)
                if(history.lastOrNull()!=source) history=(history+source).takeLast(20)
            }
        }
        saveLocated(c,p,a);fragment=target;searchTarget=find;searchOrdinal=match.coerceAtLeast(0);selection="";selectedRange=SelectedText()
        locationAvailable=null;jump++
    }
    fun returnToSource() {
        if(locationAvailable==null) return
        val source=history.lastOrNull() ?: return
        history=history.dropLast(1)
        saveLocated(source.chapter,source.position,source.anchor)
        searchTarget=""
        searchOrdinal=0
        selection="";selectedRange=SelectedText();locationAvailable=null;jump++
    }
    fun toggleBookmark() {
        val current = store.current(book.id)
        val existing = current.marks.firstOrNull { it.chapter == chapter && kotlin.math.abs(it.position - position) < .015f }
        val marks = if (existing != null) current.marks - existing else current.marks + Mark(chapter, position, "${chapter + 1} · ${(position * 100).toInt()}%", anchor)
        store.update(current.copy(marks = marks))
    }
    fun openReadingMenu() {
        val current=pagination?.takeIf {it.chapter==chapter && !scrolling}
        val info=current?.map?.locate(chapter,current.range)
        menuPageMap=current?.map?.takeIf {info!=null}
        menuLayoutKey=current?.key;menuProgress=info?.progress ?: current?.range?.position(current.map.columns) ?: position
        menuOpen=true
    }
    LaunchedEffect(pagination?.key) {if(menuOpen && menuLayoutKey!=null && pagination?.key!=menuLayoutKey) menuOpen=false}
    val selectionActions: @Composable () -> Unit = {
                if(selection.isBlank() && searchTarget.isNotBlank()) {
                    val at=activeHits.indexOfFirst {it.chapter==chapter && it.ordinal==searchOrdinal}
                    val global=if(at>=0 && searchMatch!=null) SearchMatchInfo(at,activeHits.size) else null
                    SearchNavigation(global,locationAvailable==true,theme,
                        onMove={index->activeHits.getOrNull(index)?.let {navigate(it.chapter,find=searchTarget,match=it.ordinal,rememberSource=false)}},
                        onClose={if(history.isNotEmpty()) returnToSource() else locationCapture.capture {p,a->navigate(chapter,p,a,rememberSource=false)}},wholeBook=true)
                }
                if(selection.isNotBlank()) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                    TextButton(onClick={val b=store.current(book.id);store.update(b.copy(notes=b.notes+Note(chapter,selection,start=selectedRange.start,end=selectedRange.end)));selection=""}) { Text("高亮标记") }
                    TextButton(onClick={editingNote=null;noteText="";panel=ReaderPanel.AddNote}) { Text("添加笔记") }
                    if(book.format=="PDF") TextButton(onClick={
                        val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(book.title,selection));selection=""
                        scope.launch {snackbar.showSnackbar("已拷贝")}
                    }) {Text("拷贝")}
                }
    }
    Box(Modifier.fillMaxSize().readingSleep(extendSleep && book.format!="PDF",Triple(menuOpen,themeOpen,panel))) { Surface(color=theme.paper,contentColor=theme.ink) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            if(controls && book.format=="PDF") Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onClose) { Icon(Icons.Default.Close,"返回书库") }
                Text(book.title,Modifier.weight(1f),fontSize=14.sp,maxLines=1)
                IconButton(onClick=::toggleBookmark) { Icon(if(book.marks.any { it.chapter==chapter && kotlin.math.abs(it.position-position)<.015f }) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,"书签") }
                IconButton(onClick={panel=ReaderPanel.Contents}) { Icon(Icons.AutoMirrored.Filled.List,"目录与书签") }
                IconButton(onClick={themeOpen=true}) { Text("aA",fontSize=20.sp) }
                IconButton(onClick={inkMode=!inkMode;selection=""}) {Icon(Icons.Default.Edit,"标记 PDF",tint=if(inkMode) Accent else theme.ink)}
                IconButton(onClick={exportPdf.launch("${book.title}-标记.pdf")},enabled=!exportState.running) {Icon(Icons.Default.FileUpload,"导出 PDF")}
            }
            if(inkMode && book.format=="PDF") Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("画笔",Modifier.weight(1f).padding(start=16.dp),color=Accent)
                IconButton(onClick={val b=store.current(book.id);val at=b.pdfInk.indexOfLast {it.page==chapter};if(at>=0) store.update(b.copy(pdfInk=b.pdfInk.filterIndexed {i,_->i!=at}))},enabled=book.pdfInk.any {it.page==chapter}) {Icon(Icons.AutoMirrored.Filled.Undo,"撤销标记")}
                TextButton(onClick={inkMode=false}) {Text("完成标记")}
            }
            Box(Modifier.weight(1f).fillMaxWidth().then(if(book.format!="PDF") Modifier.padding(top=TextReaderChromeTopInset,bottom=TextReaderChromeBottomInset) else Modifier),contentAlignment=Alignment.Center) {
                if(error!=null) Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(error!!); TextButton(onClick=onClose) { Text("返回书库") } }
                else publication?.let { p ->
                    if(book.format=="PDF") PdfReader(book,chapter,jump,onPosition={savePosition(it,0f)},onTap={controls=!controls},service=pdfService,hit=pdfHit,inkMode=inkMode,
                        onSelection={page,range->chapter=page;selectedRange=range;selection=range.text;if(range.text.isNotBlank()) controls=true},
                        onInk={stroke->val b=store.current(book.id);chapter=stroke.page;store.update(b.copy(chapter=stroke.page,pdfInk=b.pdfInk+stroke))},
                        onMessage={message->scope.launch {snackbar.showSnackbar(message)}})
                    else if(p.chapters.isNotEmpty()) {
                        val safeChapter=chapter.coerceIn(0,p.chapters.size-1)
                        val readerJump=jump
                        TextReader(p,java.io.File(java.io.File(book.source).parentFile,"content"),safeChapter,position,jump,theme,fontSize,scrolling,options.font!="sans",searchTarget,book.notes,
                            onPosition={savePosition(safeChapter,it)},onTap={controls=!controls},onSelection={selection=it},
                            onChapter={next -> if(next in p.chapters.indices) { searchTarget="";searchOrdinal=0;savePosition(next,if(next<safeChapter) 1f else 0f);jump++ } },
                            anchor=anchor,fragment=fragment,lineHeight=effectiveOptions.lineHeight,margin=effectiveOptions.margin,letterSpacing=effectiveOptions.letterSpacing,pageTurn=pageTurn,
                            fontFamily=options.font,boldText=options.bold,wordSpacing=effectiveOptions.wordSpacing,justify=effectiveOptions.justify,customLayout=options.customLayout,
                            chineseLayout=effectiveOptions.chineseLayout,firstLineIndent=effectiveOptions.firstLineIndent,paragraphSpacing=effectiveOptions.paragraphSpacing,
                            onLocation={progress,at->if(jump==readerJump) {saveLocated(safeChapter,progress,at);locationAvailable=true}},
                            onLoadError={if(jump==readerJump) locationAvailable=false},
                            onSelectionRange={ if(panel?.editsNote != true) {selectedRange=it;selection=it.text} },
                            onLink={next,target->navigate(next,target=target)},onPages={pages=it},onPagination={pagination=it},
                            searchIndex=searchOrdinal,onSearchMatch={searchMatch=it},locationCapture=locationCapture,
                            dismissFootnote=dismissFootnote,onFootnoteState={footnoteOpen=it},
                            pauseCounting=panel==ReaderPanel.Search || searchTarget.isNotBlank(),onSearchEngine={searchEngine=it})
                    }
                } ?: CircularProgressIndicator(color=Accent)
            }
            if(controls && book.format=="PDF") {
                selectionActions()
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("第 ${chapter+1} / ${pdfService?.pageCount ?: 1} 页",Modifier.weight(1f),fontSize=12.sp)
                    IconButton(onClick={panel=ReaderPanel.Search}) { Icon(Icons.Default.Search,"在图书中搜索") }
                }
            }
        }
    }
    if (controls && book.format != "PDF" && !footnoteOpen) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            TextReaderChrome(theme, chapter, position, pages, scrolling, onClose, ::openReadingMenu, selectionActions,
                onReturn=if(history.isEmpty()) null else ::returnToSource,returnEnabled=locationAvailable!=null,
                returnLabel=history.lastOrNull()?.let { "第 ${it.chapter+1} 章" } ?: "返回",pagination=pagination)
        }
    }
    SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) }
    if (menuOpen && book.format != "PDF") {
        ReadingMenu(
            title = book.title, theme = theme, chapter = chapter, chapterCount = publication?.chapters?.size ?: 0,
            position = menuProgress, bookmarked = book.marks.any { it.chapter == chapter && kotlin.math.abs(it.position - position) < .015f },
            onDismiss = { menuOpen = false },
            onPanel = { menuOpen = false; panel = it },
            onThemes = { menuOpen = false; themeOpen = true },
            onBookmark = ::toggleBookmark,
            onChapter = { next ->
                if (next in 0 until (publication?.chapters?.size ?: 0)) {
                    navigate(next); menuOpen = false
                }
            },
            onSeek = {fraction ->
                val map=menuPageMap
                if(map!=null) {
                    val total=map.total!!
                    val target=map.target(1+(fraction.coerceIn(0f,1f)*(total-1)).roundToInt())
                    if(target!=null && pagination?.key==menuLayoutKey) navigate(target.chapter,target.position)
                } else navigate(chapter,fraction)
                menuOpen=false
            },progressScope=if(menuPageMap!=null) "全书" else "本章"
        )
    }
    if(themeOpen) ThemeSettingsPanel(themeIndex,options,fontSize,scrolling,pageTurn,brightness,appearance,book.format=="PDF",
        onTheme={themeIndex=it},onOptions={options=it;optionsStore.save(themeIndex,it)},onFontSize={fontSize=it},
        onStyle={if(it=="scroll") scrolling=true else {scrolling=false;pageTurn=it}},onBrightness={brightness=it},onAppearance={appearance=it},onDismiss={themeOpen=false})
    panel?.let { title ->
        ModalBottomSheet(onDismissRequest={
            if(panel?.editsNote == true) {selection="";selectedRange=SelectedText();editingNote=null;noteText=""}
            panel=null
        },containerColor=theme.paper,contentColor=theme.ink) {
            ReadingSystemBars((LocalView.current.parent as? DialogWindowProvider)?.window,theme.paper)
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).navigationBarsPadding()) {
                Text(title.title,fontSize=23.sp,modifier=Modifier.padding(bottom=20.dp))
                when(title) {
                    ReaderPanel.ReadingOptions -> ReadingSleepPanel(extendSleep) { extendSleep=it;preferences.edit { putBoolean("extendSleep",it) } }
                    ReaderPanel.Contents,ReaderPanel.Bookmarks,ReaderPanel.Notes -> {
                        Row { listOf(ReaderPanel.Contents,ReaderPanel.Bookmarks,ReaderPanel.Notes).forEach { TextButton(onClick={panel=it}) { Text(it.title,fontSize=12.sp) } } }
                        when (title) {
                            ReaderPanel.Contents -> if (book.format == "PDF") {
                                PdfContentsPanel(pdfOutline, pdfProblem, { panel = ReaderPanel.GoToPage }) { page -> savePosition(page, 0f); jump++; panel = null }
                            } else ReaderContents(publication?.navigation.orEmpty(), chapter, theme.ink) { entry ->
                                navigate(entry.chapter,target=entry.fragment); panel = null
                            }
                            ReaderPanel.Bookmarks -> ReaderBookmarks(book.marks,
                                onNavigate = { mark -> navigate(mark.chapter,mark.position,mark.anchor); panel = null },
                                onRemove = { mark -> val current = store.current(book.id); store.update(current.copy(marks = current.marks - mark)) })
                            ReaderPanel.Notes -> ReaderNotes(book.notes,
                                onNavigate = { note -> navigate(note.chapter,a=note.start); panel = null },
                                onEdit = { note -> editingNote = note; selection = note.text; selectedRange = SelectedText(note.text, note.start, note.end); noteText = note.note; panel = ReaderPanel.EditNote },
                                onRemove = { note -> val current = store.current(book.id); store.update(current.copy(notes = current.notes - note)) })
                            else -> Unit
                        }
                    }
                    ReaderPanel.Search -> ReaderSearchPanel(search, results, pdfHits, searching, searchProblem ?: if (book.format == "PDF") pdfProblem else if(searchLimited) "已达1000条结果上限" else null,
                        onQuery = { val next=it.take(256);if(next!=search) {searchGeneration++;results=emptyList();searching=true;search=next} },
                        onResult = { hit -> keyboard?.hide(); activeHits=results.toList(); navigate(hit.chapter,find=search,match=hit.ordinal,rememberSource=searchTarget.isBlank()); panel = null },
                        onPdfHit = { hit -> keyboard?.hide(); savePosition(hit.page, 0f); pdfHit = hit; jump++; panel = null })
                    ReaderPanel.GoToPage -> ReaderPageJump(pdfService?.pageCount) { page -> keyboard?.hide(); savePosition(page, 0f); jump++; panel = null }
                    ReaderPanel.AddNote,ReaderPanel.EditNote -> {
                        ReaderNoteEditor(selection, noteText, { noteText = it }, onSave = {
                            val b=store.current(book.id)
                            val edited=editingNote?.copy(note=noteText)
                            val updated=if(edited!=null) b.notes.map { if(it==editingNote) edited else it } else b.notes+Note(chapter,selection,noteText,selectedRange.start,selectedRange.end)
                            store.update(b.copy(notes=updated));editingNote=null;noteText="";selection="";panel=null
                        })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
