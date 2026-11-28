package com.chuahws.mayalanguageapp.integration.glyph

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/** Recovers strong drawing strokes when fine texture covers a bright display background. */
internal object GlyphScreenPhoto {
    private data class Stroke(
        val label: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val ink: Int,
    ) {
        val width get() = right - left
        val height get() = bottom - top
        val area get() = width * height
    }

    /** Returns a temporary working image in the same normalized coordinates as the photo. */
    fun clean(photo: Bitmap): Bitmap? {
        val scale = minOf(1f, 720f / maxOf(photo.width, photo.height))
        val width = (photo.width * scale).roundToInt()
        val height = (photo.height * scale).roundToInt()
        if (minOf(width, height) < 128) return null

        val sample = if (scale == 1f) photo else Bitmap.createScaledBitmap(photo, width, height, true)
        val gray = IntArray(width * height)
        try {
            sample.getPixels(gray, 0, width, 0, 0, width, height)
        } finally {
            if (sample !== photo) sample.recycle()
        }
        for (i in gray.indices) {
            val pixel = gray[i]
            val luminance = (77 * Color.red(pixel) + 150 * Color.green(pixel) + 29 * Color.blue(pixel)) shr 8
            val alpha = Color.alpha(pixel)
            gray[i] = (luminance * alpha + 255 * (255 - alpha)) / 255
        }

        val blurred = blur(gray, width, height)
        val histogram = IntArray(256)
        blurred.forEach { histogram[it]++ }
        var count = 0
        val paper = histogram.indices.first { value ->
            count += histogram[value]
            count >= gray.size * .72
        }
        if (paper < 145 || !hasBackgroundTexture(gray, blurred, width, height, maxOf(125, paper - 35))) return null

        val labels = IntArray(gray.size)
        val strokes = collectStrokes(gray, blurred, width, height, paper - 65, labels)
        val minSpan = minOf(width, height) * .045
        val anchors = strokes.filter {
            it.width >= minSpan && it.height >= minSpan &&
                it.area >= gray.size * .006 && it.ink >= it.area * .06
        }
        if (anchors.none { it.area >= gray.size * .045 }) return null

        val retained = BooleanArray(strokes.maxOf { it.label } + 1)
        for (stroke in strokes) {
            retained[stroke.label] = anchors.any { anchor ->
                // Keep disconnected dots and details inside the larger outline.
                val pad = minOf(anchor.width, anchor.height) / 20
                stroke.left >= anchor.left - pad && stroke.right <= anchor.right + pad &&
                    stroke.top >= anchor.top - pad && stroke.bottom <= anchor.bottom + pad
            }
        }
        val pixels = IntArray(gray.size) { i ->
            val label = labels[i]
            if (label in 1 until retained.size && retained[label]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blur(gray: IntArray, width: Int, height: Int): IntArray {
        val stride = width + 1
        val sums = IntArray(stride * (height + 1))
        for (y in 0 until height) {
            var row = 0
            for (x in 0 until width) {
                row += gray[y * width + x]
                sums[(y + 1) * stride + x + 1] = sums[y * stride + x + 1] + row
            }
        }
        return IntArray(gray.size) { i ->
            val x = i % width
            val y = i / width
            val left = maxOf(0, x - 2)
            val top = maxOf(0, y - 2)
            val right = minOf(width, x + 3)
            val bottom = minOf(height, y + 3)
            val sum = sums[bottom * stride + right] - sums[top * stride + right] -
                sums[bottom * stride + left] + sums[top * stride + left]
            sum / ((right - left) * (bottom - top))
        }
    }

    private fun hasBackgroundTexture(gray: IntArray, blurred: IntArray, width: Int, height: Int, minLight: Int): Boolean {
        val light = IntArray(16)
        val textured = IntArray(16)
        for (y in 2 until height - 2) for (x in 2 until width - 2) {
            val i = y * width + x
            if (blurred[i] < minLight) continue
            val tile = (y * 4 / height) * 4 + x * 4 / width
            light[tile]++
            if (abs(gray[i] - blurred[i]) >= 10) textured[tile]++
        }
        return light.indices.count { tile ->
            light[tile] >= gray.size * .012 && textured[tile] >= light[tile] * .20
        } >= 5
    }

    private fun collectStrokes(
        gray: IntArray,
        blurred: IntArray,
        width: Int,
        height: Int,
        threshold: Int,
        labels: IntArray,
    ): List<Stroke> {
        val pending = IntArray(gray.size)
        val strokes = mutableListOf<Stroke>()
        val ink = BooleanArray(gray.size) { (gray[it] + 2 * blurred[it]) / 3 <= threshold }
        var label = 0
        for (i in ink.indices) {
            if (!ink[i] || labels[i] != 0) continue
            label++
            var head = 0
            var size = 0
            var left = width
            var top = height
            var right = 0
            var bottom = 0
            fun enqueue(index: Int) {
                if (ink[index] && labels[index] == 0) {
                    labels[index] = label
                    pending[size++] = index
                }
            }
            enqueue(i)
            while (head < size) {
                val index = pending[head++]
                val x = index % width
                val y = index / width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + 1)
                bottom = maxOf(bottom, y + 1)
                if (x > 0) enqueue(index - 1)
                if (x < width - 1) enqueue(index + 1)
                if (y > 0) enqueue(index - width)
                if (y < height - 1) enqueue(index + width)
            }
            val stroke = Stroke(label, left, top, right, bottom, size)
            val outerBar = stroke.ink >= stroke.area * .65 &&
                ((stroke.height >= height * .9 && (left == 0 || right == width)) ||
                    (stroke.width >= width * .9 && (top == 0 || bottom == height)))
            if (!outerBar && size >= 4) strokes.add(stroke)
        }
        return strokes
    }
}
