package com.chuahws.mayalanguageapp.integration.glyph

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max

/** Pure JVM inference for the supplied TinyGlyphNet v1 weights. No model download. */
class GlyphNetwork private constructor(private val layers: List<Layer>) {
    data class Candidate(val id: String, val reading: String, val english: String, val score: Float)
    data class Result(val suggested: Candidate?, val alternatives: List<Candidate>) {
        val status: String get() = if (suggested == null) "NEEDS_REVIEW" else "POSSIBLE_MATCH_UNVERIFIED"
    }
    private data class Layer(
        val type: Int, val outputs: Int, val inputs: Int, val kh: Int, val kw: Int,
        val stride: Int, val padding: Int, val scales: FloatArray,
        val biases: FloatArray, val weights: ByteArray,
    )
    companion object {
        const val INPUT_SIZE = 64
        const val MODEL_BYTES = 29_420
        const val MODEL_SHA256 = "d4ed697fbb7dd3552f6efac0e58e6c29e872b9b60832dba8d5f6a19bd94a4094"
        private val ids = listOf("DEER", "HAWK", "JAGUAR", "STAR", "KAKAW", "OTHER")
        private val readings = listOf("CHIJ", "MUWAAN", "HIX", "EK'", "KAKAW", "—")
        private val glosses = listOf("Deer", "Hawk", "Jaguar", "Star", "Cacao / Cocoa", "Other / unsupported")
        private val architecture = listOf(
            intArrayOf(1, 8, 1, 5, 5, 2, 2), intArrayOf(1, 16, 8, 3, 3, 2, 1),
            intArrayOf(1, 24, 16, 3, 3, 2, 1), intArrayOf(1, 32, 24, 3, 3, 2, 1),
            intArrayOf(2, 32, 512, 1, 1, 0, 0), intArrayOf(2, 6, 32, 1, 1, 0, 0),
        )

        fun fromBytes(data: ByteArray): GlyphNetwork {
            require(data.size == MODEL_BYTES) { "Glyph model is incomplete or has the wrong version." }
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(data)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            require(digest == MODEL_SHA256) { "Glyph model integrity check failed." }
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(8).also { buf.get(it) }
            require(magic.contentEquals(byteArrayOf(77, 71, 76, 89, 80, 72, 49, 0)))
            require(buf.int == architecture.size)
            val layers = architecture.map { expected ->
                val header = IntArray(7) { buf.int }
                require(header.contentEquals(expected)) { "Unsupported glyph network architecture." }
                val (type, outputs, inputs, kh, kw) = header
                val scales = FloatArray(outputs) { buf.float }
                val biases = FloatArray(outputs) { buf.float }
                require(scales.all { it.isFinite() && it > 0f } && biases.all { it.isFinite() })
                val weights = ByteArray(outputs * inputs * kh * kw).also { buf.get(it) }
                Layer(type, outputs, inputs, kh, kw, header[5], header[6], scales, biases, weights)
            }
            require(!buf.hasRemaining())
            return GlyphNetwork(layers)
        }

        /** Screening gates only. A score is not calibrated recognition accuracy. */
        fun interpret(scores: FloatArray): Result {
            require(scores.size == ids.size && scores.all { it.isFinite() && it in 0f..1f })
            val ranks = scores.indices.sortedByDescending { scores[it] }
            fun candidate(i: Int) = Candidate(ids[i], readings[i], glosses[i], scores[i])
            val best = ranks[0]
            val plausible = best != 5 && scores[best] >= .90f && scores[best] - scores[ranks[1]] >= .25f
            return Result(if (plausible) candidate(best) else null, ranks.take(3).map(::candidate))
        }
    }

    fun recognize(input: FloatArray): Result = interpret(predict(input))

    /** NCHW order: foreground 1, background 0. Input must match the preprocessing contract. */
    fun predict(input: FloatArray): FloatArray {
        require(input.size == INPUT_SIZE * INPUT_SIZE && input.all { it.isFinite() && it in 0f..1f })
        var z = input
        var height = INPUT_SIZE
        var width = INPUT_SIZE
        layers.forEachIndexed { index, l ->
            if (l.type == 1) {
                require(z.size == l.inputs * height * width)
                val oh = (height + 2 * l.padding - l.kh) / l.stride + 1
                val ow = (width + 2 * l.padding - l.kw) / l.stride + 1
                val out = FloatArray(l.outputs * oh * ow)
                for (oc in 0 until l.outputs) for (y in 0 until oh) for (x in 0 until ow) {
                    var sum = 0f
                    for (ic in 0 until l.inputs) for (ky in 0 until l.kh) {
                        val iy = y * l.stride + ky - l.padding
                        if (iy !in 0 until height) continue
                        for (kx in 0 until l.kw) {
                            val ix = x * l.stride + kx - l.padding
                            if (ix !in 0 until width) continue
                            val wi = ((oc * l.inputs + ic) * l.kh + ky) * l.kw + kx
                            sum += l.weights[wi].toInt() * z[(ic * height + iy) * width + ix]
                        }
                    }
                    out[(oc * oh + y) * ow + x] = max(0f, sum * l.scales[oc] + l.biases[oc])
                }
                z = out; height = oh; width = ow
            } else {
                require(z.size == l.inputs)
                z = FloatArray(l.outputs) { oc ->
                    var sum = 0f
                    for (i in z.indices) sum += l.weights[oc * l.inputs + i].toInt() * z[i]
                    val value = sum * l.scales[oc] + l.biases[oc]
                    if (index == layers.lastIndex) value else max(0f, value)
                }
            }
        }
        val largest = z.maxOrNull() ?: error("Empty glyph output")
        val values = DoubleArray(z.size) { exp((z[it] - largest).toDouble()) }
        val sum = values.sum()
        return FloatArray(z.size) { (values[it] / sum).toFloat() }
    }
}
