package com.chuahws.mayalanguageapp.integration.glyph

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes the EXPLICIT raw one-class YOLO11 ONNX layout [1, 5, N]:
 * [centreX, centreY, boxW, boxH, glyph-block score].
 *
 * Unit-testable on JVM, with no Android Bitmap, networking, or sign translation.
 * Coordinates are in 512x512 letterboxed MODEL pixels; correct for pad and
 * scale before returning normalized original-image coordinates.
 */
internal object GlyphV18Postprocessor {
    const val INPUT_SIDE = 512
    private const val MAX_BLOCKS = 48

    internal data class Box(val crop: GlyphCrop, val detectionScore: Float)

    fun decode(
        channels: Array<FloatArray>,
        sourceWidth: Int,
        sourceHeight: Int,
        scale: Float,
        padX: Float,
        padY: Float,
        minimumScore: Float = .35f,
    ): List<Box> {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(scale.isFinite() && scale > 0f)
        require(padX.isFinite() && padY.isFinite())
        require(minimumScore in 0f..1f)
        require(channels.size == 5) {
            "Expected raw single-class YOLO11 detection [1,5,N], not a segmentation/classifier model."
        }
        val n = channels.first().size
        require(n in 1..20000 && channels.all { it.size == n }) {
            "Unexpected YOLO11 prediction count or channel shapes."
        }

        val proposals = ArrayList<Box>()
        for (i in 0 until n) {
            val score = channels[4][i]
            if (!score.isFinite() || score < minimumScore) continue
            val x = channels[0][i]
            val y = channels[1][i]
            val w = channels[2][i]
            val h = channels[3][i]
            if (listOf(x,y,w,h).any { !it.isFinite() } || w <= 0f || h <= 0f) continue
            val left = ((x - w / 2f - padX) / scale / sourceWidth).coerceIn(0f, 1f)
            val right = ((x + w / 2f - padX) / scale / sourceWidth).coerceIn(0f, 1f)
            val top = ((y - h / 2f - padY) / scale / sourceHeight).coerceIn(0f, 1f)
            val bottom = ((y + h / 2f - padY) / scale / sourceHeight).coerceIn(0f, 1f)
            if (right - left < .005f || bottom - top < .005f) continue
            if ((right - left) * sourceWidth < 25f || (bottom - top) * sourceHeight < 25f) continue
            proposals += Box(GlyphCrop(left,top,right,bottom), score)
        }
        val sorted = proposals.sortedWith(
            compareByDescending<Box> { it.detectionScore }
                .thenBy { it.crop.top }.thenBy { it.crop.left }
        )
        val selected = ArrayList<Box>()
        for (box in sorted) {
            if (selected.all { overlap(it.crop, box.crop) <= .45f }) {
                selected += box
                if (selected.size == MAX_BLOCKS) break
            }
        }
        return selected
    }

    fun readingOrder(boxes: List<Box>): List<GlyphDetectedRegion> {
        if (boxes.isEmpty()) return emptyList()
        val heights = boxes.map { it.crop.bottom - it.crop.top }.sorted()
        val typicalHeight = heights[heights.size / 2]
        data class Row(val items: MutableList<Box>, var centre: Float)
        val rows = ArrayList<Row>()
        val topToBottom = boxes.sortedWith(compareBy<Box> {
            (it.crop.top + it.crop.bottom) * .5f
        }.thenBy { it.crop.left })
        for (box in topToBottom) {
            val cy = (box.crop.top + box.crop.bottom) * .5f
            val row = rows.minByOrNull { abs(it.centre - cy) }
                ?.takeIf { abs(it.centre - cy) <= typicalHeight * .45f }
            if (row == null) rows += Row(mutableListOf(box), cy)
            else {
                row.items += box
                row.centre = row.items.map { (it.crop.top + it.crop.bottom) * .5f }.average().toFloat()
            }
        }
        return rows.sortedBy { it.centre }.flatMapIndexed { rowIndex, row ->
            row.items.sortedBy { it.crop.left }.mapIndexed { colIndex, box ->
                GlyphDetectedRegion(box.crop, rowIndex + 1, colIndex + 1)
            }
        }
    }

    private fun overlap(a: GlyphCrop, b: GlyphCrop): Float {
        val left = max(a.left,b.left)
        val right = min(a.right,b.right)
        val top = max(a.top,b.top)
        val bottom = min(a.bottom,b.bottom)
        val intersection = max(0f,right-left) * max(0f,bottom-top)
        val aa = (a.right-a.left)*(a.bottom-a.top)
        val ab = (b.right-b.left)*(b.bottom-b.top)
        val union = aa+ab-intersection
        return if(union>0f) intersection/union else 0f
    }
}
