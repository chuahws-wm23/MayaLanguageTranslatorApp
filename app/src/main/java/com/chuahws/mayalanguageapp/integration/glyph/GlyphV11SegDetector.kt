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
 * OPTIONAL offline instance segmenter for individual graphic components.
 *
 * Research-only; exported from the user's separate Roboflow v11 one-class
 * YOLO11n-seg training job. Never load the .pt checkpoint into Android.
 * Model must be exported to ONNX with nms=false, imgsz=512, dynamic=false.
 * Unlike v18, this model returns TWO tensors: [1,37,N] and [1,32,128,128].
 */
internal class GlyphV11SegDetector private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
) {
    companion object {
        const val ASSET_NAME = "maya_signs_v11_seg.onnx"
        private const val SIDE = GlyphV11SegPostprocessor.INPUT_SIDE
        @Volatile private var cached: GlyphV11SegDetector? = null

        fun isInstalled(context: Context): Boolean =
            ASSET_NAME in (context.applicationContext.assets.list("") ?: emptyArray())

        fun fromAssetsOrNull(context: Context): GlyphV11SegDetector? {
            if (!isInstalled(context)) return null
            return cached ?: synchronized(this) {
                cached ?: run {
                    val app = context.applicationContext
                    val bytes = app.assets.open(ASSET_NAME).use { it.readBytes() }
                    require(bytes.isNotEmpty()) { "Empty v11 segmentation model asset." }
                    val env = OrtEnvironment.getEnvironment()
                    val options = OrtSession.SessionOptions()
                    options.setIntraOpNumThreads(2)
                    try {
                        val session = env.createSession(bytes, options)
                        try {
                            require(session.inputNames.size == 1 && session.outputNames.size == 2 &&
                                session.outputNames.containsAll(listOf("output0", "output1"))) {
                                "Expected YOLO11n-seg outputs output0 and output1."
                            }
                            GlyphV11SegDetector(env,session).also { cached = it }
                        } catch (err: Exception) {
                            session.close()
                            throw err
                        }
                    } finally {
                        options.close()
                    }
                }
            }
        }
    }

    fun detect(image: Bitmap): List<GlyphDetectedRegion> {
        require(image.width >= 25 && image.height >= 25)
        val scale = min(SIDE.toFloat()/image.width, SIDE.toFloat()/image.height)
        val width = (image.width * scale).roundToInt().coerceIn(1,SIDE)
        val height = (image.height * scale).roundToInt().coerceIn(1,SIDE)
        val left = (SIDE - width) / 2
        val top = (SIDE - height) / 2
        val letterbox = Bitmap.createBitmap(SIDE,SIDE,Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(letterbox)
            canvas.drawColor(Color.rgb(114,114,114))
            canvas.drawBitmap(
                image,Rect(0,0,image.width,image.height),
                RectF(left.toFloat(),top.toFloat(),(left+width).toFloat(),(top+height).toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            val pixels = IntArray(SIDE*SIDE)
            letterbox.getPixels(pixels,0,SIDE,0,0,SIDE,SIDE)
            val buffer = ByteBuffer.allocateDirect(pixels.size*3*4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            for (c in 0..2) for (pixel in pixels) {
                val v = when(c) {
                    0 -> pixel ushr 16 and 255
                    1 -> pixel ushr 8 and 255
                    else -> pixel and 255
                }
                buffer.put(v/255f)
            }
            buffer.flip()
            OnnxTensor.createTensor(env,buffer,longArrayOf(1L,3L,SIDE.toLong(),SIDE.toLong()))
                .use { tensor ->
                    session.run(mapOf(session.inputNames.single() to tensor)).use { result ->
                        // Output 0: [batch=1,channel=37,proposal=N].
                        val detectionBatch = result[0].value as? Array<*>
                            ?: error("Expected float prediction batch")
                        val channelRows = detectionBatch.singleOrNull() as? Array<*>
                            ?: error("Expected one prediction batch")
                        val channels = Array(channelRows.size) { i ->
                            channelRows[i] as? FloatArray
                                ?: error("Expected one float array per prediction channel")
                        }

                        // Output 1: [batch=1,mask channel=32,y=128,x=128].
                        val protoBatch = result[1].value as? Array<*>
                            ?: error("Expected mask prototype batch")
                        val protoChannels = protoBatch.singleOrNull() as? Array<*>
                            ?: error("Expected one mask prototype batch")
                        val prototypes = Array(protoChannels.size) { i ->
                            val rows = protoChannels[i] as? Array<*>
                                ?: error("Expected 2D mask plane")
                            Array(rows.size) { r ->
                                rows[r] as? FloatArray
                                    ?: error("Expected float prototype pixels")
                            }
                        }
                        val components = GlyphV11SegPostprocessor.decode(
                            channels,prototypes,image.width,image.height,
                            scale,left.toFloat(),top.toFloat(),
                        )
                        return GlyphV18Postprocessor.readingOrder(
                            components.map { c ->
                                GlyphV18Postprocessor.Box(c.crop,c.score)
                            }
                        )
                    }
                }
        } finally {
            letterbox.recycle()
        }
    }
}
