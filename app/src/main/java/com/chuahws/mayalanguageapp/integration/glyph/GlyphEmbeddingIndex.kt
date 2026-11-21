package com.chuahws.mayalanguageapp.integration.glyph

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.floor
import kotlin.math.sqrt

/** Numerical contract shared by the exporter and Android. Scores are NOT probabilities. */
internal object GlyphEmbeddingMath {
    const val DIMENSION = 576

    fun normalize(vector: FloatArray): FloatArray {
        require(vector.isNotEmpty() && vector.all { it.isFinite() })
        val norm = sqrt(vector.sumOf { it.toDouble() * it }).toFloat()
        require(norm.isFinite() && norm > 1e-8f) { "Empty visual embedding" }
        return FloatArray(vector.size) { vector[it] / norm }
    }

    fun cosine(unitQuery: FloatArray, gallery: FloatArray, offset: Int): Float {
        require(unitQuery.size == DIMENSION && offset >= 0 && offset + DIMENSION <= gallery.size)
        var dot = 0f
        for (i in unitQuery.indices) dot += unitQuery[i] * gallery[offset + i]
        return dot.coerceIn(-1f, 1f)
    }

    /** Inverted binary artwork -> float bilinear resize -> ImageNet NCHW. */
    fun tensor(bits: ByteArray): FloatArray {
        require(bits.size == 512)
        val side = 224
        val plane = side * side
        val means = floatArrayOf(.485f, .456f, .406f)
        val stds = floatArrayOf(.229f, .224f, .225f)
        fun value(x: Int, y: Int): Float {
            val i = y * 64 + x
            return if ((bits[i ushr 3].toInt() and (0x80 ushr (i and 7))) != 0) 0f else 1f
        }
        val out = FloatArray(3 * plane)
        for (y in 0 until side) for (x in 0 until side) {
            val fx = ((x + .5) * 64 / side - .5).coerceIn(0.0, 63.0)
            val fy = ((y + .5) * 64 / side - .5).coerceIn(0.0, 63.0)
            val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
            val x1 = minOf(63, x0 + 1); val y1 = minOf(63, y0 + 1)
            val dx = (fx - x0).toFloat(); val dy = (fy - y0).toFloat()
            val top = value(x0, y0) * (1 - dx) + value(x1, y0) * dx
            val bottom = value(x0, y1) * (1 - dx) + value(x1, y1) * dx
            val pixel = top * (1 - dy) + bottom * dy
            for (c in 0..2) out[c * plane + y * side + x] = (pixel - means[c]) / stds[c]
        }
        return out
    }
}

/** Reuses the shipped pretrained MobileNet features; no five-class SVM is run. */
internal class GlyphEmbeddingIndex private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val vectors: Map<String, FloatArray>,
) {
    companion object {
        private const val MODEL = "maya_mobilenet_v2.onnx"
        @Volatile private var cached: GlyphEmbeddingIndex? = null

        fun fromAssets(context: Context): GlyphEmbeddingIndex = cached ?: synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }

        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

        private fun load(context: Context): GlyphEmbeddingIndex {
            val meta = JSONObject(context.assets.open("maya_reference_embeddings.json").bufferedReader().use { it.readText() })
            require(meta.getInt("format_version") == 1 && meta.getInt("dimension") == 576)
            require(meta.getString("preprocessing") == "binary64_float_bilinear224_imagenet_nchw_v1")
            val model = context.assets.open(MODEL).use { it.readBytes() }
            // Metadata also binds every row to the precise catalogue order and image bytes.
            require(sha(model) == meta.getString("model_sha256"))
            require(meta.getString("model_sha256") ==
                "e2887dae439a9cc39d0035d31bf55c7deeee7c1002b81d59ecc7f1e6171fc6de")
            val galleries = meta.getJSONObject("galleries")
            val vectors = listOf("logogram", "phonogram").associateWith { kind ->
                val row = galleries.getJSONObject(kind)
                val prefix = "maya_twkm_${kind}"
                val index = context.assets.open("${prefix}_index.json").use { it.readBytes() }
                val artwork = context.assets.open("${prefix}_bitmap.bin").use { it.readBytes() }
                require(sha(index) == row.getString("index_sha256"))
                require(sha(artwork) == row.getString("artwork_sha256"))
                val raw = context.assets.open("maya_${kind}_embeddings.bin").use { it.readBytes() }
                require(sha(raw) == row.getString("vectors_sha256"))
                val count = row.getInt("count")
                require(count in 1..5000 && raw.size == count * 576 * 4)
                require(JSONObject(String(index, Charsets.UTF_8)).getInt("count") == count)
                val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                FloatArray(count * 576) { buffer.float }.also { values ->
                    require(values.all { it.isFinite() })
                    for (i in 0 until count) {
                        var norm = 0.0
                        for (d in 0 until 576) norm += values[i * 576 + d].toDouble() * values[i * 576 + d]
                        require(norm in .999..1.001)
                    }
                }
            }
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions()
            options.setIntraOpNumThreads(2)
            return try { GlyphEmbeddingIndex(env, env.createSession(model, options), vectors) }
            finally { options.close() }
        }
    }

    /** Worker thread only. A query runs once, then all reference rows are dot products. */
    fun similarities(queryBits: ByteArray, kind: String): FloatArray {
        val gallery = vectors.getValue(kind)
        val input = GlyphEmbeddingMath.tensor(queryBits)
        val buffer = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(input); buffer.flip()
        val query = OnnxTensor.createTensor(env, buffer, longArrayOf(1, 3, 224, 224)).use { tensor ->
            session.run(mapOf("image" to tensor)).use { result ->
                val batch = result[0].value as? Array<*> ?: error("Invalid visual feature output")
                val features = batch.firstOrNull() as? FloatArray ?: error("Invalid visual features")
                require(features.size == 576)
                GlyphEmbeddingMath.normalize(features)
            }
        }
        return FloatArray(gallery.size / 576) { GlyphEmbeddingMath.cosine(query, gallery, it * 576) }
    }
}
