package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphEmbeddingMathTest {
    @Test fun normalizationMakesCosineIndependentOfMagnitude() {
        val query = FloatArray(576) { if (it == 3) 7f else 0f }
        val unit = GlyphEmbeddingMath.normalize(query)
        assertEquals(1f, GlyphEmbeddingMath.cosine(unit, unit, 0), .000001f)
        assertEquals(1f, unit[3], .000001f)
    }

    @Test fun galleryOffsetsDoNotReturnAnotherReferencesVector() {
        val query = FloatArray(576) { if (it == 0) 1f else 0f }
        val gallery = FloatArray(1152)
        gallery[0] = 1f
        gallery[577] = 1f
        assertEquals(1f, GlyphEmbeddingMath.cosine(query, gallery, 0), 0f)
        assertEquals(0f, GlyphEmbeddingMath.cosine(query, gallery, 576), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroVectorCannotProduceConfidence() { GlyphEmbeddingMath.normalize(FloatArray(576)) }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteFeaturesAreRejected() { GlyphEmbeddingMath.normalize(floatArrayOf(Float.NaN)) }

    @Test fun tensorPreservesPolarityChannelOrderAndBitOrder() {
        val white = GlyphEmbeddingMath.tensor(ByteArray(512))
        assertEquals((1f - .485f) / .229f, white[0], .000001f)
        assertEquals((1f - .456f) / .224f, white[224 * 224], .000001f)
        val bits = ByteArray(512)
        bits[0] = 0x80.toByte()
        val tensor = GlyphEmbeddingMath.tensor(bits)
        assertEquals(-.485f / .229f, tensor[0], .000001f)
        assertEquals(white.last(), tensor.last(), .000001f)
    }
}
