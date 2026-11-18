package com.miuing.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.*
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class PdfDocumentServiceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun ligaturesAreSearchableUsingOrdinarySpelling()=runBlocking {
        PDFBoxResourceLoader.init(context)
        val source=File(context.cacheDir,"ligature.pdf")
        try {
            PDDocument().use {doc->
                val page=PDPage();doc.addPage(page)
                val font=PDType0Font.load(doc,File("/system/fonts/Roboto-Regular.ttf"))
                PDPageContentStream(doc,page).use {s->s.beginText();s.setFont(font,20f);s.newLineAtOffset(60f,700f);s.showText("of\uFB01ce");s.endText()};doc.save(source)
            }
            PdfDocumentService(source,context).use {service->
                assertTrue("Ligature text was not normalized",service.pageText(0).text.contains("office"))
                val hits=service.search("office")
                assertEquals(1,hits.size);assertTrue(hits[0].boxes.isNotEmpty())
            }
        } finally {source.delete()}
    }
    @Test fun croppedRotatedPagesKeepTextAndExportGeometryAligned()=runBlocking {
        PDFBoxResourceLoader.init(context)
        val source=File(context.cacheDir,"rotated-service.pdf");val result=File(context.cacheDir,"rotated-result.pdf")
        try {
            PDDocument().use {doc->
                for(rotation in listOf(0,90,180,270)) {
                    val page=PDPage(PDRectangle(700f,900f));page.cropBox=PDRectangle(10f,20f,600f,800f);page.rotation=rotation;doc.addPage(page)
                    PDPageContentStream(doc,page).use {s->s.beginText();s.setFont(PDType1Font.HELVETICA,20f);s.newLineAtOffset(70f,720f);s.showText("First");s.endText()}
                };doc.save(source)
            }
            PdfDocumentService(source,context).use {service->
                val boxes=(0..3).map {service.pageText(it).glyphs.first().box}
                assertTrue(boxes[0].left in .09f.. .12f && boxes[0].top in .09f.. .13f)
                assertTrue(boxes[1].left in .86f.. .91f && boxes[1].top in .09f.. .13f)
                assertTrue(boxes[2].left in .86f.. .91f && boxes[2].top in .86f.. .91f)
                assertTrue(boxes[3].left in .09f.. .13f && boxes[3].top in .86f.. .91f)
                result.outputStream().use {out->service.export(out,(0..3).map {Note(it,"First",start=0,end=5)},emptyList())}
            }
            PDDocument.load(result).use {doc->for(i in 0..3) {
                val mark=doc.getPage(i).annotations.first() as com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
                // Appearance construction deliberately expands /Rect for the rounded
                // highlight caps. /QuadPoints describes the selected text itself.
                val points=mark.quadPoints
                assertEquals("Export x, page $i",70f,points.filterIndexed {index,_->index%2==0}.min(),1f)
                assertEquals("Export y, page $i",720f,points.filterIndexed {index,_->index%2==1}.min(),1f)
            }}
        } finally {source.delete();result.delete()}
    }
    private fun fixture(file:File) {
        PDFBoxResourceLoader.init(context)
        PDDocument().use { doc->
            for(i in 0..1) {
                val page=PDPage(PDRectangle(600f,800f));doc.addPage(page)
                PDPageContentStream(doc,page).use {stream->
                    stream.beginText();stream.setFont(PDType1Font.HELVETICA,20f);stream.newLineAtOffset(60f,700f)
                    stream.showText(if(i==0) "First searchable passage." else "Second searchable passage.");stream.endText()
                }
            }
            val outline=PDDocumentOutline();doc.documentCatalog.documentOutline=outline
            val parent=PDOutlineItem().apply {title="Part One";destination=PDPageFitDestination().apply {page=doc.getPage(0)}}
            parent.addLast(PDOutlineItem().apply {title="Second Section";destination=PDPageFitDestination().apply {page=doc.getPage(1)}})
            outline.addLast(parent);doc.save(file)
        }
    }
    @Test fun extractsSearchesOutlinesAndExportsMarksWithoutChangingOriginal()=runBlocking {
        val source=File(context.cacheDir,"pdf-service-test.pdf");val exported=File(context.cacheDir,"pdf-service-export.pdf")
        fixture(source)
        val before=MessageDigest.getInstance("SHA-256").digest(source.readBytes())
        try {
            PdfDocumentService(source,context).use { service->
                assertEquals(2,service.pageCount)
                val text=service.pageText(0)
                assertTrue("Missing text extraction",text.text.contains("First searchable passage."))
                val glyph=text.glyphs.first()
                assertTrue("Glyph geometry incorrect",glyph.box.left in .09f.. .12f && glyph.box.top in .08f.. .14f)
                val outline=service.outline()
                assertEquals(listOf("Part One","Second Section"),outline.map {it.title})
                assertEquals(1,outline[1].page);assertEquals(1,outline[1].depth)
                val hits=service.search("searchable")
                assertEquals(2,hits.size);assertTrue(hits.all {it.boxes.isNotEmpty()})
                val hit=hits[0]
                exported.outputStream().use { service.export(it,listOf(Note(0,"searchable","A test note",hit.start,hit.end)),listOf(PdfInk(1,listOf(PdfPoint(.1f,.2f),PdfPoint(.4f,.3f))))) }
            }
            assertArrayEquals("Original PDF was modified",before,MessageDigest.getInstance("SHA-256").digest(source.readBytes()))
            PDDocument.load(exported).use { doc->
                assertEquals(2,doc.numberOfPages)
                assertTrue("Highlight missing from exported PDF",doc.getPage(0).annotations.any {it.subtype=="Highlight" && it.contents.contains("A test note")})
                assertTrue("Ink missing from exported PDF",doc.getPage(1).annotations.any {it.subtype=="Ink"})
            }
            PdfDocumentService(exported,context).use {service->
                assertTrue("Exported highlights cannot be restored on old Android",service.annotations(0).highlights.isNotEmpty())
                assertTrue("Exported ink cannot be restored on old Android",service.annotations(1).ink.isNotEmpty())
            }
        } finally {source.delete();exported.delete()}
    }
}
