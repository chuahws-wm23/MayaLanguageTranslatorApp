package com.chuahws.mayalanguageapp.integration.glyph

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/** A second look for a light-background drawing with detached, smaller text below. */
internal object GlyphCaptionCrop {
    fun find(bitmap: Bitmap): GlyphCrop? {
        val scale = minOf(1f, 512f / maxOf(bitmap.width, bitmap.height))
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        if (minOf(width, height) < 64) return null
        val sampled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, width, height, true) else bitmap
        val pixels = IntArray(width * height)
        try {
            sampled.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            if (sampled !== bitmap) sampled.recycle()
        }
        fun luma(pixel: Int): Int {
            val opacity = Color.alpha(pixel)
            val brightness = (77 * Color.red(pixel) + 150 * Color.green(pixel) + 29 * Color.blue(pixel)) / 256
            return (brightness * opacity + 255 * (255 - opacity)) / 255
        }
        val edges = pixels.indices.filter { i ->
            i < width || i >= width * (height - 1) || i % width == 0 || i % width == width - 1
        }
        if (edges.count { luma(pixels[it]) >= 225 } < edges.size * .98) return null
        if (pixels.count { luma(it) >= 225 } < pixels.size * .72) return null

        val left = IntArray(height) { width }
        val right = IntArray(height) { -1 }
        for (y in 0 until height) for (x in 0 until width) {
            if (luma(pixels[y * width + x]) < 145) {
                left[y] = minOf(left[y], x)
                right[y] = x
            }
        }
        val bands = mutableListOf<IntRange>()
        var start = -1
        for (y in 0..height) {
            if (y < height && right[y] >= 0) {
                if (start < 0) start = y
            } else if (start >= 0) {
                bands.add(start until y)
                start = -1
            }
        }
        val glyph = bands.firstOrNull() ?: return null
        val suffix = bands.drop(1)
        val glyphWidth = glyph.maxOf { right[it] } - glyph.minOf { left[it] } + 1
        val glyphHeight = glyph.count()
        if (glyphWidth < width * .22 || glyphHeight < height * .32 || glyph.first > height * .30) return null
        if (suffix.isEmpty() || suffix.first().first - glyph.last < maxOf(4, glyphHeight / 20)) return null
        if (suffix.any { it.count() > glyphHeight * .18 }) return null
        val caption = suffix.first()
        val captionWidth = caption.maxOf { right[it] } - caption.minOf { left[it] } + 1
        if (captionWidth > glyphWidth * .8) return null

        // Keep the full glyph and a small amount of the blank gap.
        val bottom = (glyph.last + (caption.first - glyph.last) / 2).toFloat() / height
        return GlyphCrop(bottom = bottom)
    }
}
