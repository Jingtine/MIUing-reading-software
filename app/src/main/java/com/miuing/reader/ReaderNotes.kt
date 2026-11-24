package com.miuing.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable internal fun ReaderNotes(
    notes: List<Note>, chapterTitles: List<String>, pdf: Boolean, exportBusy: Boolean,
    onExport: (List<NoteChapter>, NotesFormat) -> Unit,
    onNavigate: (Note) -> Unit, onEdit: (Note) -> Unit, onRemove: (Note) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(NoteFilter.All) }
    var exportMenu by remember { mutableStateOf(false) }
    val groups = remember(notes, chapterTitles, pdf, query, filter) { noteGroups(notes, chapterTitles, pdf, query, filter) }
    val count = groups.sumOf { it.notes.size }
    Column {
        OutlinedTextField(query, { query = it.take(256) }, label = { Text("搜索摘录、笔记或章节") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NoteFilter.entries.forEach { kind ->
                FilterChip(selected = filter == kind, onClick = { filter = kind }, label = { Text(kind.label) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$count / ${notes.size} 条", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 16.dp))
            Box {
                TextButton(onClick = { exportMenu = true }, enabled = count > 0 && !exportBusy) { Text(if (exportBusy) "正在导出…" else "导出筛选结果") }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    NotesFormat.entries.forEach { format ->
                        DropdownMenuItem(text = { Text(format.label) }, onClick = { exportMenu = false; onExport(groups, format) })
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f, fill = false)) {
            if (groups.isEmpty()) item {
                Text(if (notes.isEmpty()) "选择正文中的文字以添加高亮标记或笔记。" else "没有符合条件的高亮标记或笔记", Modifier.padding(vertical = 16.dp))
            }
            groups.forEach { group ->
                item { Text(group.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 12.dp)) }
                items(group.notes) { note ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Text(note.text, Modifier.fillMaxWidth().background(Color(0xFFEBCF67).copy(alpha = .35f)).padding(8.dp))
                        if (note.note.isNotBlank()) Text(note.note, Modifier.padding(top = 8.dp))
                        Row {
                            TextButton(onClick = { onNavigate(note) }) { Text("前往原文") }
                            TextButton(onClick = { onEdit(note) }) { Text("编辑") }
                            TextButton(onClick = { onRemove(note) }) { Text("移除") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
