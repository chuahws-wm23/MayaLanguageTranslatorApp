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
 * Experimental FIVE-SIGN closed-set visual model, distinct from TinyGlyphNet v1.
 * The two ONNX assets were exported from the *trained* research model and
 * checked for exact SVM predictions and backbone numeric parity in GitHub Actions.
 *
 * IMPORTANT: This always picks one of five signs, EVEN for unfamiliar input.
 * It is not an expert-verified translation or an unknown-glyph detector.
 */
data class GlyphV2Result(
    val signNumber: Int,
    val reading: String,
    val englishGloss: String,
) {
    val status: String = "EXPERIMENTAL_CLOSED_SET_PREDICTION"
}

class MayaGlyphRecognizerV2 private constructor(
    private val environment: OrtEnvironment,
    private val visual: OrtSession,
    private val svm: OrtSession,
) {
    companion object {
        private const val BACKBONE = "maya_mobilenet_v2.onnx"
        private const val SVM = "maya_svm_v2.onnx"
        private const val BACKBONE_SHA256 =
            "e2887dae439a9cc39d0035d31bf55c7deeee7c1002b81d59ecc7f1e6171fc6de"
        private const val SVM_SHA256 =
            "b0b42ebd42d955063f93b492a88be06bf9d6d12461b7edb9e739e21a1cd4811b"
        private val signs = intArrayOf(32, 184, 291, 4, 44)
        private val readings = arrayOf("K'UH", "K'INICH", "SIIP", "NAAH", "TOK")
        private val meanings = arrayOf(
            "God / deity", "Resplendent / sunny", "Name of a deity",
            "First / house", "Cloud / mist",
        )

        @Volatile private var cached: MayaGlyphRecognizerV2? = null

        fun fromAssets(context: Context): MayaGlyphRecognizerV2 =
            cached ?: synchronized(this) {
                cached ?: create(context.applicationContext).also { cached = it }
            }

        private fun verifiedAsset(context: Context, name: String, sha: String): ByteArray {
            val data = context.assets.open(name).use { it.readBytes() }
            val digest = MessageDigest.getInstance("SHA-256").digest(data)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            require(digest == sha) { "Invalid Maya glyph v2 model asset: $name" }
            return data
        }

        private fun create(context: Context): MayaGlyphRecognizerV2 {
            val visualData = verifiedAsset(context, BACKBONE, BACKBONE_SHA256)
            val svmData = verifiedAsset(context, SVM, SVM_SHA256)
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions()
            options.setIntraOpNumThreads(2)
            return try {
                val visualSession = env.createSession(visualData, options)
                try {
                    val svmSession = env.createSession(svmData, options)
                    MayaGlyphRecognizerV2(env, visualSession, svmSession)
                } catch (e: Exception) {
                    visualSession.close()
                    throw e
                }
            } finally {
                options.close()
            }
        }

        private fun createTensor(env: OrtEnvironment, data: FloatArray, shape: LongArray): OnnxTensor {
            val buffer = ByteBuffer.allocateDirect(data.size * java.lang.Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            buffer.put(data)
            buffer.flip()
            return OnnxTensor.createTensor(env, buffer, shape)
        }
    }

    /** Call from Dispatchers.Default, never directly from a Compose render. */
    fun recognize(bitmap: Bitmap): GlyphV2Result {
        val tensorData = GlyphV2Preprocessor.fromBitmap(bitmap)
        val visualTensor = createTensor(
            environment, tensorData, longArrayOf(1, 3, 224, 224),
        )
        val embeddings = try {
            visual.run(mapOf("image" to visualTensor)).use { output ->
                val featureBatch = output[0].value as? Array<*>
                    ?: error("Unexpected visual model output shape.")
                val vector = featureBatch.firstOrNull() as? FloatArray
                    ?: error("Unexpected visual model output type.")
                require(vector.size == 576 && vector.all { it.isFinite() }) {
                    "Visual model produced invalid features."
                }
                vector.copyOf()
            }
        } finally {
            visualTensor.close()
        }

        val svmTensor = createTensor(environment, embeddings, longArrayOf(1, 576))
        val classIndex = try {
            svm.run(mapOf("features" to svmTensor)).use { output ->
                val label = output[0].value
                when (label) {
                    is LongArray -> label.firstOrNull()?.toInt()
                    is IntArray -> label.firstOrNull()
                    is Array<*> -> when (val first = label.firstOrNull()) {
                        is LongArray -> first.firstOrNull()?.toInt()
                        is IntArray -> first.firstOrNull()
                        else -> null
                    }
                    else -> null
                } ?: error("Unrecognised classifier output.")
            }
        } finally {
            svmTensor.close()
        }
        require(classIndex in signs.indices) {
            "Classifier returned an unsupported class."
        }
        return GlyphV2Result(signs[classIndex], readings[classIndex], meanings[classIndex])
    }
}
