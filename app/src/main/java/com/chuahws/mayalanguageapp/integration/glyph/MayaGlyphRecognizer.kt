package com.chuahws.mayalanguageapp.integration.glyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Android preprocessing for TinyGlyphNet. Call on a background dispatcher. */
class MayaGlyphRecognizer private constructor(private val network: GlyphNetwork) {
    companion object {
        private const val INPUT_SIZE = GlyphNetwork.INPUT_SIZE
        @Volatile private var cached: MayaGlyphRecognizer? = null
        fun fromAssets(context: Context): MayaGlyphRecognizer = cached ?: synchronized(this) {
            cached ?: MayaGlyphRecognizer(GlyphNetwork.fromBytes(
                context.applicationContext.assets.open("maya_glyph_v1.mgl").use { it.readBytes() }
            )).also { cached = it }
        }
    }

    fun recognize(bitmap: Bitmap): GlyphNetwork.Result = network.recognize(preprocess(bitmap))

    private fun preprocess(original: Bitmap): FloatArray {
        require(original.width >= 25 && original.height >= 25) { "Image is too small" }
        val factor = min(1f, 1200f / max(original.width, original.height))
        val bitmap = if (factor < 1f) Bitmap.createScaledBitmap(
            original, max(25, (original.width * factor).roundToInt()),
            max(25, (original.height * factor).roundToInt()), true
        ) else original
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val gray = IntArray(pixels.size)
            val hist = IntArray(256)
            val edgeHist = IntArray(256)
            var edgeCount = 0
            for (y in 0 until height) for (x in 0 until width) {
                val idx = y * width + x
                val c = pixels[idx]
                val alpha = Color.alpha(c)
                val luminance = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29 + 128) shr 8
                val v = (luminance * alpha + 255 * (255 - alpha) + 127) / 255
                gray[idx] = v
                if (x < 3 || y < 3 || x >= width - 3 || y >= height - 3) {
                    edgeHist[v]++;edgeCount++
                }
            }
            var medianValue = 0
            var edgesSoFar = 0
            for (level in edgeHist.indices) {
                edgesSoFar += edgeHist[level]
                if (edgesSoFar >= edgeCount / 2) { medianValue = level; break }
            }
            val invert = medianValue < 110
            for (idx in gray.indices) {
                if (invert) gray[idx] = 255 - gray[idx]
                hist[gray[idx]]++
            }
            val total = gray.size.toDouble()
            var fraction = 0.0
            var partialMean = 0.0
            var globalMean = 0.0
            for (i in hist.indices) globalMean += hist[i].toDouble() * i / total
            var maxScore = -1.0
            var threshold = 128
            for (i in hist.indices) {
                fraction += hist[i].toDouble() / total
                partialMean += hist[i].toDouble() * i / total
                if (fraction <= 1e-9 || fraction >= 1.0 - 1e-9) continue
                val score = Math.pow(globalMean * fraction - partialMean, 2.0) / (fraction * (1.0 - fraction))
                if (score > maxScore) { maxScore = score; threshold = i }
            }
            threshold = threshold.coerceIn(50, 225)
            var minX = width
            var maxX = -1
            var minY = height
            var maxY = -1
            var count = 0
            for (y in 0 until height) for (x in 0 until width) {
                if (gray[y * width + x] < threshold) {
                    count++
                    minX = min(minX, x); maxX = max(maxX, x)
                    minY = min(minY, y); maxY = max(maxY, y)
                }
            }
            val density = count.toFloat() / gray.size
            require(density in 0.005f..0.75f && maxX >= minX) {
                "No isolated dark glyph detected; crop the image to one sign"
            }
            val dx = max(1, ((maxX - minX + 1) * .025f).toInt())
            val dy = max(1, ((maxY - minY + 1) * .025f).toInt())
            val x0 = max(0, minX - dx)
            val x1 = min(width, maxX + 1 + dx)
            val y0 = max(0, minY - dy)
            val y1 = min(height, maxY + 1 + dy)
            val bw = x1 - x0
            val bh = y1 - y0
            val cropped = IntArray(bw * bh)
            for (y in 0 until bh) for (x in 0 until bw) {
                cropped[y * bw + x] = if (gray[(y + y0) * width + x + x0] < threshold) Color.WHITE else Color.BLACK
            }
            val source = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            try {
                source.setPixels(cropped, 0, bw, 0, 0, bw, bh)
                val resizeFactor = min(54.0 / bw, 54.0 / bh)
                val nw = max(1, (bw * resizeFactor).roundToInt())
                val nh = max(1, (bh * resizeFactor).roundToInt())
                val scaled = Bitmap.createScaledBitmap(source, nw, nh, true)
                try {
                    val tiny = IntArray(nw * nh)
                    scaled.getPixels(tiny, 0, nw, 0, 0, nw, nh)
                    val out = FloatArray(INPUT_SIZE * INPUT_SIZE)
                    val offX = (INPUT_SIZE - nw) / 2
                    val offY = (INPUT_SIZE - nh) / 2
                    for (y in 0 until nh) for (x in 0 until nw) {
                        out[(offY + y) * INPUT_SIZE + offX + x] = Color.red(tiny[y * nw + x]) / 255f
                    }
                    return out
                } finally { if (scaled !== source) scaled.recycle() }
            } finally { source.recycle() }
        } finally { if (bitmap !== original) bitmap.recycle() }
    }
}
