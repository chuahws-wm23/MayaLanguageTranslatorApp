package com.chuahws.mayalanguageapp.presentation.translation

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chuahws.mayalanguageapp.integration.glyph.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Runs ONLY local, expert-labelled staged photos. Never substitute catalogue smoke fixtures. */
@RunWith(AndroidJUnit4::class)
class IndependentGlyphPhotoTest {
    @Test fun evaluateStagedHeldOutPhotosWithActualAndroidPreprocessingAndModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val source = instrumentation.context.assets
        assumeTrue("No expert-labelled photo batch staged; no photo accuracy was measured.",
            source.list("independent_photos").orEmpty().contains("manifest.json"))
        val context = instrumentation.targetContext
        val manifest = JSONObject(source.open("independent_photos/manifest.json").bufferedReader().use { it.readText() })
        require(manifest.getInt("format_version") == 1)
        val indexBytes = context.assets.open("maya_twkm_logogram_index.json").use { it.readBytes() }
        require(sha(indexBytes) == manifest.getString("catalogue_index_sha256"))
        val words = requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val sounds = requireNotNull(GlyphVisualCatalogue.fromAssetsOrNull(context))
        val samples = manifest.getJSONArray("samples")
        require(samples.length() in 1..5000)
        val predictions = JSONArray()
        val folder = File(context.cacheDir, "images").apply { mkdirs() }
        for (i in 0 until samples.length()) {
            val row = samples.getJSONObject(i)
            val name = row.getString("file")
            require(name.matches(Regex("photo-[0-9]{5}\\.(png|jpg|jpeg|webp)")))
            val bytes = source.open("independent_photos/$name").use { it.readBytes() }
            require(sha(bytes) == row.getString("sha256"))
            val file = File(folder, name)
            val outcome = JSONObject().put("file", name)
                .put("expected_sign", row.getString("expected_sign"))
                .put("source_group", row.getString("source_group"))
                .put("confirmed_identification", false)
            try {
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val image = GlyphImageLoader.load(context, uri)
                try {
                    val start = System.nanoTime()
                    val matches = words.search(image, sounds, 5)
                    outcome.put("elapsed_ms", (System.nanoTime() - start) / 1_000_000.0)
                    outcome.put("candidates", JSONArray().apply {
                        matches.forEach { match -> put(JSONObject()
                            .put("sign", match.reference.twkmSign)
                            .put("graph", match.reference.graph)
                            .put("similarity", match.visualOverlap.toDouble())
                            .put("identical_normalized_drawing", match.identicalNormalizedDrawing)) }
                    })
                } finally { image.recycle() }
            } catch (error: Exception) {
                outcome.put("error", error.javaClass.simpleName)
                outcome.put("candidates", JSONArray())
            } finally { file.delete() }
            predictions.put(outcome)
        }
        val report = JSONObject().put("format_version", 1)
            .put("runtime", "Android image loader and installed ONNX reference retrieval")
            .put("source_manifest_sha256", manifest.getString("source_manifest_sha256"))
            .put("catalogue_index_sha256", sha(indexBytes))
            .put("audit", manifest.getJSONObject("audit"))
            .put("predictions", predictions)
        File(context.filesDir, "reference-photo-report.json").writeText(report.toString(2))
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
