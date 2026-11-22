package com.miuing.reader

import org.junit.Assert.*
import org.junit.Test

class BookPageMapTest {
    @Test fun prefixRangesAndTargetsUseMeasuredPhysicalPages() {
        val map=BookPageMap(listOf(2,5,3),1)
        assertEquals(10,map.total)
        assertEquals(BookPageInfo(3,3,10),map.locate(1,ChapterPageRange(1,1,5)))
        assertEquals(BookPageTarget(1,.5f),map.target(5))
        assertEquals(BookPageTarget(2,1f),map.target(10))
        assertNull(map.target(0));assertNull(map.target(11))
    }
    @Test fun partialAndStaleChapterRangesCannotInventWholeBookPages() {
        assertNull(BookPageMap(listOf(2,0,3),1).total)
        assertNull(BookPageMap(listOf(2,0,3),1).target(1))
        assertNull(BookPageMap(listOf(2,5,3),1).locate(1,ChapterPageRange(1,1,6)))
        assertNull(BookPageMap(listOf(Int.MAX_VALUE,1),1).total)
    }
    @Test fun doubleColumnMapsEitherVisiblePageToTheSameSpreadAndEndTo100Percent() {
        val map=BookPageMap(listOf(3,4),2)
        assertEquals(BookPageInfo(1,2,7),map.locate(0,ChapterPageRange(1,2,3)))
        assertEquals(BookPageInfo(3,3,7),map.locate(0,ChapterPageRange(3,3,3)))
        assertEquals(BookPageTarget(0,1f),map.target(3))
        assertEquals(map.target(4),map.target(5))
        assertEquals(1f,map.locate(1,ChapterPageRange(3,4,4))!!.progress,0f)
    }
}
