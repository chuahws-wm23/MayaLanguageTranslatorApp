package com.chuahws.mayalanguageapp.integration.glyph

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Conservative visual warning for a dense *full-page* image. Complexity alone
 * cannot prove a Maya inscription; this avoids a subset of false one-sign
 * predictions and is not a trained sign classifier.
 */
internal object GlyphPanelGuard {
    fun looksLikeDensePanel(bitmap: Bitmap): Boolean {
        if (bitmap.width < 100 || bitmap.height < 100) return false
        val scale = minOf(1f, 320f / max(bitmap.width, bitmap.height))
        val copy = if (scale < 1f) Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        ) else bitmap
        try {
            val w = copy.width
            val h = copy.height
            val pixels = IntArray(w * h)
            copy.getPixels(pixels, 0, w, 0, 0, w, h)
            val gray = IntArray(pixels.size) { i ->
                val pixel = pixels[i]
                val luma = (Color.red(pixel) * 77 + Color.green(pixel) * 150 +
                    Color.blue(pixel) * 29 + 128) shr 8
                val alpha = Color.alpha(pixel)
                (luma * alpha + 255 * (255 - alpha) + 127) / 255
            }
            return looksLikeDensePanel(gray, w, h)
        } finally {
            if (copy !== bitmap) copy.recycle()
        }
    }

    fun looksLikeDensePanel(gray: IntArray, width: Int, height: Int): Boolean {
        require(width > 0 && height > 0 && gray.size == width * height)
        if (width < 100 || height < 100) return false
        require(gray.all { it in 0..255 })
        val hist = IntArray(256)
        for (v in gray) hist[v]++
        val n = gray.size.toDouble()
        var sum = 0.0
        for (i in 0..255) sum += i * hist[i].toDouble()
        var count = 0.0
        var weighted = 0.0
        var bestVariance = -1.0
        var threshold = 128
        for (i in 0..255) {
            count += hist[i]
            if (count == 0.0) continue
            if (count >= n) break
            weighted += i * hist[i].toDouble()
            val delta = weighted / count - (sum - weighted) / (n - count)
            val variance = count * (n - count) * delta * delta
            if (variance > bestVariance) {
                bestVariance = variance
                threshold = i
            }
        }
        if (bestVariance < 0) return false
        val black = BooleanArray(gray.size) { gray[it] <= threshold }
        var changes = 0L
        for (y in 0 until height) for (x in 0 until width) {
            val offset = y * width + x
            if (x > 0 && black[offset] != black[offset - 1]) changes++
            if (y > 0 && black[offset] != black[offset - width]) changes++
        }
        val possible = (width - 1L) * height + (height - 1L) * width
        if (changes.toDouble() / possible < .19) return false
        var occupied = 0
        for (r in 0 until 4) for (c in 0 until 4) {
            val x0 = c * width / 4
            val x1 = (c + 1) * width / 4
            val y0 = r * height / 4
            val y1 = (r + 1) * height / 4
            var dark = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                if (black[y * width + x]) dark++
            }
            val ratio = dark.toDouble() / ((x1 - x0) * (y1 - y0))
            if (ratio in .08..0.90) occupied++
        }
        return occupied >= 12
    }
}
