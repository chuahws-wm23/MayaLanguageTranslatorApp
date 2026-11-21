package com.chuahws.mayalanguageapp.integration.glyph

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Optional OFFLINE Maya glyph-BLOCK detector.
 *
 * The trained ONNX asset is NOT bundled until genuine v18 YOLO training,
 * held-out validation and on-phone checking have been completed.
 * If absent, the existing regular-grid detector remains available.
 *
 * ONLY a one-class YOLO11n export with raw channels [1,5,N], input [1,3,512,512]
 * is supported; do not supply Roboflow's hosted RF-DETR checkpoint or YOLO
 * models exported with embedded NMS.
 */
internal class GlyphV18Detector private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
) {
    companion object {
        private const val ASSET = "maya_blocks_v18.onnx"
        private const val INPUT = GlyphV18Postprocessor.INPUT_SIDE

        @Volatile private var cached: GlyphV18Detector? = null

        fun fromAssetsOrNull(context: Context): GlyphV18Detector? {
            val app = context.applicationContext
            if (ASSET !in (app.assets.list("") ?: emptyArray())) return null
            return cached ?: synchronized(this) {
                cached ?: run {
                    val data = app.assets.open(ASSET).use { it.readBytes() }
                    require(data.isNotEmpty()) { "Empty v18 block detector asset" }
                    val env = OrtEnvironment.getEnvironment()
                    val opts = OrtSession.SessionOptions()
                    opts.setIntraOpNumThreads(2)
                    try {
                        GlyphV18Detector(env, env.createSession(data, opts)).also { cached = it }
                    } finally {
                        opts.close()
                    }
                }
            }
        }
    }

    fun detect(image: Bitmap): List<GlyphDetectedRegion> {
        require(image.width >= 25 && image.height >= 25)
        val side = GlyphV18Postprocessor.INPUT_SIDE
        val scale = min(side.toFloat() / image.width, side.toFloat() / image.height)
        val nw = (image.width * scale).roundToInt().coerceIn(1, side)
        val nh = (image.height * scale).roundToInt().coerceIn(1, side)
        val left = (side - nw) / 2
        val top = (side - nh) / 2
        val letterboxed = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(letterboxed)
            canvas.drawColor(Color.rgb(114,114,114))
            canvas.drawBitmap(
                image, Rect(0,0,image.width,image.height),
                RectF(left.toFloat(),top.toFloat(),(left+nw).toFloat(),(top+nh).toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            val pixels = IntArray(side * side)
            letterboxed.getPixels(pixels, 0, side, 0, 0, side, side)
            val total = pixels.size
            val buf = ByteBuffer.allocateDirect(total * 3 * java.lang.Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            // RGB NCHW, float32 0..1. Do not normalize as ImageNet/MobileNet!
            for (channel in 0..2) for (colour in pixels) {
                val part = when(channel) {
                    0 -> colour ushr 16 and 0xff
                    1 -> colour ushr 8 and 0xff
                    else -> colour and 0xff
                }
                buf.put(part / 255f)
            }
            buf.flip()
            OnnxTensor.createTensor(
                env, buf, longArrayOf(1,3,side.toLong(),side.toLong()),
            ).use { tensor ->
                val inputName = session.inputNames.single()
                session.run(mapOf(inputName to tensor)).use { output ->
                    val batch = output[0].value as? Array<*>
                        ?: throw IllegalArgumentException("Unexpected YOLO output batch type")
                    val rows = batch.singleOrNull() as? Array<*>
                        ?: throw IllegalArgumentException("Expected one YOLO output batch")
                    require(rows.size == 5) { "Expected five raw YOLO box channels" }
                    val values = Array(5) { idx ->
                        rows[idx] as? FloatArray ?: error("Expected float YOLO box channels")
                    }
                    val detections = GlyphV18Postprocessor.decode(
                        channels = values, sourceWidth = image.width,
                        sourceHeight = image.height, scale = scale,
                        padX = left.toFloat(), padY = top.toFloat(),
                    )
                    return GlyphV18Postprocessor.readingOrder(detections)
                }
            }
        } finally {
            letterboxed.recycle()
        }
    }
}
