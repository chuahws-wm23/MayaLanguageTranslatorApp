package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphV18PostprocessorTest {
    private fun raw(n: Int) = Array(5) { FloatArray(n) }

    @Test fun letterboxCoordinatesRecoverSourceRectangle() {
        // 1000x500 content -> 512x256, pad Y=128.
        val p = raw(1)
        p[0][0] = 256f; p[1][0] = 256f
        p[2][0] = 256f; p[3][0] = 128f
        p[4][0] = .92f
        val got = GlyphV18Postprocessor.decode(p, 1000, 500, .512f,0f,128f)
        assertEquals(1,got.size)
        assertEquals(.25f,got.single().crop.left,.001f)
        assertEquals(.25f,got.single().crop.top,.001f)
        assertEquals(.75f,got.single().crop.right,.001f)
        assertEquals(.75f,got.single().crop.bottom,.001f)
    }

    @Test fun overlappingPredictionsAreNonMaxSuppressed() {
        val p = raw(3)
        for (i in 0..2) {
            p[0][i] = if(i==2) 405f else 128f
            p[1][i] = if(i==2) 405f else 128f
            p[2][i] = 95f; p[3][i] = 95f
        }
        p[4][0]=.9f; p[4][1]=.8f; p[4][2]=.7f
        val found = GlyphV18Postprocessor.decode(p, 512, 512,1f,0f,0f)
        assertEquals(2,found.size)
        assertEquals(.9f,found.first().detectionScore,.0001f)
    }

    @Test fun noDetectionsWhenBelowThreshold() {
        val p=raw(1)
        p[0][0]=256f;p[1][0]=256f;p[2][0]=80f;p[3][0]=80f;p[4][0]=.19f
        assertTrue(GlyphV18Postprocessor.decode(p,512,512,1f,0f,0f).isEmpty())
    }

    @Test fun invalidOrDifferentOutputLayoutIsRejected() {
        try {
            GlyphV18Postprocessor.decode(Array(6){FloatArray(2)},512,512,1f,0f,0f)
            fail("Expected one-class output validation failure")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("single-class"))
        }
    }

    @Test fun readingOrderIsGroupedIntoRowsThenColumns() {
        val boxes = listOf(
            GlyphV18Postprocessor.Box(GlyphCrop(.55f,.55f,.78f,.82f),.7f),
            GlyphV18Postprocessor.Box(GlyphCrop(.10f,.05f,.32f,.32f),.9f),
            GlyphV18Postprocessor.Box(GlyphCrop(.55f,.05f,.78f,.32f),.8f),
            GlyphV18Postprocessor.Box(GlyphCrop(.10f,.55f,.32f,.82f),.75f),
        )
        val order = GlyphV18Postprocessor.readingOrder(boxes)
        assertEquals(4,order.size)
        assertEquals(listOf(1,1,2,2),order.map{it.row})
        assertEquals(listOf(1,2,1,2),order.map{it.column})
        assertEquals(.10f,order.first().crop.left,.0001f)
        assertEquals(.55f,order.last().crop.left,.0001f)
    }
}
