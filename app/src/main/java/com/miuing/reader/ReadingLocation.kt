package com.miuing.reader

import androidx.compose.runtime.saveable.listSaver

internal data class ReadingLocation(val chapter: Int, val position: Float, val anchor: Int)

// Only primitive values survive Activity recreation; never retain a WebView or DOM node.
internal val ReadingHistorySaver = listSaver<List<ReadingLocation>, Any>(
    save = { history -> history.flatMap { listOf(it.chapter, it.position, it.anchor) } },
    restore = { values -> values.chunked(3).map { ReadingLocation(it[0] as Int, it[1] as Float, it[2] as Int) } }
)
