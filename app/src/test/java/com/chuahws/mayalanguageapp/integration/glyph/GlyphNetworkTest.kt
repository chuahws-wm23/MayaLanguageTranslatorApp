package com.chuahws.mayalanguageapp.integration.glyph

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class GlyphNetworkTest {
    private fun bytes(): ByteArray {
        val paths = listOf("src/main/assets/maya_glyph_v1.mgl", "app/src/main/assets/maya_glyph_v1.mgl")
        return paths.map(::File).first { it.isFile }.readBytes()
    }
    @Test fun bundledModelHasExpectedSizeAndIntegrity() {
        assertEquals(29420, bytes().size)
        GlyphNetwork.fromBytes(bytes())
    }
    @Test(expected = IllegalArgumentException::class) fun missingModelIsRejected() {
        GlyphNetwork.fromBytes(byteArrayOf())
    }
    @Test(expected = IllegalArgumentException::class) fun modifiedWeightsAreRejected() {
        val data = bytes(); data[data.lastIndex] = (data.last().toInt() xor 1).toByte()
        GlyphNetwork.fromBytes(data)
    }
    @Test(expected = IllegalArgumentException::class) fun truncatedModelIsRejected() {
        GlyphNetwork.fromBytes(bytes().copyOf(100))
    }
    @Test fun inferenceReturnsSixFiniteNormalizedScores() {
        val network = GlyphNetwork.fromBytes(bytes())
        val input = FloatArray(4096) { if (it / 64 in 12..51 && it % 64 in 12..51) 1f else 0f }
        val scores = network.predict(input)
        assertEquals(6, scores.size)
        assertTrue(scores.all { it.isFinite() && it in 0f..1f })
        assertEquals(1f, scores.sum(), .00001f)
        assertArrayEquals(scores, network.predict(input), 0f)
    }
    @Test(expected = IllegalArgumentException::class) fun wrongInputDimensionsAreRejected() {
        GlyphNetwork.fromBytes(bytes()).predict(FloatArray(64))
    }
    @Test(expected = IllegalArgumentException::class) fun nonFiniteInputIsRejected() {
        GlyphNetwork.fromBytes(bytes()).predict(FloatArray(4096) { Float.NaN })
    }
    @Test fun unknownWinsEvenWithHighScore() {
        val result = GlyphNetwork.interpret(floatArrayOf(.01f, .01f, .01f, .01f, .01f, .95f))
        assertNull(result.suggested); assertEquals("NEEDS_REVIEW", result.status)
    }
    @Test fun lowScoreDoesNotBecomeTranslation() {
        assertNull(GlyphNetwork.interpret(floatArrayOf(.60f, .10f, .10f, .10f, .05f, .05f)).suggested)
    }
    @Test fun strongKnownMatchIsStillUnverified() {
        val result = GlyphNetwork.interpret(floatArrayOf(.01f, .01f, .01f, .01f, .95f, .01f))
        assertEquals("KAKAW", result.suggested?.id)
        assertEquals("POSSIBLE_MATCH_UNVERIFIED", result.status)
        assertEquals(3, result.alternatives.size)
    }
}
