package com.miuing.reader

import java.io.Writer

internal enum class NoteFilter(val label: String) { All("全部"), Highlights("仅高亮"), Notes("有笔记") }
internal enum class NotesFormat(val extension: String, val mime: String, val label: String) {
    Text("txt", "text/plain", "TXT"), Markdown("md", "text/markdown", "Markdown")
}
internal data class NoteChapter(val chapter: Int, val label: String, val notes: List<Note>)

/** Same immutable selection feeds the panel and export; legacy offsets retain their input order. */
internal fun noteGroups(
    notes: List<Note>, chapterTitles: List<String>, pdf: Boolean = false,
    query: String = "", filter: NoteFilter = NoteFilter.All
): List<NoteChapter> {
    val term = query.trim()
    return notes.groupBy { it.chapter }.toSortedMap().mapNotNull { (chapter, entries) ->
        val label = when {
            chapter < 0 -> "未知位置"
            pdf -> "第 ${chapter + 1} 页"
            else -> chapterTitles.getOrNull(chapter)?.trim()?.takeIf { it.isNotEmpty() } ?: "第 ${chapter + 1} 章"
        }
        val selected = entries.filter { note ->
            val annotated = note.note.isNotBlank()
            (filter == NoteFilter.All || (filter == NoteFilter.Notes) == annotated) &&
                (term.isEmpty() || listOf(label, note.text, note.note).any { it.contains(term, ignoreCase = true) })
        }.sortedBy { if (it.start >= 0) it.start else Int.MAX_VALUE }
        if (selected.isEmpty()) null else NoteChapter(chapter, label, selected)
    }
}

/** Plain file formatting only. No renderer, database conversion or synthesized note metadata. */
internal data class NotesDocument(val title: String, val author: String, val chapters: List<NoteChapter>) {
    val count: Int get() = chapters.sumOf { it.notes.size }

    fun filename(format: NotesFormat): String {
        val safe = title.map { if (it.isISOControl() || it in "/\\:*?\"<>|") '_' else it }
            .joinToString("").trim(' ', '.').take(80).trimEnd { it.isHighSurrogate() }
            .ifBlank { "图书" }
        return "$safe-阅读笔记.${format.extension}"
    }

    fun write(out: Writer, format: NotesFormat) {
        val markdown = format == NotesFormat.Markdown
        fun literal(value: String) = if (markdown) escapeMarkdown(value) else value
        fun heading(value: String) = literal(lines(value).joinToString(" "))
        out.write((if (markdown) "# " else "") + heading(title) + "\n")
        if (author.isNotBlank()) out.write((if (markdown) "\n" else "") + "作者：${heading(author)}\n")
        out.write("\n共 $count 条高亮标记和笔记\n")
        chapters.forEach { group ->
            out.write("\n" + (if (markdown) "## " else "") + heading(group.label) + "\n")
            group.notes.forEach { note ->
                out.write("\n" + (if (markdown) "---\n\n" else "────────\n"))
                if (markdown) out.write(lines(note.text).joinToString("\n") { "> ${literal(it)}" } + "\n")
                else out.write("摘录：\n${lines(note.text).joinToString("\n")}\n")
                if (note.note.isNotBlank()) {
                    out.write(if (markdown) "\n**笔记：**\n\n" else "\n笔记：\n")
                    out.write(lines(note.note).joinToString(if (markdown) "  \n" else "\n", transform = ::literal) + "\n")
                }
            }
        }
    }
}

private fun lines(value: String) = value.replace("\r\n", "\n").replace('\r', '\n').split('\n')
private fun escapeMarkdown(value: String) = buildString {
    value.forEach { char ->
        if (char in '!'..'/' || char in ':'..'@' || char in '['..'`' || char in '{'..'~') append('\\')
        append(char)
    }
}
