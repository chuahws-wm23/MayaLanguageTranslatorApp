package com.chuahws.mayalanguageapp.integration.glyph

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * OPTIONAL FYP research probe: 25 TWKM sign IDs, not a reliable reading engine.
 *
 * Assets must be downloaded from the pinned GitHub Actions export. The normal
 * ten-class v4 stays installed and default. This classifier needs ONE isolated
 * sign drawing; YOLO glyph-BLOCK boxes can include multiple distinct signs.
 * Unknown rejection here was calibrated only on catalogue drawings.
 */
internal data class GlyphV25Result(
    val proposedSignNumber: Int?,
    val accepted: Boolean,
    val explanation: String,
)

internal object GlyphV25Similarity {
    data class Match(val same: Float, val other: Float) {
        val margin: Float get() = same - other
    }

    fun score(feature: FloatArray, signs: IntArray, gallery: FloatArray,
              dimension: Int, predicted: Int): Match {
        require(dimension > 0 && feature.size == dimension)
        require(signs.size * dimension == gallery.size && signs.size >= 2)
        require(feature.all { it.isFinite() } && gallery.all { it.isFinite() })
        var own = Float.NEGATIVE_INFINITY
        var other = Float.NEGATIVE_INFINITY
        for (row in signs.indices) {
            var sim = 0f
            for (i in 0 until dimension) {
                sim += feature[i] * gallery[row * dimension + i]
            }
            if (signs[row] == predicted) own = maxOf(own, sim)
            else other = maxOf(other, sim)
        }
        require(own.isFinite() && other.isFinite()) {
            "SVM predicted a sign missing from its reference gallery."
        }
        return Match(own, other)
    }
}

internal class MayaGlyphRecognizerV25 private constructor(
    private val env: OrtEnvironment,
    private val featureSession: OrtSession,
    private val svmSession: OrtSession,
    private val supported: Set<Int>,
    private val gallerySigns: IntArray,
    private val galleryVectors: FloatArray,
    private val dimension: Int,
    private val minSimilarity: Float,
    private val minMargin: Float,
) {
    companion object {
        private const val META = "maya_twkm25_metadata.json"
        private const val FEATURE = "maya_twkm25_feature.onnx"
        private const val SVM = "maya_twkm25_svm.onnx"
        private const val GALLERY = "maya_twkm25_gallery.bin"
        private const val INPUT_SIDE = 224

        @Volatile private var cached: MayaGlyphRecognizerV25? = null

        fun isInstalled(context: Context): Boolean = try {
            val names = context.assets.list("")?.toSet().orEmpty()
            listOf(META, FEATURE, SVM, GALLERY).all { it in names }
        } catch (_: Exception) {
            false
        }

        fun fromAssets(context: Context): MayaGlyphRecognizerV25 =
            cached ?: synchronized(this) {
                cached ?: create(context.applicationContext).also { cached = it }
            }

        private fun readVerified(context: Context, file: JSONObject): ByteArray {
            val name = file.getString("name")
            require(name in setOf(FEATURE, SVM, GALLERY)) {
                "Unexpected experimental TWKM asset: $name"
            }
            val bytes = context.assets.open(name).use { it.readBytes() }
            require(bytes.size == file.getInt("bytes")) { "Wrong experimental model size: $name" }
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            require(sha.equals(file.getString("sha256"), ignoreCase = true)) {
                "Wrong experimental model hash: $name"
            }
            return bytes
        }

        private fun create(context: Context): MayaGlyphRecognizerV25 {
            require(isInstalled(context)) {
                "Optional 25-sign model assets are not installed. " +
                    "Download the research artifact and copy four maya_twkm25_* files into app/src/main/assets."
            }
            val meta = JSONObject(context.assets.open(META).bufferedReader().use { it.readText() })
            require(meta.getInt("format_version") == 1)
            require(meta.getInt("image_side") == INPUT_SIDE)
            require(!meta.getBoolean("release_approved")) {
                "This app mode must remain research-only."
            }
            val signArray = meta.getJSONArray("supported_twkm_sign_numbers")
            val supported = (0 until signArray.length()).map { signArray.getInt(it) }.toSet()
            require(supported.size == 25) {
                "Expected exactly 25 distinct verified TWKM catalogue sign IDs."
            }
            val dim = meta.getInt("feature_dim")
            require(dim in 1..2048)
            val files = meta.getJSONObject("files")
            val feature = readVerified(context, files.getJSONObject("feature"))
            val classifier = readVerified(context, files.getJSONObject("svm"))
            val gallery = readVerified(context, files.getJSONObject("gallery"))

            val buffer = ByteBuffer.wrap(gallery).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4)
            buffer.get(magic)
            require(String(magic, Charsets.US_ASCII) == "MW25")
            require(buffer.int == 1) { "Unsupported gallery version" }
            val count = buffer.int
            val vectorSize = buffer.int
            require(count in 25..10000 && vectorSize == dim)
            require(gallery.size.toLong() == 16L + (count.toLong() * 4) +
                (count.toLong() * dim * 4L))
            val galleryLabels = IntArray(count) { buffer.int }
            require(galleryLabels.toSet() == supported)
            val vectors = FloatArray(count * dim) { buffer.float }
            require(vectors.all { it.isFinite() })

            val gate = meta.getJSONObject("rejection")
            val minSim = gate.getDouble("similarity_min").toFloat()
            val minMargin = gate.getDouble("margin_min").toFloat()
            require(minSim.isFinite() && minMargin.isFinite())
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions()
            options.setIntraOpNumThreads(2)
            return try {
                val featureSession = env.createSession(feature, options)
                try {
                    val svmSession = env.createSession(classifier, options)
                    MayaGlyphRecognizerV25(
                        env, featureSession, svmSession, supported,
                        galleryLabels, vectors, dim, minSim, minMargin,
                    )
                } catch (e: Exception) {
                    featureSession.close()
                    throw e
                }
            } finally {
                options.close()
            }
        }

        private fun tensor(env: OrtEnvironment, values: FloatArray, shape: LongArray): OnnxTensor {
            val buffer = ByteBuffer.allocateDirect(values.size * java.lang.Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            buffer.put(values)
            buffer.flip()
            return OnnxTensor.createTensor(env, buffer, shape)
        }
    }

    /** Must run on a worker dispatcher, never in a Compose render call. */
    fun recognize(bitmap: Bitmap): GlyphV25Result {
        val pixels = GlyphV2Preprocessor.fromBitmap(bitmap, INPUT_SIDE)
        val imageTensor = tensor(env, pixels, longArrayOf(1, 3, 224, 224))
        val embedding = try {
            featureSession.run(mapOf("image" to imageTensor)).use { output ->
                val batch = output[0].value as? Array<*>
                    ?: error("Unexpected 25-sign feature output shape.")
                val feature = batch.firstOrNull() as? FloatArray
                    ?: error("Unexpected 25-sign feature vector.")
                require(feature.size == dimension && feature.all { it.isFinite() })
                feature.copyOf()
            }
        } finally {
            imageTensor.close()
        }

        val featuresTensor = tensor(env, embedding, longArrayOf(1, dimension.toLong()))
        val predicted = try {
            svmSession.run(mapOf("features" to featuresTensor)).use { output ->
                when (val label = output[0].value) {
                    is LongArray -> label.firstOrNull()?.toInt()
                    is IntArray -> label.firstOrNull()
                    else -> null
                } ?: error("Unknown experimental SVM output.")
            }
        } finally {
            featuresTensor.close()
        }
        require(predicted in supported)
        val comparison = GlyphV25Similarity.score(
            embedding, gallerySigns, galleryVectors, dimension, predicted,
        )
        val accepted = comparison.same >= minSimilarity &&
            comparison.margin >= minMargin
        return if (accepted) {
            GlyphV25Result(predicted, true,
                "Experimental visual match only. Catalogue-calibrated, not independently " +
                    "verified on photographed Maya signs. A glyph BLOCK may contain multiple signs.")
        } else {
            GlyphV25Result(null, false,
                "No reliable catalogue match under the experimental screening rule. " +
                    "Try a clearer crop of one isolated sign. This does not prove the sign is unknown.")
        }
    }
}
