package com.miuing.reader

import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter

class NotesDocumentTest {
    private val notes = listOf(
        Note(1, "后面的摘录", "批注 needle", 50, 55),
        Note(0, "旧版摘录", start = -1),
        Note(0, "前面的 Needle", start = 10, end = 14),
        Note(0, "前面的 Needle", start = 10, end = 14)
    )

    @Test fun groupingSortsSourceOffsetsWithoutLosingDuplicatesOrLegacyNotes() {
        val groups = noteGroups(notes, listOf("开篇", "结尾"))
        assertEquals(listOf(0, 1), groups.map { it.chapter })
        assertEquals(listOf(notes[2], notes[3], notes[1]), groups[0].notes)
        assertEquals("开篇", groups[0].label)
    }

    @Test fun queryAndKindFilterIntersectAcrossExcerptAnnotationAndChapter() {
        assertEquals(listOf(notes[0]), noteGroups(notes, emptyList(), query = " NEEDLE ", filter = NoteFilter.Notes).flatMap { it.notes })
        assertEquals(listOf(notes[2], notes[3]), noteGroups(notes, emptyList(), query = "needle", filter = NoteFilter.Highlights).flatMap { it.notes })
        assertEquals(3, noteGroups(notes, listOf("开篇", "结尾"), query = "开篇").single().notes.size)
        assertTrue(noteGroups(notes, emptyList(), query = "不存在").isEmpty())
        assertEquals(4, noteGroups(notes, emptyList(), query = " \n ").sumOf { it.notes.size })
    }

    @Test fun emptyChapterNamesPdfAndUnknownLocationsHaveHonestFallbacks() {
        assertEquals("第 2 章", noteGroups(listOf(notes[0]), listOf("a", " ")).single().label)
        assertEquals("第 2 页", noteGroups(listOf(notes[0]), listOf("a", "b"), pdf = true).single().label)
        assertEquals("未知位置", noteGroups(listOf(Note(-1, "x")), emptyList()).single().label)
        assertTrue(noteGroups(emptyList(), emptyList()).isEmpty())
    }

    private fun render(format: NotesFormat): String {
        val selected = listOf(NoteChapter(0, "章*节", listOf(Note(0, "首行\r\n\r\n> 引文 *字* [链](x) 😀", "笔记\r<script>\n末行"))))
        return StringWriter().also { NotesDocument("书#名\n换行", "作者", selected).write(it, format) }.toString()
    }

    @Test fun plainTextRetainsMultilineUnicodeAndSeparatesExcerptFromAnnotation() {
        val text = render(NotesFormat.Text)
        assertTrue(text.startsWith("书#名 换行\n作者：作者\n"))
        assertTrue(text.contains("摘录：\n首行\n\n> 引文 *字* [链](x) 😀"))
        assertTrue(text.contains("笔记：\n笔记\n<script>\n末行"))
        assertFalse(text.contains("创建时间")); assertFalse(text.contains('\r'))
    }

    @Test fun markdownEscapesLiteralMarkupAndQuotesEveryExcerptLine() {
        val text = render(NotesFormat.Markdown)
        assertTrue(text.startsWith("# 书\\#名 换行\n"))
        assertTrue(text.contains("## 章\\*节"))
        assertTrue(text.contains("> 首行\n> \n> \\> 引文 \\*字\\* \\[链\\]\\(x\\) 😀"))
        assertTrue(text.contains("\\<script\\>"))
        assertFalse(text.contains("<script>"))
        assertFalse(text.contains("第 0"))
    }

    @Test fun filenameIsSafeAndHasTheChosenExtension() {
        val document = NotesDocument("../书:名/?\\\n", "", emptyList())
        val name = document.filename(NotesFormat.Markdown)
        assertTrue(name.endsWith(".md"))
        assertFalse(name.any { it in "/\\:*?\"<>|\r\n" })
        assertTrue(NotesDocument("", "", emptyList()).filename(NotesFormat.Text).endsWith(".txt"))
    }
}
