package com.chuahws.mayalanguageapp.integration.glyph

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Fractions of the correctly oriented image, independent of display size. */
data class GlyphCrop(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f })
        require(right > left && bottom > top)
    }
    fun pixels(width: Int, height: Int): IntArray {
        require(width > 0 && height > 0)
        val x = floor(left * width).toInt().coerceIn(0, width - 1)
        val y = floor(top * height).toInt().coerceIn(0, height - 1)
        val r = ceil(right * width).toInt().coerceIn(x + 1, width)
        val b = ceil(bottom * height).toInt().coerceIn(y + 1, height)
        return intArrayOf(x, y, r - x, b - y)
    }
    companion object {
        fun between(x1: Float, y1: Float, x2: Float, y2: Float): GlyphCrop? {
            if (!listOf(x1, y1, x2, y2).all { it.isFinite() }) return null
            val l = min(x1, x2).coerceIn(0f, 1f)
            val t = min(y1, y2).coerceIn(0f, 1f)
            val r = max(x1, x2).coerceIn(0f, 1f)
            val b = max(y1, y2).coerceIn(0f, 1f)
            return if (r - l < .03f || b - t < .03f) null else GlyphCrop(l, t, r, b)
        }
    }
}
