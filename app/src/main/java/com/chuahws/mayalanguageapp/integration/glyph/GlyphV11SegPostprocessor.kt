package com.chuahws.mayalanguageapp.integration.glyph

import kotlin.math.max
import kotlin.math.min

/**
 * Decodes one-class YOLO11n-seg ONNX WITHOUT embedded NMS:
 * output0 = [1,37,5376] (xywh, one score, 32 mask coefficients)
 * output1 = [1,32,128,128] (32 prototype mask channels) for input 512.
 *
 * Output regions are mask-derived tight bounding rectangles; they are NOT
 * labels, syllables, TWKM IDs, verified sign segmentation, or translations.
 * Keep separate from the block detector's [1,5,N] output.
 */
internal object GlyphV11SegPostprocessor {
    const val INPUT_SIDE = 512
    private const val MASK_CHANNELS = 32
    private const val MAX_COMPONENTS = 64

    internal data class Candidate(
        val crop: GlyphCrop,
        val score: Float,
    )

    private data class Proposal(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val score: Float, val index: Int,
    ) {
        val left get() = x - w / 2f
        val right get() = x + w / 2f
        val top get() = y - h / 2f
        val bottom get() = y + h / 2f
    }

    fun decode(
        channels: Array<FloatArray>,
        prototypes: Array<Array<FloatArray>>,
        sourceWidth: Int,
        sourceHeight: Int,
        scale: Float,
        padX: Float,
        padY: Float,
        minimumScore: Float = .35f,
    ): List<Candidate> {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(scale > 0f && scale.isFinite() && padX.isFinite() && padY.isFinite())
        require(minimumScore in 0f..1f)
        require(channels.size == 5 + MASK_CHANNELS) {
            "Expected one-class raw YOLO11 segmentation [1,37,N], not the v18 block detector."
        }
        val n = channels[0].size
        require(n in 1..20000 && channels.all { it.size == n }) {
            "Unexpected YOLO segmentation anchor dimensions."
        }
        require(prototypes.size == MASK_CHANNELS &&
            prototypes.all { it.size == 128 && it.all { row -> row.size == 128 } }) {
            "Expected 32 mask prototypes of 128x128 at 512-pixel input."
        }

        val proposals = ArrayList<Proposal>()
        for (i in 0 until n) {
            val score = channels[4][i]
            if (!score.isFinite() || score < minimumScore) continue
            val x = channels[0][i]
            val y = channels[1][i]
            val w = channels[2][i]
            val h = channels[3][i]
            if (listOf(x, y, w, h).any { !it.isFinite() } || w <= 0f || h <= 0f) continue
            if (x + w/2f < 0f || x - w/2f > INPUT_SIDE ||
                y + h/2f < 0f || y - h/2f > INPUT_SIDE) continue
            proposals += Proposal(x,y,w,h,score,i)
        }

        // Suppress likely duplicate instances before expensive mask multiplication.
        val sorted = proposals.sortedByDescending { it.score }.take(240)
        val picked = ArrayList<Proposal>(MAX_COMPONENTS)
        for (proposal in sorted) {
            if (picked.all { iou(it, proposal) <= .50f }) {
                picked += proposal
                if (picked.size >= MAX_COMPONENTS) break
            }
        }

        return picked.mapNotNull { p ->
            val protoBounds = tightMaskBounds(p, channels, prototypes) ?: return@mapNotNull null
            val (pl, pt, pr, pb) = protoBounds
            val ml = pl * (INPUT_SIDE / 128f)
            val mt = pt * (INPUT_SIDE / 128f)
            val mr = pr * (INPUT_SIDE / 128f)
            val mb = pb * (INPUT_SIDE / 128f)
            val left = ((ml - padX) / scale / sourceWidth).coerceIn(0f,1f)
            val top = ((mt - padY) / scale / sourceHeight).coerceIn(0f,1f)
            val right = ((mr - padX) / scale / sourceWidth).coerceIn(0f,1f)
            val bottom = ((mb - padY) / scale / sourceHeight).coerceIn(0f,1f)
            if (right - left < .004f || bottom - top < .004f) null
            else Candidate(GlyphCrop(left,top,right,bottom),p.score)
        }
    }

    /** Bounds in proto-grid coordinates [left,top,right-exclusive,bottom-exclusive]. */
    private fun tightMaskBounds(
        p: Proposal,
        channels: Array<FloatArray>,
        prototype: Array<Array<FloatArray>>,
    ): IntArray? {
        val px0 = (p.left / 4f).toInt().coerceIn(0,127)
        val py0 = (p.top / 4f).toInt().coerceIn(0,127)
        val px1 = (p.right / 4f).toInt().coerceIn(0,127)
        val py1 = (p.bottom / 4f).toInt().coerceIn(0,127)
        if (px1 <= px0 || py1 <= py0) return null
        var minX = 128; var minY = 128
        var maxX = -1; var maxY = -1
        var onPixels = 0
        for (y in py0..py1) for (x in px0..px1) {
            var logit = 0f
            for (k in 0 until MASK_CHANNELS) {
                val weight = channels[5 + k][p.index]
                if (!weight.isFinite()) return null
                logit += weight * prototype[k][y][x]
            }
            // sigmoid(logit)>0.5 <=> logit>0.
            if (logit > 0f) {
                onPixels++
                minX = min(minX,x); minY = min(minY,y)
                maxX = max(maxX,x); maxY = max(maxY,y)
            }
        }
        if (onPixels < 3 || maxX < minX || maxY < minY) return null
        // One mask-cell margin preserves thin strokes and avoids clipping.
        return intArrayOf(
            max(0,minX-1), max(0,minY-1),
            min(128,maxX+2), min(128,maxY+2)
        )
    }

    private fun iou(a: Proposal, b: Proposal): Float {
        val iw = max(0f,min(a.right,b.right)-max(a.left,b.left))
        val ih = max(0f,min(a.bottom,b.bottom)-max(a.top,b.top))
        val intersection = iw*ih
        val union = a.w*a.h+b.w*b.h-intersection
        return if (union>0f) intersection/union else 0f
    }
}
