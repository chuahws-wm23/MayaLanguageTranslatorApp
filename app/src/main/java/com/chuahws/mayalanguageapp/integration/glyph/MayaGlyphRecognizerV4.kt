package com.chuahws.mayalanguageapp.integration.glyph

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Offline experimental 10-sign visual recognizer.
 *
 * The fine-tuned MobileNetV3-small model was trained using the publicly
 * accessible TWKM catalogue. It chooses the MOST LIKELY of ten categories
 * even for an unsupported sign; it is NOT a validated open-set detector.
 * Use v3 for the five-sign screened research demo.
 */
data class GlyphV4Result(
    val signNumber: Int,
    val reading: String,
    val englishGloss: String,
    val modelVersion: String = "Research v4",
)

class MayaGlyphRecognizerV4 private constructor(
    private val env: OrtEnvironment,
    private val model: OrtSession,
) {
    companion object {
        private const val ASSET = "maya_glyph_v4.onnx"
        private const val BYTES = 3_741_064
        private const val SHA256 = "b9bb6bcc816f9c7957792302d7a1e50aaee2a1ecf3d44b8ae9b9b493cf4efba9"
        const val INPUT_SIDE = 160

        private val signs = intArrayOf(32, 184, 291, 4, 44, 507, 548, 122, 187, 1030)
        private val readings = arrayOf(
            "K'UH", "K'INICH", "SIIP", "NAAH", "TOK",
            "TZIMAH", "HAAB", "K'AK'", "K'ABA'", "K'AWIIL",
        )
        private val english = arrayOf(
            "God / deity", "Resplendent / sunny", "Name of a deity",
            "House / first", "Cloud / mist", "Gourd", "Year", "Fire", "Name", "Name of a deity",
        )

        @Volatile private var cached: MayaGlyphRecognizerV4? = null

        fun fromAssets(context: Context): MayaGlyphRecognizerV4 =
            cached ?: synchronized(this) {
                cached ?: create(context.applicationContext).also { cached = it }
            }

        private fun create(context: Context): MayaGlyphRecognizerV4 {
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            require(bytes.size == BYTES) { "v4 model asset has incorrect size." }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            require(hash == SHA256) { "v4 model asset integrity check failed." }
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions()
            opts.setIntraOpNumThreads(2)
            return try {
                MayaGlyphRecognizerV4(env, env.createSession(bytes, opts))
            } finally {
                opts.close()
            }
        }

        /** Pure numerical validation. A relative logit is not a probability. */
        internal fun interpret(logits: FloatArray): GlyphV4Result {
            require(logits.size == signs.size && logits.all { it.isFinite() }) {
                "Invalid ten-sign model logits."
            }
            var best = 0
            for (i in 1 until logits.size) {
                if (logits[i] > logits[best]) best = i
            }
            return GlyphV4Result(signs[best], readings[best], english[best])
        }
    }

    /** Runs the trained neural weights entirely offline on a background thread. */
    fun recognize(bitmap: Bitmap): GlyphV4Result {
        val input = GlyphV2Preprocessor.fromBitmap(bitmap, INPUT_SIDE)
        require(input.size == 3 * INPUT_SIDE * INPUT_SIDE)
        val buffer = ByteBuffer.allocateDirect(input.size * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(input)
        buffer.flip()
        val tensor = OnnxTensor.createTensor(
            env, buffer, longArrayOf(1, 3, INPUT_SIDE.toLong(), INPUT_SIDE.toLong()),
        )
        try {
            model.run(mapOf("image" to tensor)).use { output ->
                val logits = when (val value = output[0].value) {
                    is Array<*> -> value.firstOrNull() as? FloatArray
                    is FloatArray -> value
                    else -> null
                } ?: error("Unexpected v4 model output.")
                return interpret(logits)
            }
        } finally {
            tensor.close()
        }
    }
}
