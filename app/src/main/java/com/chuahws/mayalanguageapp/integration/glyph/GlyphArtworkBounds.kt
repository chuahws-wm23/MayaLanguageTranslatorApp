package com.chuahws.mayalanguageapp.integration.glyph

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/** Finds the extent of one drawing after the panel detector has checked the layout. */
internal object GlyphArtworkBounds {
    fun find(image: Bitmap): GlyphCrop? {
        val scale = minOf(1f, 512f / maxOf(image.width, image.height))
        val width = (image.width * scale).roundToInt().coerceAtLeast(1)
        val height = (image.height * scale).roundToInt().coerceAtLeast(1)
        val sample = if (scale < 1f) Bitmap.createScaledBitmap(image, width, height, true) else image
        val pixels = IntArray(width * height)
        try {
            sample.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            if (sample !== image) sample.recycle()
        }
        val gray = IntArray(pixels.size) { i ->
            val p = pixels[i]
            val alpha = Color.alpha(p)
            val luma = (77 * Color.red(p) + 150 * Color.green(p) + 29 * Color.blue(p) + 128) shr 8
            (luma * alpha + 255 * (255 - alpha) + 127) / 255
        }
        val bottom = GlyphCaptionCrop.find(image)?.pixels(width, height)
            ?.let { (_, y, _, h) -> y + h } ?: height
        return bounds(gray, width, height, bottom)
    }

    fun bounds(gray: IntArray, width: Int, height: Int, bottom: Int = height): GlyphCrop? {
        require(width > 0 && height > 0 && gray.size == width * height && bottom in 1..height)
        if (width < 20 || bottom < 20) return null
        val drawing = if (bottom == height) gray else gray.copyOf(width * bottom)
        val perimeter = (0 until width).flatMap { listOf(drawing[it], drawing[(bottom - 1) * width + it]) } +
            (1 until bottom - 1).flatMap { listOf(drawing[it * width], drawing[(it + 1) * width - 1]) }
        val background = perimeter.sorted()[perimeter.size / 2]
        val foreground = if (background <= 32 && perimeter.count { it <= background + 16 } >= perimeter.size * .85f) {
            // Include the bright fill itself so the box contains the complete silhouette.
            BooleanArray(drawing.size) { drawing[it] > background + 28 }
        } else GlyphVisualCatalogue.foregroundMask(drawing, width, bottom) ?: return null
        val rows = IntArray(bottom)
        val columns = IntArray(width)
        for (y in 0 until bottom) for (x in 0 until width) {
            if (foreground[y * width + x]) {
                rows[y]++
                columns[x]++
            }
        }
        val left = columns.indexOfFirst { it >= 2 }
        val right = columns.indexOfLast { it >= 2 } + 1
        val top = rows.indexOfFirst { it >= 2 }
        val end = rows.indexOfLast { it >= 2 } + 1
        if (left < 0 || top < 0 || right - left < 12 || end - top < 12 || rows.sum() < 24) return null
        val padding = maxOf(3, (minOf(right - left, end - top) * .06f).roundToInt())
        return GlyphCrop(
            (left - padding).coerceAtLeast(0).toFloat() / width,
            (top - padding).coerceAtLeast(0).toFloat() / height,
            (right + padding).coerceAtMost(width).toFloat() / width,
            (end + padding).coerceAtMost(bottom).toFloat() / height,
        )
    }
}
