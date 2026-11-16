package com.chuahws.mayalanguageapp.integration.glyph

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/** Capture hints only. Heuristic limits are NOT a validated glyph/unknown detector. */
internal object GlyphImageQuality {
    fun hints(image: Bitmap): List<String> {
        val factor = minOf(1f, 256f / maxOf(image.width, image.height))
        val scaled = if (factor < 1f) Bitmap.createScaledBitmap(image,
            maxOf(1, (image.width * factor).roundToInt()),
            maxOf(1, (image.height * factor).roundToInt()), true) else image
        return try {
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            val gray = IntArray(pixels.size) { i ->
                val p = pixels[i]
                val lum = (Color.red(p) * 77 + Color.green(p) * 150 + Color.blue(p) * 29 + 128) shr 8
                (lum * Color.alpha(p) + 255 * (255 - Color.alpha(p)) + 127) / 255
            }
            assess(gray, scaled.width, scaled.height)
        } finally { if (scaled !== image) scaled.recycle() }
    }

    fun assess(gray: IntArray, width: Int, height: Int): List<String> {
        require(width > 0 && height > 0 && gray.size == width * height && gray.all { it in 0..255 })
        val hints = mutableListOf<String>()
        if (minOf(width, height) < 64) hints += "This crop is small. A higher-resolution image may preserve more detail."
        val sorted = gray.sorted()
        val low = sorted[sorted.size / 20]
        val high = sorted[(sorted.size - 1) * 19 / 20]
        if (high - low < 25) hints += "Contrast is low. Try even lighting with the sign clearly separated from its background."
        if (high < 65) hints += "The image is dark. Add soft, even light without reflections."
        var energy = 0.0
        var count = 0
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val i = y * width + x
            val laplacian = 4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - width] - gray[i + width]
            energy += laplacian.toDouble() * laplacian
            count++
        }
        if (count > 0 && energy / count < 20.0)
            hints += "Few sharp edges are visible. Check focus and keep the camera steady."
        return hints
    }
}
