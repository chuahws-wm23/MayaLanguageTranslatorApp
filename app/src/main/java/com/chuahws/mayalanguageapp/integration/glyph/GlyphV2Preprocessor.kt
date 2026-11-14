package com.chuahws.mayalanguageapp.integration.glyph

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mirrors the pinned TWKM training preprocessing as closely as practical on Android:
 * grayscale -> Otsu dark-ink mask -> tight crop with margin -> 64x64 bright foreground
 * on black -> inverted ImageNet-normalized RGB tensor in NCHW order.
 * 224x224 remains the default for v2/v3; v4 uses 160x160.
 *
 * This is deliberately separate from TinyGlyphNet v1's preprocessing.
 */
internal object GlyphV2Preprocessor {
    const val SIDE = 224
    private const val CANONICAL = 64
    private val means = floatArrayOf(.485f, .456f, .406f)
    private val stds = floatArrayOf(.229f, .224f, .225f)

    fun fromBitmap(original: Bitmap, outputSide: Int = SIDE): FloatArray {
        require(original.width >= 25 && original.height >= 25) {
            "Crop is too small. Include one complete glyph."
        }
        val longest = max(original.width, original.height)
        val factor = min(1.0f, 1200.0f / longest.toFloat())
        val scaled = if (factor < 1f) {
            Bitmap.createScaledBitmap(
                original,
                max(25, (original.width * factor).roundToInt()),
                max(25, (original.height * factor).roundToInt()),
                true,
            )
        } else original
        try {
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            val gray = IntArray(pixels.size)
            for (i in pixels.indices) {
                val c = pixels[i]
                val light = (Color.red(c) * 77 + Color.green(c) * 150 +
                    Color.blue(c) * 29 + 128) shr 8
                val alpha = Color.alpha(c)
                gray[i] = (light * alpha + 255 * (255 - alpha) + 127) / 255
            }
            return fromGrayscale(gray, scaled.width, scaled.height, outputSide)
        } finally {
            if (scaled !== original) scaled.recycle()
        }
    }

    /** Pure numeric input supports independent JVM tests without an Android Bitmap. */
    fun fromGrayscale(grayInput: IntArray, width: Int, height: Int, outputSide: Int = SIDE): FloatArray {
        require(outputSide == 224 || outputSide == 160) { "Unsupported vision model image size." }
        require(width >= 25 && height >= 25 && grayInput.size == width * height)
        require(grayInput.all { it in 0..255 })
        val gray = grayInput.copyOf()
        val border = IntArray(256)
        for (x in 0 until width) {
            border[gray[x]]++
            border[gray[(height - 1) * width + x]]++
        }
        for (y in 0 until height) {
            border[gray[y * width]]++
            border[gray[y * width + width - 1]]++
        }
        val half = (width * 2 + height * 2) / 2
        var accumulated = 0
        var median = 255
        for (value in border.indices) {
            accumulated += border[value]
            if (accumulated >= half) { median = value; break }
        }
        if (median < 110) for (i in gray.indices) gray[i] = 255 - gray[i]
        val threshold = max(1, otsu(gray))

        var left = width
        var top = height
        var right = -1
        var bottom = -1
        var dark = 0
        for (y in 0 until height) for (x in 0 until width) {
            if (gray[y * width + x] < threshold) {
                dark++
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
            }
        }
        require(right >= left && dark.toFloat() / gray.size in 0.003f..0.85f) {
            "No clear isolated glyph detected. Crop to one dark drawing on light background."
        }
        val margin = max(2, (max(right - left + 1, bottom - top + 1) * .035f).toInt())
        left = max(0, left - margin)
        top = max(0, top - margin)
        right = min(width - 1, right + margin)
        bottom = min(height - 1, bottom + margin)
        val bw = right - left + 1
        val bh = bottom - top + 1
        val ink = ByteArray(bw * bh)
        for (y in 0 until bh) for (x in 0 until bw) {
            ink[y * bw + x] = if (gray[(y + top) * width + x + left] < threshold) 1 else 0
        }
        val ratio = 56.0 / max(bw, bh)
        val nw = max(1, (bw * ratio).roundToInt())
        val nh = max(1, (bh * ratio).roundToInt())
        val canonical = FloatArray(CANONICAL * CANONICAL)
        val offsetX = (CANONICAL - nw) / 2
        val offsetY = (CANONICAL - nh) / 2
        for (y in 0 until nh) for (x in 0 until nw) {
            // Area averaging approximates cv2.INTER_AREA, used in model training.
            val sx0 = x.toDouble() * bw / nw
            val sx1 = (x + 1).toDouble() * bw / nw
            val sy0 = y.toDouble() * bh / nh
            val sy1 = (y + 1).toDouble() * bh / nh
            var sum = 0.0
            for (iy in floor(sy0).toInt() until ceil(sy1).toInt()) {
                val dy = max(0.0, min(iy + 1.0, sy1) - max(iy.toDouble(), sy0))
                if (dy <= 0 || iy !in 0 until bh) continue
                for (ix in floor(sx0).toInt() until ceil(sx1).toInt()) {
                    val dx = max(0.0, min(ix + 1.0, sx1) - max(ix.toDouble(), sx0))
                    if (dx > 0 && ix in 0 until bw) {
                        sum += ink[iy * bw + ix].toInt() * dx * dy
                    }
                }
            }
            canonical[(y + offsetY) * CANONICAL + x + offsetX] =
                (sum / ((sx1 - sx0) * (sy1 - sy0))).toFloat().coerceIn(0f, 1f)
        }
        // Training upsamples 64x64 inverted glyphs to the target model size with bilinear interpolation.
        val channel = FloatArray(outputSide * outputSide)
        for (y in 0 until outputSide) for (x in 0 until outputSide) {
            val fx = (x + .5) * CANONICAL / outputSide - .5
            val fy = (y + .5) * CANONICAL / outputSide - .5
            val x0 = floor(fx).toInt().coerceIn(0, CANONICAL - 1)
            val y0 = floor(fy).toInt().coerceIn(0, CANONICAL - 1)
            val x1 = min(CANONICAL - 1, x0 + 1)
            val y1 = min(CANONICAL - 1, y0 + 1)
            val dx = fx.coerceIn(0.0, (CANONICAL - 1).toDouble()) - x0
            val dy = fy.coerceIn(0.0, (CANONICAL - 1).toDouble()) - y0
            val a = canonical[y0 * CANONICAL + x0]
            val b = canonical[y0 * CANONICAL + x1]
            val c = canonical[y1 * CANONICAL + x0]
            val d = canonical[y1 * CANONICAL + x1]
            val inkFraction = (a * (1.0 - dx) + b * dx) * (1.0 - dy) +
                (c * (1.0 - dx) + d * dx) * dy
            // cv2 uint8 resize quantizes to nearest 8-bit value.
            channel[y * outputSide + x] = (255 - (inkFraction * 255.0).roundToInt()).toFloat() / 255f
        }
        val output = FloatArray(3 * channel.size)
        for (c in 0..2) for (i in channel.indices) {
            output[c * channel.size + i] = (channel[i] - means[c]) / stds[c]
        }
        return output
    }

    private fun otsu(values: IntArray): Int {
        val counts = IntArray(256)
        for (v in values) counts[v]++
        val total = values.size.toDouble()
        var weighted = 0.0
        for (v in 0..255) weighted += v.toDouble() * counts[v]
        var backWeight = 0.0
        var backSum = 0.0
        var best = -1.0
        var threshold = 0
        for (v in 0..255) {
            backWeight += counts[v]
            if (backWeight == 0.0) continue
            val foreground = total - backWeight
            if (foreground == 0.0) break
            backSum += v.toDouble() * counts[v]
            val meanDiff = backSum / backWeight - (weighted - backSum) / foreground
            val between = backWeight * foreground * meanDiff * meanDiff
            if (between > best) {
                best = between
                threshold = v
            }
        }
        return threshold
    }
}
