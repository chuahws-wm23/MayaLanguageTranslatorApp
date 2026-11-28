package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

/** Tests synthetic ONNX tensors; not evidence of phone-photo accuracy. */
class GlyphV11SegPostprocessorTest {
    private fun predictions(n: Int = 1) = Array(37) { FloatArray(n) }
    private fun masks() = Array(32) { Array(128) { FloatArray(128) } }

    private fun addBox(p: Array<FloatArray>, at: Int = 0, x: Float=128f, y: Float=128f) {
        p[0][at]=x;p[1][at]=y;p[2][at]=128f;p[3][at]=128f;p[4][at]=.93f
        p[5][at]=1f
    }

    @Test fun maskBoundsNotJustBoxAreReturned() {
        val p = predictions()
        val m = masks()
        addBox(p)
        for (y in 22..39) for (x in 22..39) m[0][y][x]=3f
        val result = GlyphV11SegPostprocessor.decode(p,m,512,512,1f,0f,0f)
        assertEquals(1,result.size)
        // Input box spans x=64..192; fitted mask spans x approx 84..164.
        assertTrue(result.single().crop.left > .15f)
        assertTrue(result.single().crop.right < .34f)
        assertEquals(.93f,result.single().score,.001f)
    }

    @Test fun overlappingAnchorsAreSuppressed() {
        val p = predictions(2);val m=masks()
        addBox(p,0);addBox(p,1,x=129f,y=129f);p[4][1]=.72f
        for (y in 22..39) for (x in 22..39) m[0][y][x]=2f
        assertEquals(1,GlyphV11SegPostprocessor.decode(p,m,512,512,1f,0f,0f).size)
    }

    @Test fun emptyMaskDoesNotBecomeFakeGlyph() {
        val p=predictions();addBox(p)
        val got=GlyphV11SegPostprocessor.decode(p,masks(),512,512,1f,0f,0f)
        assertTrue(got.isEmpty())
    }

    @Test fun mustHaveOneClassAnd32MaskChannels() {
        try {
            GlyphV11SegPostprocessor.decode(
                Array(5){FloatArray(1)},masks(),512,512,1f,0f,0f)
            fail("Should reject v18 block detector layout")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("segmentation"))
        }
    }

    @Test fun sourceCoordinatesAreRecoveredAfterLetterbox() {
        val p=predictions();val m=masks()
        addBox(p,x=256f,y=256f);p[2][0]=150f;p[3][0]=150f
        for(y in 55..72) for(x in 55..72) m[0][y][x]=3f
        val got=GlyphV11SegPostprocessor.decode(
            p,m,1000,500,.512f,0f,128f).single().crop
        assertTrue(got.left in .35f.. .55f)
        assertTrue(got.top in .2f.. .7f)
        assertTrue(got.right > got.left)
    }
}
