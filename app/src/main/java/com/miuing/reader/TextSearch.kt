package com.miuing.reader

import java.util.Locale

// Discovery and the DOM renderer use locale-independent lowercase plus
// equivalent whitespace; discovery offsets are only for snippets, never anchors.
internal fun textSearchSnippet(text: String, query: String): String? {
    val parts = query.trim().lowercase(Locale.ROOT).split(Regex("[\\s\\u00a0]+"))
    if (parts.all { it.isEmpty() }) return null
    val folded = text.lowercase(Locale.ROOT)
    val match = Regex(parts.joinToString("[\\s\\u00a0]+") { Regex.escape(it) }).find(folded) ?: return null
    fun originalOffset(offset: Int): Int {
        if (text.length == folded.length) return offset
        var original = 0
        var lower = 0
        while (original < text.length && lower < offset) {
            val length = Character.charCount(text.codePointAt(original))
            lower += text.substring(original, original + length).lowercase(Locale.ROOT).length
            original += length
        }
        return original
    }
    val start = originalOffset(match.range.first)
    val end = originalOffset(match.range.last + 1)
    return text.substring((start - 30).coerceAtLeast(0), (end + 75).coerceAtMost(text.length)).trim()
}
