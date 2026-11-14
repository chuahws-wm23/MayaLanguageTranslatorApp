package com.chuahws.mayalanguageapp.integration.glyph

import kotlin.math.roundToInt

/**
 * Optional illumination correction for RESEARCH V4 photo inference.
 *
 * Removes slow-moving shadows before the unchanged catalogue-style ink
 * segmentation. It is NOT a new trained model or a sign-recognition method.
 * Pure numeric so synthetic-photo behaviour can be unit-tested on the JVM.
 */
internal object GlyphV4PhotoNormalizer {
    fun correct(gray: IntArray, width: Int, height: Int): IntArray {
        require(width >= 25 && height >= 25 && gray.size == width * height)
        require(gray.all { it in 0..255 })

        // Match the existing glyph pipeline's dark-background convention.
        // A white symbol on black should become dark ink on a light background.
        val borderHistogram = IntArray(256)
        for (x in 0 until width) {
            borderHistogram[gray[x]]++
            borderHistogram[gray[(height - 1) * width + x]]++
        }
        for (y in 0 until height) {
            borderHistogram[gray[y * width]]++
            borderHistogram[gray[y * width + width - 1]]++
        }
        val borderCount = 2 * (width + height)
        var running = 0
        var median = 255
        for (v in 0..255) {
            running += borderHistogram[v]
            if (running >= borderCount / 2) {
                median = v
                break
            }
        }
        val source = if (median < 110) IntArray(gray.size) { 255 - gray[it] } else gray

        // Integral-image local mean, linear time in the number of pixels.
        // A wide radius preserves sign strokes while cancelling lighting gradients.
        val radius = (minOf(width, height) / 6).coerceIn(12, 96)
        val stride = width + 1
        val sums = LongArray(stride * (height + 1))
        for (y in 1..height) {
            var rowSum = 0L
            for (x in 1..width) {
                rowSum += source[(y - 1) * width + x - 1]
                sums[y * stride + x] = sums[(y - 1) * stride + x] + rowSum
            }
        }
        val corrected = IntArray(source.size)
        for (y in 0 until height) {
            val top = (y - radius).coerceAtLeast(0)
            val bottom = (y + radius + 1).coerceAtMost(height)
            for (x in 0 until width) {
                val left = (x - radius).coerceAtLeast(0)
                val right = (x + radius + 1).coerceAtMost(width)
                val total = sums[bottom * stride + right] -
                    sums[top * stride + right] -
                    sums[bottom * stride + left] +
                    sums[top * stride + left]
                val area = (right - left) * (bottom - top)
                val background = total.toDouble() / area
                corrected[y * width + x] =
                    (source[y * width + x] - background + 212.0)
                        .roundToInt().coerceIn(0, 255)
            }
        }
        return corrected
    }

    /**
     * Third *optional* view for isolated noisy photos. A 3x3 median removes
     * isolated camera noise but can also erase very thin marks, so the original
     * unsmoothed view remains authoritative in the v4 consensus.
     */
    fun medianDenoise(gray: IntArray, width: Int, height: Int): IntArray {
        require(width >= 25 && height >= 25 && gray.size == width * height)
        require(gray.all { it in 0..255 })
        val dst = gray.copyOf()
        val window = IntArray(9)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            var count = 0
            for (dy in -1..1) for (dx in -1..1) {
                window[count++] = gray[(y + dy) * width + x + dx]
            }
            window.sort()
            dst[y * width + x] = window[4]
        }
        return dst
    }
}
