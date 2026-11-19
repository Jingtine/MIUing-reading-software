package com.miuing.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val TextReaderChromeTopInset = 44.dp
val TextReaderChromeBottomInset = 48.dp

/** Pure overlays: showing controls never changes the reader's measured viewport. */
@Composable
fun TextReaderChrome(
    theme: ReadingTheme,
    chapter: Int,
    position: Float,
    pages: ReaderPageInfo?,
    scrolling: Boolean,
    onClose: () -> Unit,
    onMenu: () -> Unit,
    selectionActions: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(TextReaderChromeTopInset).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(48.dp))
            Text(
                if (!scrolling && pages != null) "本章还剩 ${pages.total - pages.current} 页" else "第 ${chapter + 1} 章",
                Modifier.weight(1f),
                color = theme.ink,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "返回书库", Modifier.size(28.dp).background(theme.ink.copy(alpha = .06f), CircleShape).padding(4.dp), tint = theme.ink.copy(alpha = .55f))
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(TextReaderChromeBottomInset).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(48.dp))
            val label = if (!scrolling && pages != null) "本章 ${pages.current} / ${pages.total}" else "第 ${chapter + 1} 章 · ${(position * 100).toInt()}%"
            val description = if (!scrolling && pages != null) "本章第 ${pages.current} 页，共 ${pages.total} 页" else "本章进度 ${(position * 100).toInt()}%"
            Text(
                label,
                Modifier.weight(1f).semantics { contentDescription = description },
                color = theme.ink,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, "阅读菜单", Modifier.size(30.dp).background(theme.ink.copy(alpha = .06f), CircleShape).padding(5.dp), tint = theme.ink)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = TextReaderChromeBottomInset + 4.dp)) { selectionActions() }
    }
}
