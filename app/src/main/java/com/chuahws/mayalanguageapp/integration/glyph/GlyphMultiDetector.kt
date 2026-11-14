package com.chuahws.mayalanguageapp.integration.glyph

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** An automatically separated glyph BLOCK, not necessarily one phonetic sign. */
data class GlyphDetectedRegion(
    val crop: GlyphCrop,
    val row: Int,
    val column: Int,
)

/**
 * Lightweight, offline detection of repeated stone/codex glyph BLOCKS arranged
 * in a regular row/column panel. Does not depend on Python, OpenCV or network.
 *
 * Finds repeated dark seams using image projections + peak/contrast checks.
 * Designed for multi-block grid-style photos, including textured carved panels.
 * Deliberately returns NO regions rather than hallucinating boxes when the
 * photo has no clear regularly-spaced block boundaries.
 *
 * This is image layout detection, NOT individual-sign recognition or translation.
 */
object GlyphMultiDetector {
    private const val MAX_SIDE = 960
    private const val MAX_CELLS = 48

    fun detect(image: Bitmap): List<GlyphDetectedRegion> {
        require(image.width > 0 && image.height > 0)
        val scale = min(1f, MAX_SIDE.toFloat() / max(image.width, image.height))
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(
            image,
            max(1, (image.width * scale).roundToInt()),
            max(1, (image.height * scale).roundToInt()),
            true,
        ) else image
        try {
            val width = bitmap.width
            val height = bitmap.height
            if (width < 100 || height < 100) return emptyList()
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val gray = IntArray(pixels.size)
            for (i in pixels.indices) {
                val c = pixels[i]
                val luma = (Color.red(c) * 77 + Color.green(c) * 150 +
                    Color.blue(c) * 29 + 128) shr 8
                val alpha = Color.alpha(c)
                gray[i] = (luma * alpha + 255 * (255 - alpha) + 127) / 255
            }
            return detectFromGrayscale(gray, width, height)
        } finally {
            if (bitmap !== image) bitmap.recycle()
        }
    }

    /** Pure numerical method tested without Android camera or Bitmap. */
    fun detectFromGrayscale(
        gray: IntArray,
        width: Int,
        height: Int,
    ): List<GlyphDetectedRegion> {
        require(width > 0 && height > 0 && gray.size == width * height)
        if (width < 100 || height < 100) return emptyList()
        require(gray.all { it in 0..255 })

        val threshold = otsu(gray).coerceIn(45, 210)
        val columns = FloatArray(width)
        val rows = FloatArray(height)
        for (y in 0 until height) for (x in 0 until width) {
            if (gray[y * width + x] < threshold) {
                columns[x] += 1f
                rows[y] += 1f
            }
        }
        for (x in columns.indices) columns[x] /= height
        for (y in rows.indices) rows[y] /= width

        val x = findRegularSeams(columns) ?: return emptyList()
        val y = findRegularSeams(rows) ?: return emptyList()
        if (x.cells < 2 || y.cells < 2 || x.cells * y.cells !in 4..MAX_CELLS) {
            return emptyList()
        }
        // Require at least one dimension to have multiple repeated seams.
        if (x.cells < 3 && y.cells < 3) return emptyList()

        val lefts = listOf(0) + x.seams
        val rights = x.seams + listOf(width)
        val tops = listOf(0) + y.seams
        val bottoms = y.seams + listOf(height)

        val result = mutableListOf<GlyphDetectedRegion>()
        for (row in 0 until y.cells) for (column in 0 until x.cells) {
            val l = lefts[column]
            val r = rights[column]
            val top = tops[row]
            val bottom = bottoms[row]
            val cellWidth = r - l
            val cellHeight = bottom - top
            if (cellWidth < 30 || cellHeight < 30) continue
            // Dark seams belong to neighbouring blocks. Exclude a small inset.
            val insetX = max(1, (cellWidth * .018f).roundToInt())
            val insetY = max(1, (cellHeight * .018f).roundToInt())
            val a = (l + insetX).toFloat() / width
            val b = (top + insetY).toFloat() / height
            val c = (r - insetX).toFloat() / width
            val d = (bottom - insetY).toFloat() / height
            if (c > a && d > b) {
                result += GlyphDetectedRegion(
                    crop = GlyphCrop(a.coerceIn(0f, 1f), b.coerceIn(0f, 1f),
                        c.coerceIn(0f, 1f), d.coerceIn(0f, 1f)),
                    row = row + 1,
                    column = column + 1,
                )
            }
        }
        return result
    }

    private data class Seams(val cells: Int, val seams: List<Int>, val score: Double)

    private fun findRegularSeams(profile: FloatArray): Seams? {
        val length = profile.size
        val radius = max(2, length / 90)
        val smooth = FloatArray(length)
        for (i in profile.indices) {
            var total = 0.0
            var count = 0
            for (j in max(0, i - radius)..min(length - 1, i + radius)) {
                total += profile[j]
                count++
            }
            smooth[i] = (total / count).toFloat()
        }

        var best: Seams? = null
        val maximum = min(10, length / 40)
        for (cells in 2..maximum) {
            val step = length.toDouble() / cells
            val seams = ArrayList<Int>(cells - 1)
            val contrasts = ArrayList<Double>(cells - 1)
            var stronglySupported = 0
            for (k in 1 until cells) {
                val expected = k * step
                val tolerance = min(13.0, max(4.0, step * .15))
                val first = max(2, (expected - tolerance).toInt())
                val last = min(length - 3, (expected + tolerance).toInt())
                if (first >= last) continue
                var peak = first
                for (idx in first..last) {
                    if (smooth[idx] > smooth[peak]) peak = idx
                }
                val midBefore = ((k - .5) * step).toInt().coerceIn(0, length - 1)
                val midAfter = ((k + .5) * step).toInt().coerceIn(0, length - 1)
                val contrast = smooth[peak].toDouble() -
                    .5 * (smooth[midBefore].toDouble() + smooth[midAfter].toDouble())
                seams += peak
                contrasts += contrast
                if (contrast > .12) stronglySupported++
            }
            if (seams.size != cells - 1) continue
            // A single outlined glyph may have two strong edges; real panels need
            // nearly every repeated seam, not just two of three.
            if (stronglySupported.toDouble() / contrasts.size < .80) continue

            val meanPositive = contrasts.map { max(0.0, it) }.average()
            val coverage = stronglySupported.toDouble() / contrasts.size
            val score = meanPositive * (.55 + .45 * coverage) +
                .015 * sqrt((cells - 1).toDouble()) - .012 * (cells - 2)
            if (score < .12) continue
            if (best == null || score > best.score) best = Seams(cells, seams, score)
        }
        return best
    }

    private fun otsu(values: IntArray): Int {
        val hist = IntArray(256)
        for (v in values) hist[v]++
        val total = values.size.toDouble()
        var sum = 0.0
        for (i in hist.indices) sum += i * hist[i].toDouble()
        var background = 0.0
        var backgroundSum = 0.0
        var bestScore = -1.0
        var best = 128
        for (t in hist.indices) {
            background += hist[t]
            if (background <= 0.0) continue
            val foreground = total - background
            if (foreground <= 0.0) break
            backgroundSum += t * hist[t].toDouble()
            val difference = backgroundSum / background - (sum - backgroundSum) / foreground
            val variance = background * foreground * difference * difference
            if (variance > bestScore) {
                bestScore = variance
                best = t
            }
        }
        return best
    }
}
