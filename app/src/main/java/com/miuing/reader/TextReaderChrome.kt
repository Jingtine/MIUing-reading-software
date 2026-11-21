package com.miuing.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val TextReaderChromeTopInset = 44.dp
val TextReaderChromeBottomInset = 48.dp

internal fun readerStatusColor(theme: ReadingTheme): Color {
    val minimum = ColorUtils.calculateMinimumAlpha(theme.ink.toArgb(),theme.paper.toArgb(),4.5f)
    return theme.ink.copy(alpha = maxOf(.55f,if(minimum>=0) minimum/255f else 1f))
}

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
    selectionActions: @Composable () -> Unit,
    onReturn: (() -> Unit)? = null,
    returnEnabled: Boolean = true,
    returnLabel: String = "返回"
) {
    val statusColor = remember(theme) { readerStatusColor(theme) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(TextReaderChromeTopInset).padding(horizontal = 12.dp),
        ) {
            Text(
                if (!scrolling && pages != null) "本章还剩 ${pages.total - pages.current} 页" else "第 ${chapter + 1} 章",
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 96.dp),
                color = statusColor,
                fontSize = 12.sp,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (onReturn != null) TextButton(onClick = onReturn, enabled = returnEnabled,
                modifier = Modifier.align(Alignment.CenterStart).widthIn(max = 96.dp).height(TextReaderChromeTopInset).semantics { contentDescription = "返回跳转前位置" },
                contentPadding = PaddingValues(horizontal = 4.dp), colors = ButtonDefaults.textButtonColors(contentColor = theme.ink)) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(14.dp).background(LocalContentColor.current,CircleShape).padding(2.dp), tint = theme.paper)
                Spacer(Modifier.width(4.dp))
                Text(returnLabel, Modifier.weight(1f,fill=false), fontSize=12.sp, fontWeight=FontWeight.Normal, letterSpacing=0.sp, maxLines=1, overflow=TextOverflow.Ellipsis)
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
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
                color = statusColor,
                fontSize = 12.sp,
                letterSpacing = 0.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = onMenu, modifier = Modifier.semantics { contentDescription = "阅读菜单" }) {
                Box(Modifier.size(30.dp).background(theme.ink.copy(alpha = .06f),RoundedCornerShape(8.dp)).padding(6.dp)) { ReaderMenuGlyph(theme.ink) }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = TextReaderChromeBottomInset + 4.dp)) { selectionActions() }
    }
}

@Composable private fun ReaderMenuGlyph(ink: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val unit = size.width / 18f
        repeat(2) { index -> val y=(3+5*index)*unit; drawLine(ink,Offset(2*unit,y),Offset(16*unit,y),1.8f*unit,StrokeCap.Round) }
        repeat(3) { index -> drawCircle(ink,1.25f*unit,Offset((3+6*index)*unit,14f*unit)) }
    }
}
