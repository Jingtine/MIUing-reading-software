package com.miuing.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuing.core.Publication

@Composable
fun ReaderContents(entries: List<Publication.Navigation>, chapter: Int, ink: Color, onNavigate: (Publication.Navigation) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        items(entries) { entry ->
            TextButton(onClick = { onNavigate(entry) }, enabled = entry.chapter >= 0, modifier = Modifier.padding(start = (entry.depth * 16).dp)) {
                Text(entry.title, color = if (entry.chapter == chapter) Accent else ink)
            }
        }
    }
}

@Composable
fun PdfContentsPanel(entries: List<PdfOutline>, problem: String?, onGoToPage: () -> Unit, onNavigate: (Int) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        item { TextButton(onClick = onGoToPage) { Text("前往页码") } }
        items(entries) { entry ->
            TextButton(onClick = { onNavigate(entry.page) }, modifier = Modifier.padding(start = (entry.depth * 16).dp)) { Text(entry.title) }
        }
        if (entries.isEmpty()) item { Text(problem ?: "此 PDF 没有内嵌目录", Modifier.padding(vertical = 12.dp), fontSize = 13.sp) }
    }
}

@Composable
fun ReaderBookmarks(marks: List<Mark>, onNavigate: (Mark) -> Unit, onRemove: (Mark) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        if (marks.isEmpty()) item { Text("没有书签", Modifier.padding(16.dp)) }
        items(marks) { mark ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onNavigate(mark) }, modifier = Modifier.weight(1f)) { Text(mark.label) }
                IconButton(onClick = { onRemove(mark) }) { Icon(Icons.Default.DeleteOutline, "移除书签") }
            }
        }
    }
}

@Composable
fun ReaderNotes(notes: List<Note>, onNavigate: (Note) -> Unit, onEdit: (Note) -> Unit, onRemove: (Note) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        if (notes.isEmpty()) item { Text("选择正文中的文字以添加高亮标记或笔记。", Modifier.padding(16.dp)) }
        items(notes) { note ->
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(note.text, Modifier.background(Color(0xFFEBCF67).copy(alpha = .35f)).padding(8.dp))
                if (note.note.isNotBlank()) Text(note.note)
                Row {
                    TextButton(onClick = { onNavigate(note) }) { Text("前往原文") }
                    TextButton(onClick = { onEdit(note) }) { Text("编辑") }
                    TextButton(onClick = { onRemove(note) }) { Text("移除") }
                }
            }
        }
    }
}

@Composable
fun ReaderSearchPanel(
    query: String,
    results: List<Pair<Int, String>>,
    pdfHits: List<PdfHit>,
    searching: Boolean,
    problem: String?,
    onQuery: (String) -> Unit,
    onResult: (Int) -> Unit,
    onPdfHit: (PdfHit) -> Unit
) {
    OutlinedTextField(query, onQuery, label = { Text("在图书中搜索") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    LazyColumn(Modifier.heightIn(max = 360.dp)) {
        items(pdfHits) { hit ->
            TextButton(onClick = { onPdfHit(hit) }, modifier = Modifier.semantics { contentDescription = "PDF 搜索结果 第 ${hit.page + 1} 页" }) { Text("第 ${hit.page + 1} 页 · ${hit.snippet}") }
        }
        items(results) { result -> TextButton(onClick = { onResult(result.first) }, modifier = Modifier.semantics { contentDescription = "文字搜索结果 第 ${result.first + 1} 章" }) { Text(result.second) } }
        if (searching) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
        if (problem != null) item { Text(problem, Modifier.padding(vertical = 16.dp)) }
        else if (!searching && query.isNotBlank() && results.isEmpty() && pdfHits.isEmpty()) item { Text("没有找到匹配内容", Modifier.padding(vertical = 20.dp)) }
    }
}

@Composable
fun ReaderPageJump(pageCount: Int?, onNavigate: (Int) -> Unit) {
    var page by remember { mutableStateOf("") }
    OutlinedTextField(page, { page = it.filter(Char::isDigit) }, label = { Text("页码") }, singleLine = true)
    TextButton(onClick = { page.toIntOrNull()?.let { onNavigate((it - 1).coerceIn(0, (pageCount ?: 1) - 1)) } }, enabled = pageCount != null) { Text("前往") }
}

@Composable
fun ReaderNoteEditor(selection: String, text: String, onText: (String) -> Unit, onSave: () -> Unit) {
    Text(selection.take(300), fontSize = 14.sp)
    OutlinedTextField(text, onText, label = { Text("笔记") }, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = onSave) { Text("完成") }
}
