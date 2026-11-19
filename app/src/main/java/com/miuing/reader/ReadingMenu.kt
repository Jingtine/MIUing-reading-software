package com.miuing.reader

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ReadingMenu(
    title: String,
    theme: ReadingTheme,
    chapter: Int,
    chapterCount: Int,
    position: Float,
    bookmarked: Boolean,
    onDismiss: () -> Unit,
    onPanel: (ReaderPanel) -> Unit,
    onThemes: () -> Unit,
    onBookmark: () -> Unit,
    onChapter: (Int) -> Unit,
    onSeek: (Float) -> Unit
) {
    var preview by rememberSaveable(chapter) { mutableFloatStateOf(position) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onDismiss))
            Surface(
                color = theme.paper,
                contentColor = theme.ink,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 12.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    .width(minOf(360.dp, maxWidth - 24.dp)).heightIn(max = maxHeight * .92f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭阅读菜单") }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        MenuAction(Icons.AutoMirrored.Filled.List, "目录", "目录与书签") { onPanel(ReaderPanel.Contents) }
                        MenuAction(Icons.Default.TextFields, "主题与设置") { onThemes() }
                        MenuAction(Icons.Default.Search, "在图书中搜索") { onPanel(ReaderPanel.Search) }
                        MenuAction(if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, if (bookmarked) "移除书签" else "添加书签", onClick = onBookmark)
                        MenuAction(Icons.Default.EditNote, "高亮标记和笔记") { onPanel(ReaderPanel.Notes) }
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = theme.ink.copy(alpha = .12f))
                        Text("第 ${chapter + 1} / ${chapterCount.coerceAtLeast(1)} 章 · ${(preview * 100).toInt()}%", fontSize = 12.sp, color = theme.ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onChapter(chapter - 1) }, enabled = chapter > 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一章") }
                            Slider(
                                value = preview,
                                onValueChange = { preview = it },
                                onValueChangeFinished = { onSeek(preview) },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "阅读进度" },
                                colors = SliderDefaults.colors(thumbColor = theme.ink, activeTrackColor = theme.ink, inactiveTrackColor = theme.ink.copy(alpha = .12f))
                            )
                            IconButton(onClick = { onChapter(chapter + 1) }, enabled = chapter + 1 < chapterCount) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一章") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuAction(icon: ImageVector, label: String, description: String = label, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).semantics { contentDescription = description }.padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp)
    }
}
