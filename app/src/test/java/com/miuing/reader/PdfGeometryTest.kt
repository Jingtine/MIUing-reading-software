package com.miuing.reader

import org.junit.Assert.*
import org.junit.Test

class PdfGeometryTest {
    @Test fun mapsCroppedRotatedPageCornersAndRoundTrips() {
        val cases=listOf(
            Triple(0,PdfPoint(10f,820f),PdfPoint(160f,220f)),
            Triple(90,PdfPoint(10f,20f),PdfPoint(460f,220f)),
            Triple(180,PdfPoint(610f,20f),PdfPoint(460f,620f)),
            Triple(270,PdfPoint(610f,820f),PdfPoint(160f,620f))
        )
        for((rotation,corner,expected) in cases) {
            val geometry=PdfGeometry(600f,800f,10f,20f,rotation)
            assertEquals("Corner x, rotation $rotation",corner.x,geometry.toPdf(PdfPoint(0f,0f)).x,.001f)
            assertEquals("Corner y, rotation $rotation",corner.y,geometry.toPdf(PdfPoint(0f,0f)).y,.001f)
            val actual=geometry.toPdf(PdfPoint(.25f,.75f))
            assertEquals(expected.x,actual.x,.001f);assertEquals(expected.y,actual.y,.001f)
            val restored=geometry.toDisplay(actual)
            assertEquals(.25f,restored.x,.001f);assertEquals(.75f,restored.y,.001f)
        }
    }
}
