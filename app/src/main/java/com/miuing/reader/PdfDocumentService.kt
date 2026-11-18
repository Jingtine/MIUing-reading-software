package com.miuing.reader

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageTree
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.*
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

data class PdfPoint(val x:Float,val y:Float)
data class PdfBox(val left:Float,val top:Float,val right:Float,val bottom:Float)
data class PdfGlyph(val start:Int,val end:Int,val box:PdfBox)
data class PdfText(val text:String,val glyphs:List<PdfGlyph>) {
    fun boxes(start:Int,end:Int)=glyphs.filter {it.end>start && it.start<end}.map {it.box}
}
data class PdfOutline(val title:String,val page:Int,val depth:Int)
data class PdfHit(val page:Int,val start:Int,val end:Int,val snippet:String,val boxes:List<PdfBox>)
data class PdfInk(val page:Int,val points:List<PdfPoint>,val color:String="#D96828",val width:Float=.004f)
data class PdfExistingHighlight(val box:PdfBox,val color:Int,val opacity:Float)
data class PdfExistingMarks(val highlights:List<PdfExistingHighlight> = emptyList(),val ink:List<PdfInk> = emptyList())

private val documentCleanup=CoroutineScope(SupervisorJob()+Dispatchers.IO)

class PdfDocumentService(private val source:File,context:Context):Closeable {
    private val closed=AtomicBoolean(false)
    private val lock=Mutex()
    private val document:PDDocument
    private val pages:List<PDPage>
    val pageCount:Int
    private val cache=object:LinkedHashMap<Int,PdfText>(8,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Int,PdfText>?)=size>6
    }
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
        document=PDDocument.load(source,MemoryUsageSetting.setupMixed(8L*1024*1024).apply {tempDir=context.cacheDir})
        pages=document.pages.toList();pageCount=pages.size
    }
    private fun checkOpen() {check(!closed.get()) {"PDF 已关闭"}}
    private fun geometry(page:PDPage)=page.cropBox.let {PdfGeometry(it.width,it.height,it.lowerLeftX,it.lowerLeftY,page.rotation)}
    suspend fun pageText(page:Int):PdfText=withContext(Dispatchers.IO) {lock.withLock {checkOpen();textLocked(page)}}
    suspend fun annotations(page:Int):PdfExistingMarks=withContext(Dispatchers.IO) {lock.withLock {
        checkOpen();require(page in pages.indices)
        val geometry=geometry(pages[page]);val highlights=ArrayList<PdfExistingHighlight>();val ink=ArrayList<PdfInk>()
        for(annotation in pages[page].annotations) {
            if(annotation.isHidden || annotation.isNoView) continue
            if(annotation is PDAnnotationTextMarkup && annotation.subtype=="Highlight") {
                val points=annotation.quadPoints ?: continue
                val color=runCatching {annotation.color?.toRGB() ?: 0xFFD54F}.getOrDefault(0xFFD54F)
                for(i in points.indices step 8) {
                    if(i+7>=points.size) break
                    val corners=(0..3).map {j->geometry.toDisplay(PdfPoint(points[i+j*2],points[i+j*2+1]))}
                    highlights.add(PdfExistingHighlight(PdfBox(corners.minOf {it.x},corners.minOf {it.y},corners.maxOf {it.x},corners.maxOf {it.y}),color,annotation.constantOpacity.coerceIn(.1f,.5f)))
                }
            } else if(annotation is PDAnnotationMarkup && annotation.subtype=="Ink") {
                val color=runCatching {annotation.color?.toRGB() ?: 0xD96828}.getOrDefault(0xD96828)
                val width=(annotation.borderStyle?.width ?: 1f)/min(geometry.width,geometry.height)
                annotation.inkList?.forEach {path->
                    val points=ArrayList<PdfPoint>()
                    for(i in path.indices step 2) if(i+1<path.size) points.add(geometry.toDisplay(PdfPoint(path[i],path[i+1])))
                    if(points.isNotEmpty()) ink.add(PdfInk(page,points,"#%06x".format(color and 0xffffff),width.coerceAtLeast(.001f)))
                }
            }
        };PdfExistingMarks(highlights,ink)
    }}
    private fun textLocked(page:Int):PdfText {
        require(page in 0 until pageCount) {"无效的 PDF 页码"}
        cache[page]?.let {return it}
        val target=pages[page];val geometry=geometry(target)
        val text=StringBuilder();val glyphs=ArrayList<PdfGlyph>()
        val stripper=object:PDFTextStripper() {
            override fun processPages(unused:PDPageTree) {processPage(target)}
            override fun writeString(value:String,positions:MutableList<TextPosition>) {
                val offset=text.length
                val word=value.take((100000-text.length).coerceAtLeast(0));text.append(word)
                val assigned=BooleanArray(word.length);var cursor=0
                for(p in positions) {
                    val unicode=p.unicode ?: continue
                    val normalized=buildString {unicode.forEach {c->append(if(c.code in 0xFB00..0xFDFF || c.code in 0xFE70..0xFEFF) java.text.Normalizer.normalize(c.toString(),java.text.Normalizer.Form.NFKC).trim() else c.toString())}}
                    if(normalized.isEmpty()) continue
                    var start=word.indexOf(normalized,cursor)
                    if(start<0) start=word.indexOf(normalized)
                    while(start>=0 && (start until start+normalized.length).any {assigned[it]}) start=word.indexOf(normalized,start+1)
                    if(start<0) continue
                    val end=start+normalized.length;(start until end).forEach {assigned[it]=true};cursor=end
                    val x=p.textMatrix.translateX;val y=p.textMatrix.translateY
                    val dx=p.endX-x;val dy=p.endY-y;val length=hypot(dx,dy).coerceAtLeast(.1f)
                    val height=p.heightDir.coerceAtLeast(1f)
                    val nx=-dy/length*height;val ny=dx/length*height
                    val corners=listOf(PdfPoint(x,y),PdfPoint(p.endX,p.endY),PdfPoint(x+nx,y+ny),PdfPoint(p.endX+nx,p.endY+ny)).map {
                        geometry.toDisplay(PdfPoint(it.x+geometry.originX,it.y+geometry.originY))
                    }
                    val box=PdfBox(corners.minOf {it.x}.coerceIn(0f,1f),corners.minOf {it.y}.coerceIn(0f,1f),corners.maxOf {it.x}.coerceIn(0f,1f),corners.maxOf {it.y}.coerceIn(0f,1f))
                    glyphs.add(PdfGlyph(offset+start,offset+end,box))
                }
            }
            override fun writeWordSeparator() {if(text.length<100000) text.append(' ')}
            override fun writeLineSeparator() {if(text.length<100000) text.append('\n')}
        }
        // processPages deliberately selects one cached page instead of walking
        // every page for each extraction. Its local page counter starts at zero.
        stripper.sortByPosition=true;stripper.startPage=0;stripper.endPage=0
        stripper.getText(document)
        return PdfText(text.toString(),glyphs).also {cache[page]=it}
    }
    suspend fun outline():List<PdfOutline> = withContext(Dispatchers.IO) {lock.withLock {
        checkOpen();val entries=ArrayList<PdfOutline>();val visited=HashSet<Int>()
        fun walk(node:PDOutlineNode,depth:Int) {
            if(depth>32 || entries.size>=2000) return
            var child=node.firstChild
            while(child!=null && entries.size<2000) {
                if(!visited.add(System.identityHashCode(child.cosObject))) break
                val item=child
                val page=try {item.findDestinationPage(document)} catch(_:Exception) {null}
                val index=if(page==null) -1 else pages.indexOfFirst {it.cosObject===page.cosObject}
                if(index>=0) entries.add(PdfOutline(item.title ?: "第 ${index+1} 页",index,depth))
                walk(item,depth+1);child=item.nextSibling
            }
        }
        document.documentCatalog.documentOutline?.let {walk(it,0)};entries
    }}
    suspend fun search(query:String):List<PdfHit> = withContext(Dispatchers.IO) {
        if(query.isBlank()) return@withContext emptyList()
        val task=currentCoroutineContext()
        lock.withLock {
            checkOpen();val hits=ArrayList<PdfHit>()
            for(page in 0 until pageCount) {
                task.ensureActive();checkOpen()
                val value=textLocked(page);var at=value.text.indexOf(query,ignoreCase=true)
                while(at>=0 && hits.size<100) {
                    hits.add(PdfHit(page,at,at+query.length,value.text.substring((at-30).coerceAtLeast(0),(at+query.length+50).coerceAtMost(value.text.length)).trim(),value.boxes(at,at+query.length)))
                    at=value.text.indexOf(query,at+query.length,ignoreCase=true)
                }
                if(hits.size>=100) break
            };hits
        }
    }
    suspend fun export(output:OutputStream,notes:List<Note>,ink:List<PdfInk>)=withContext(Dispatchers.IO) {lock.withLock {
        checkOpen()
        // Work on a fresh document every time; never append marks to the cached original.
        PDDocument.load(source,MemoryUsageSetting.setupTempFileOnly()).use { copy->
            for(note in notes) {
                if(note.chapter !in 0 until pageCount) continue
                val content=textLocked(note.chapter)
                val start=if(note.start>=0) note.start else content.text.indexOf(note.text)
                val end=if(note.end>start) note.end else start+note.text.length
                if(start<0) continue
                val boxes=content.boxes(start,end);if(boxes.isEmpty()) continue
                val page=copy.getPage(note.chapter);val geometry=geometry(page)
                val quads=boxes.flatMap {b->listOf(PdfPoint(b.left,b.top),PdfPoint(b.right,b.top),PdfPoint(b.left,b.bottom),PdfPoint(b.right,b.bottom)).map(geometry::toPdf)}
                val mark=PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT)
                mark.quadPoints=quads.flatMap {listOf(it.x,it.y)}.toFloatArray()
                mark.rectangle=bounds(quads);mark.color=PDColor(floatArrayOf(1f,.82f,.2f),PDDeviceRGB.INSTANCE)
                mark.constantOpacity=.45f;mark.contents=note.text+if(note.note.isBlank()) "" else "\n"+note.note
                page.annotations.add(mark);mark.constructAppearances(copy)
            }
            for(stroke in ink) {
                if(stroke.page !in 0 until pageCount || stroke.points.isEmpty()) continue
                val page=copy.getPage(stroke.page);val geometry=geometry(page)
                val points=stroke.points.map(geometry::toPdf)
                val mark=PDAnnotationMarkup();mark.cosObject.setName(COSName.SUBTYPE,"Ink")
                mark.inkList=arrayOf(points.flatMap {listOf(it.x,it.y)}.toFloatArray())
                mark.rectangle=bounds(points).apply {val padding=stroke.width*min(geometry.width,geometry.height);lowerLeftX-=padding;lowerLeftY-=padding;upperRightX+=padding;upperRightY+=padding}
                val color=android.graphics.Color.parseColor(stroke.color)
                mark.color=PDColor(floatArrayOf(android.graphics.Color.red(color)/255f,android.graphics.Color.green(color)/255f,android.graphics.Color.blue(color)/255f),PDDeviceRGB.INSTANCE)
                mark.borderStyle=PDBorderStyleDictionary().apply {width=stroke.width*min(geometry.width,geometry.height)}
                page.annotations.add(mark);mark.constructAppearances(copy)
            }
            copy.save(output)
        }
    }}
    private fun bounds(points:List<PdfPoint>)=PDRectangle().apply {
        lowerLeftX=points.minOf {it.x};lowerLeftY=points.minOf {it.y};upperRightX=points.maxOf {it.x};upperRightY=points.maxOf {it.y}
    }
    override fun close() {
        if(closed.compareAndSet(false,true)) documentCleanup.launch {lock.withLock {cache.clear();document.close()}}
    }
}
