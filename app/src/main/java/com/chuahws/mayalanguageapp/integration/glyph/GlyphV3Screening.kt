package com.chuahws.mayalanguageapp.integration.glyph

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.max

/**
 * Open-set screening for the five supported v2 sign categories.
 *
 * It compares one unit-normalized MobileNetV3 feature vector to the 38 original
 * catalog feature vectors, accepting the predicted sign only if it is close to
 * an example of that sign AND distinctly further from other sign examples.
 *
 * These thresholds were calibrated within TWKM; they do NOT guarantee detection
 * of unfamiliar handwriting, phone photographs or unseen archaeological styles.
 */
internal class GlyphV3Screening private constructor(
    private val labels: IntArray,
    private val prototypes: FloatArray,
    private val dimension: Int,
) {
    data class Verdict(
        val accepted: Boolean,
        val nearestOwnSignSimilarity: Float,
        val nearestOtherSignMargin: Float,
    )

    companion object {
        const val MARGIN_THRESHOLD = 0.05f
        const val SIMILARITY_THRESHOLD = 0.89f
        private const val EXPECTED_ROWS = 38
        private const val EXPECTED_DIM = 576
        private const val FILE_NAME = "maya_gallery_v3.bin"
        private const val SHA256 =
            "84c93ee2d9c09ee99845895630a2710c1bab0c2b428c9d6a4e2d774311c1871e"

        @Volatile private var cached: GlyphV3Screening? = null

        fun fromAssets(context: Context): GlyphV3Screening =
            cached ?: synchronized(this) {
                cached ?: parse(
                    context.applicationContext.assets.open(FILE_NAME).use { it.readBytes() }
                ).also { cached = it }
            }

        private fun parse(raw: ByteArray): GlyphV3Screening {
            val digest = MessageDigest.getInstance("SHA-256").digest(raw)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            require(digest == SHA256) { "Glyph screening references failed integrity verification." }
            val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val rows = buffer.int
            val dim = buffer.int
            require(rows == EXPECTED_ROWS && dim == EXPECTED_DIM)
            require(raw.size == 8 + rows + rows * dim * 4)
            val classes = IntArray(rows) { buffer.get().toInt() and 255 }
            require(classes.all { it in 0..4 })
            require((0..4).all { it in classes })
            val data = FloatArray(rows * dim) { buffer.float }
            require(!buffer.hasRemaining() && data.all { it.isFinite() })
            return GlyphV3Screening(classes, data, dim)
        }

        /** Test-only data factory; never bypass the asset SHA verification in production. */
        internal fun forTesting(labels: IntArray, vectors: Array<FloatArray>): GlyphV3Screening {
            require(labels.isNotEmpty() && labels.size == vectors.size)
            val dim = vectors[0].size
            require(dim > 0 && vectors.all { it.size == dim && it.all(Float::isFinite) })
            return GlyphV3Screening(labels.copyOf(), vectors.flatMap { it.asIterable() }.toFloatArray(), dim)
        }
    }

    fun screen(features: FloatArray, predictedClass: Int): Verdict {
        require(predictedClass in 0..4 && features.size == dimension)
        require(features.all { it.isFinite() })
        var same = Float.NEGATIVE_INFINITY
        var different = Float.NEGATIVE_INFINITY
        for (row in labels.indices) {
            var similarity = 0.0f
            val offset = row * dimension
            for (i in features.indices) {
                similarity += features[i] * prototypes[offset + i]
            }
            if (labels[row] == predictedClass) {
                same = max(same, similarity)
            } else {
                different = max(different, similarity)
            }
        }
        require(same.isFinite() && different.isFinite()) {
            "Screening references are missing an expected class."
        }
        val gap = same - different
        return Verdict(
            accepted = same >= SIMILARITY_THRESHOLD && gap >= MARGIN_THRESHOLD,
            nearestOwnSignSimilarity = same,
            nearestOtherSignMargin = gap,
        )
    }
}
