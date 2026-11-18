package com.miuing.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.Build
import android.graphics.pdf.RenderParams
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

private val pdfCleanup=CoroutineScope(SupervisorJob()+Dispatchers.IO)

@Composable fun PdfReader(
    book:Book,page:Int,jump:Int,onPosition:(Int)->Unit,onTap:()->Unit,
    service:PdfDocumentService?=null,hit:PdfHit?=null,inkMode:Boolean=false,
    onSelection:(Int,SelectedText)->Unit={_,_->},onInk:(PdfInk)->Unit={},onMessage:(String)->Unit={}
) {
    val opened=remember(book.id) {runCatching {PdfRenderer(ParcelFileDescriptor.open(File(book.source),ParcelFileDescriptor.MODE_READ_ONLY))}}
    val renderer=opened.getOrNull()
    if(renderer==null) {Text("无法打开 PDF：${opened.exceptionOrNull()?.localizedMessage}");return}
    val closed=remember(renderer) {AtomicBoolean(false)}
    val state=rememberLazyListState(page.coerceIn(0,renderer.pageCount-1))
    var zoom by remember {mutableFloatStateOf(1f)}
    var pan by remember {mutableStateOf(Offset.Zero)}
    val transform=rememberTransformableState {scale,offset,_->zoom=(zoom*scale).coerceIn(1f,3f);pan=if(zoom==1f) Offset.Zero else pan+offset}
    val callback by rememberUpdatedState(onPosition)
    LaunchedEffect(jump) {state.scrollToItem(page.coerceIn(0,renderer.pageCount-1));zoom=1f;pan=Offset.Zero}
    LaunchedEffect(state) {snapshotFlow {
        val layout=state.layoutInfo
        // At the document end or while a bitmap replaces its placeholder, a
        // fragment of the previous page can remain above the page being read.
        layout.visibleItemsInfo.maxByOrNull {item->
            min(item.offset+item.size,layout.viewportEndOffset)-max(item.offset,layout.viewportStartOffset)
        }?.index ?: state.firstVisibleItemIndex
    }.collect {callback(it)}}
    DisposableEffect(renderer) {onDispose {closed.set(true);pdfCleanup.launch {synchronized(renderer) {renderer.close()}}}}
    LazyColumn(state=state,modifier=Modifier.fillMaxSize().transformable(transform,enabled=!inkMode,canPan={zoom>1f}),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(renderer.pageCount) {index->
            var problem by remember {mutableStateOf<String?>(null)}
            val bitmap by produceState<Bitmap?>(null,index) {
                try {value=withContext(Dispatchers.IO) {val task=currentCoroutineContext();synchronized(renderer) {
                    task.ensureActive();if(closed.get()) return@synchronized null
                    renderer.openPage(index).use {p->
                        val scale=min(1200f/p.width,2400f/p.height)
                        Bitmap.createBitmap((p.width*scale).toInt().coerceAtLeast(1),(p.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            if(Build.VERSION.SDK_INT>=35) p.render(it,null,null,RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY).setRenderFlags(0).build())
                            else p.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }}} catch(e:CancellationException) {throw e} catch(e:Exception) {problem=e.localizedMessage ?: "页面渲染失败"}
            }
            val text by produceState<PdfText?>(null,index,service) {
                try {value=service?.pageText(index)} catch(e:CancellationException) {throw e} catch(_:Exception) {value=null}
            }
            val imported by produceState(PdfExistingMarks(),index,service) {
                try {value=service?.annotations(index) ?: PdfExistingMarks()} catch(e:CancellationException) {throw e} catch(_:Exception) {value=PdfExistingMarks()}
            }
            var selected by remember {mutableStateOf<SelectedText?>(null)}
            var draft by remember {mutableStateOf<List<PdfPoint>>(emptyList())}
            var origin by remember {mutableIntStateOf(-1)}
            var selectionPoint by remember {mutableStateOf(PdfPoint(0f,0f))}
            var selectionTravel by remember {mutableFloatStateOf(0f)}
            LaunchedEffect(book.notes,inkMode) {selected=null}
            val latestSelection by rememberUpdatedState(onSelection)
            val latestInk by rememberUpdatedState(onInk)
            bitmap?.let {image->
                Box(Modifier.fillMaxWidth().aspectRatio(image.width.toFloat()/image.height).graphicsLayer(scaleX=zoom,scaleY=zoom,translationX=pan.x,translationY=pan.y)) {
                    Image(image.asImageBitmap(),"第 ${index+1} 页",Modifier.fillMaxSize())
                    Canvas(Modifier.matchParentSize()
                        .pointerInput(inkMode,text) {
                            if(inkMode) detectDragGestures(
                                onDragStart={p->draft=listOf(PdfPoint((p.x/size.width).coerceIn(0f,1f),(p.y/size.height).coerceIn(0f,1f)))},
                                onDrag={change,_->change.consume();if(draft.size<5000) draft=draft+PdfPoint((change.position.x/size.width).coerceIn(0f,1f),(change.position.y/size.height).coerceIn(0f,1f))},
                                onDragEnd={if(draft.isNotEmpty()) latestInk(PdfInk(index,draft));draft=emptyList()},onDragCancel={draft=emptyList()}
                            ) else detectDragGesturesAfterLongPress(
                                onDragStart={p->
                                    val content=text
                                    if(content==null || content.glyphs.isEmpty()) onMessage("此页没有可选择的文字")
                                    else {
                                        val point=PdfPoint(p.x/size.width,p.y/size.height)
                                        selectionPoint=point;selectionTravel=0f
                                        val glyph=nearest(content,point)
                                        if(glyph!=null) {
                                            var a=glyph.start;var b=glyph.end
                                            fun word(c:Char)=c.code<128 && c.isLetterOrDigit()
                                            if(content.text.getOrNull(a)?.let(::word)==true) {
                                                while(a>0 && word(content.text[a-1])) a--
                                                while(b<content.text.length && word(content.text[b])) b++
                                            }
                                            origin=a;selected=SelectedText(content.text.substring(a,b),a,b);latestSelection(index,selected!!)
                                        }
                                    }
                                },
                                onDrag={change,amount->
                                    val content=text;val start=origin
                                    selectionPoint=PdfPoint(selectionPoint.x+amount.x/size.width,selectionPoint.y+amount.y/size.height)
                                    selectionTravel+=hypot(amount.x,amount.y)
                                    if(content!=null && start>=0 && selectionTravel>viewConfiguration.touchSlop) nearest(content,selectionPoint,false)?.let {glyph->
                                        val a=min(start,glyph.start);val b=max(start+1,glyph.end)
                                        selected=SelectedText(content.text.substring(a,b),a,b);latestSelection(index,selected!!)
                                    };change.consume()
                                },onDragEnd={origin=-1},onDragCancel={origin=-1}
                            )
                        }.pointerInput(inkMode,selected) {
                            detectTapGestures(onTap={if(!inkMode) {if(selected!=null) {selected=null;latestSelection(index,SelectedText())} else onTap()}})
                        }
                    ) {
                        fun boxes(items:List<PdfBox>,color:Color) {items.forEach {b->drawRect(color,Offset(b.left*size.width,b.top*size.height),Size((b.right-b.left)*size.width,(b.bottom-b.top)*size.height))}}
                        if(hit?.page==index) boxes(hit.boxes,Color(0xFFFFA726).copy(alpha=.45f))
                        imported.highlights.forEach {boxes(listOf(it.box),Color(0xff000000.toInt() or it.color).copy(alpha=it.opacity))}
                        book.notes.filter {it.chapter==index}.forEach {note->text?.let {content->val a=if(note.start>=0) note.start else content.text.indexOf(note.text);if(a>=0) boxes(content.boxes(a,if(note.end>a) note.end else a+note.text.length),Color(0xFFFFD54F).copy(alpha=.45f))}}
                        selected?.let {range->text?.let {boxes(it.boxes(range.start,range.end),Color(0xFF42A5F5).copy(alpha=.4f))}}
                        fun stroke(points:List<PdfPoint>,color:Color,width:Float) {
                            if(points.isEmpty()) return
                            val path=Path().apply {moveTo(points[0].x*size.width,points[0].y*size.height);points.drop(1).forEach {lineTo(it.x*size.width,it.y*size.height)}}
                            drawPath(path,color,style=Stroke(width*min(size.width,size.height),cap=StrokeCap.Round))
                        }
                        book.pdfInk.filter {it.page==index}.forEach {stroke(it.points,Color(android.graphics.Color.parseColor(it.color)),it.width)}
                        imported.ink.forEach {stroke(it.points,Color(android.graphics.Color.parseColor(it.color)),it.width)}
                        stroke(draft,Accent,.004f)
                    }
                }
            } ?: Box(Modifier.fillMaxWidth().height(400.dp)) {if(problem!=null) Text(problem!!) else CircularProgressIndicator()}
        }
    }
}

private fun nearest(text:PdfText,point:PdfPoint,strict:Boolean=true):PdfGlyph? {
    val glyph=text.glyphs.minByOrNull {g->val b=g.box;val dx=max(max(b.left-point.x,0f),point.x-b.right);val dy=max(max(b.top-point.y,0f),point.y-b.bottom);dx*dx+dy*dy} ?: return null
    val b=glyph.box;val distance=hypot(max(max(b.left-point.x,0f),point.x-b.right),max(max(b.top-point.y,0f),point.y-b.bottom))
    return if(strict && distance>.025f) null else glyph
}
