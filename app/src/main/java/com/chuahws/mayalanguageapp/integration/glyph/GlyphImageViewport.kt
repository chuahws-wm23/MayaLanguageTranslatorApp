package com.chuahws.mayalanguageapp.integration.glyph

/** Shared aspect-fit geometry for drawing boxes and selecting them by touch. */
internal data class GlyphImageViewport private constructor(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun regionAt(x: Float, y: Float, regions: List<GlyphCrop>): Int? {
        if (!x.isFinite() || !y.isFinite()) return null
        val u = (x - left) / width
        val v = (y - top) / height
        if (u !in 0f..1f || v !in 0f..1f) return null
        // A nested smaller box is easier to select than the surrounding box.
        return regions.indices.filter { i ->
            val box = regions[i]
            u in box.left..box.right && v in box.top..box.bottom
        }.minByOrNull { i ->
            val box = regions[i]
            (box.right - box.left) * (box.bottom - box.top)
        }
    }

    companion object {
        fun fit(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float): GlyphImageViewport {
            require(imageWidth > 0 && imageHeight > 0)
            require(viewWidth.isFinite() && viewHeight.isFinite() && viewWidth > 0 && viewHeight > 0)
            val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
            val width = imageWidth * scale
            val height = imageHeight * scale
            return GlyphImageViewport((viewWidth - width) / 2, (viewHeight - height) / 2, width, height)
        }
    }
}
