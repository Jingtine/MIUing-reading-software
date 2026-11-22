package com.miuing.reader

import kotlin.math.ceil

data class ChapterPageRange(val first:Int,val last:Int,val total:Int) {
    val valid get()=total>0 && first in 1..total && last in first..total
    fun position(columns:Int):Float {
        val spreads=ceil(total.toDouble()/columns).toInt()
        return if(spreads<=1) 0f else ((first-1)/columns).toFloat()/(spreads-1)
    }
}
data class BookPageInfo(val first:Int,val last:Int,val total:Int) {
    val progress:Float get()=if(last>=total || total<=1) 1f else (first-1f)/(total-1)
}
data class BookPageTarget(val chapter:Int,val position:Float)

/** A derived, immutable view of measured pages. Unknown chapters never become estimates. */
class BookPageMap(counts:List<Int>,val columns:Int) {
    val counts=counts.toList()
    private val offsets=LongArray(counts.size+1).apply {for(i in counts.indices) this[i+1]=this[i]+counts[i].coerceAtLeast(0)}
    val total:Int?=offsets.last().takeIf {columns in 1..2 && counts.isNotEmpty() && counts.all {it>0} && it in 1..Int.MAX_VALUE.toLong()}?.toInt()
    fun locate(chapter:Int,range:ChapterPageRange):BookPageInfo? {
        val sum=total ?: return null
        if(chapter !in counts.indices || !range.valid || range.total!=counts[chapter]) return null
        return BookPageInfo(offsets[chapter].toInt()+range.first,offsets[chapter].toInt()+range.last,sum)
    }
    fun target(page:Int):BookPageTarget? {
        val sum=total ?: return null
        if(page !in 1..sum) return null
        val chapter=counts.indices.first {page<=offsets[it+1]}
        val local=page-offsets[chapter].toInt()
        return BookPageTarget(chapter,ChapterPageRange(local,local,counts[chapter]).position(columns))
    }
}
