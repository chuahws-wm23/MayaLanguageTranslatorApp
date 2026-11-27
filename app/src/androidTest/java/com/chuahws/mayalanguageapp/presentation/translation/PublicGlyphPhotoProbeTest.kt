package com.chuahws.mayalanguageapp.presentation.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chuahws.mayalanguageapp.integration.glyph.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt

/** Public photographs: source-described readings are not expert-verified sign labels. */
@RunWith(AndroidJUnit4::class)
class PublicGlyphPhotoProbeTest {
    @Test fun recordPublicPhotographDevelopmentProbeWithoutInventingAccuracy() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val assets=instrumentation.context.assets
        assumeTrue("No public-photo probe staged; no photograph evaluation was measured.",
            assets.list("public_photo_probe").orEmpty().contains("manifest.json"))
        val manifestBytes=assets.open("public_photo_probe/manifest.json").use { it.readBytes() }
        val manifest=JSONObject(String(manifestBytes,Charsets.UTF_8))
        require(!manifest.getBoolean("independent_expert_verified_test"))
        val rows=manifest.getJSONArray("samples")
        val words=requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val sounds=requireNotNull(GlyphVisualCatalogue.fromAssetsOrNull(context))
        val drawingHashes=mutableSetOf<String>()
        for(indexAsset in listOf(GlyphEnglishMeaningCatalogue.INDEX_ASSET,GlyphVisualCatalogue.INDEX_ASSET)) {
            val entries=JSONObject(context.assets.open(indexAsset).bufferedReader().use { it.readText() }).getJSONArray("entries")
            for(i in 0 until entries.length()) {
                val entry=entries.getJSONObject(i)
                drawingHashes.add(entry.optString("sha",entry.optString("sha256")))
            }
        }
        val results=JSONArray()
        var errors=0
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            val name=row.getString("file")
            require(name.matches(Regex("[a-z0-9-]+\\.(jpg|png)")))
            val bytes=assets.open("public_photo_probe/"+name).use { it.readBytes() }
            require(sha(bytes)==row.getString("sha256"))
            require(sha(bytes) !in drawingHashes)
            require(!row.getBoolean("label_is_expert_verified_exact_sign"))
            val outcome=JSONObject(row.toString())
            val folder=File(context.cacheDir,"images").apply { mkdirs() }
            val file=File(folder,"public-probe-"+name)
            val model=GlyphRecognitionViewModel(context.applicationContext as Application)
            var image: Bitmap?=null
            try {
                file.writeBytes(bytes)
                val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",file)
                instrumentation.runOnMainSync { model.load(uri) }
                awaitIdle(model)
                val loaded=requireNotNull(model.state.value.bitmap) { model.state.value.message ?: "Decode failed" }
                image=loaded
                outcome.put("loaded_width",loaded.width).put("loaded_height",loaded.height)
                val start=System.nanoTime()
                instrumentation.runOnMainSync { model.compareReference() }
                awaitIdle(model)
                val state=model.state.value
                val matches=state.referenceMatches.orEmpty()
                outcome.put("production_elapsed_ms",(System.nanoTime()-start)/1_000_000.0)
                    .put("production_message",state.message ?: JSONObject.NULL)
                    .put("production_candidates",candidates(matches))
                    .put("production_decision", matches.referenceDecision().name)
                    .put("production_visible_sign", matches.displayableReference()?.reference?.twkmSign ?: JSONObject.NULL)
                    .put("photo_hints",JSONArray(state.referenceHints))
                addReadingCompatibility(outcome,"production",matches,row)
                val mask=sounds.canonicalBitplane(loaded)
                outcome.put("production_bitplane_base64",mask?.let { Base64.encodeToString(it,Base64.NO_WRAP) } ?: JSONObject.NULL)
                // Existing illumination correction is a research control only.
                // It is NOT enabled in the shipped reference-search path.
                val corrected=illuminationControl(loaded)
                try {
                    val blocked=GlyphPanelGuard.looksLikeDensePanel(corrected)
                    val alternatives=if(blocked)emptyList() else words.search(corrected,sounds,5)
                    outcome.put("research_illumination_candidates",candidates(alternatives))
                        .put("research_illumination_dense_panel_guard",blocked)
                    addReadingCompatibility(outcome,"research_illumination",alternatives,row)
                    outcome.put("research_illumination_bitplane_base64",sounds.canonicalBitplane(corrected)?.let {
                        Base64.encodeToString(it,Base64.NO_WRAP)
                    } ?: JSONObject.NULL)
                } finally { corrected.recycle() }
                instrumentation.runOnMainSync { model.scanPicture() }
                awaitIdle(model)
                val automatic = model.state.value
                val visibleReferenceLists = if (automatic.activeMode == "multiple") {
                    automatic.multiple.map { it.englishMatches }
                } else listOf(automatic.referenceMatches.orEmpty())
                outcome.put("automatic_visible_signs", JSONArray(visibleReferenceLists.mapNotNull {
                    it.displayableReference()?.reference?.twkmSign
                })).put("automatic_decisions", JSONArray(visibleReferenceLists.map { it.referenceDecision().name }))
                    .put("automatic_candidates_by_region", JSONArray(visibleReferenceLists.map { candidates(it) }))
                    .put("automatic_region_count", automatic.multiple.size)
                    .put("automatic_message", automatic.message ?: JSONObject.NULL)
                // This diagnostic also records the experimental detector fallbacks.
                val trained=GlyphV18Detector.fromAssetsOrNull(context)?.detect(loaded).orEmpty()
                val whitespace=if(trained.isEmpty())GlyphWhitespaceDetector.detect(loaded) else emptyList()
                val regions=when {
                    trained.isNotEmpty() -> trained
                    whitespace.isNotEmpty() -> whitespace
                    else -> GlyphMultiDetector.detect(loaded)
                }
                outcome.put("region_proposal_count",regions.size)
                    .put("region_proposals",JSONArray().apply { regions.forEach { region ->
                        put(JSONArray(listOf(region.crop.left,region.crop.top,region.crop.right,region.crop.bottom)))
                    } })
                    .put("region_ground_truth_available",false)
            } catch(error: Exception) {
                errors++
                outcome.put("runtime_error",error.javaClass.simpleName+": "+error.message)
                    .put("production_candidates",JSONArray())
            } finally {
                instrumentation.runOnMainSync { model.clear() }
                image?.recycle()
                file.delete()
            }
            results.put(outcome)
        }
        val embeddingManifest=context.assets.open("maya_reference_embeddings.json").use { it.readBytes() }
        val report=JSONObject().put("format_version",1)
            .put("study","Public-photograph development probe; not expert-verified photo accuracy")
            .put("runtime","Production Android image loader, single-photo ViewModel and bundled ONNX retrieval")
            .put("manifest_sha256",sha(manifestBytes))
            .put("embedding_manifest_sha256",sha(embeddingManifest))
            .put("model_sha256",JSONObject(String(embeddingManifest,Charsets.UTF_8)).getString("model_sha256"))
            .put("source_groups",manifest.getInt("source_groups"))
            .put("inputs",rows.length()).put("runtime_errors",errors)
            .put("research_illumination_control_is_enabled_in_app",false)
            .put("release_approved",false).put("results",results)
        val output=requireNotNull(context.getExternalFilesDir("screenshots"))
        File(output,"public-photo-development-report.json").writeText(report.toString(2))
        // Gate pipeline integrity, not fabricated recognition success.
        assertEquals("Photo decoding or model execution failed; inspect saved report",0,errors)
    }

    private fun awaitIdle(model: GlyphRecognitionViewModel)=runBlocking {
        withTimeout(60_000) { while(model.state.value.busy)delay(25) }
    }

    private fun candidates(matches: List<GlyphEnglishMatch>)=JSONArray().apply {
        matches.forEach { match -> put(JSONObject().put("sign",match.reference.twkmSign)
            .put("reading",match.reference.reading).put("english",match.reference.english)
            .put("graph",match.reference.graph).put("similarity",match.visualOverlap.toDouble())
            .put("identical_normalized_drawing",match.identicalNormalizedDrawing)
            .put("stroke_agreement", match.strokeAgreement?.toDouble() ?: JSONObject.NULL)) }
    }

    private fun addReadingCompatibility(outcome: JSONObject,prefix: String,matches: List<GlyphEnglishMatch>,row: JSONObject) {
        val expected=row.getJSONArray("expected_readings")
        if(expected.length()==0)return
        val readings=(0 until expected.length()).map { expected.getString(it) }.toSet()
        fun compatible(match: GlyphEnglishMatch)=match.reference.reading.split(" / ").any { it in readings }
        outcome.put(prefix+"_top1_contains_source_reading",matches.firstOrNull()?.let { compatible(it) } ?: false)
            .put(prefix+"_top5_contains_source_reading",matches.any { compatible(it) })
    }

    private fun illuminationControl(original: Bitmap): Bitmap {
        val scale=minOf(1f,1024f/maxOf(original.width,original.height))
        val small=if(scale<1f)Bitmap.createScaledBitmap(original,
            (original.width*scale).roundToInt().coerceAtLeast(25),
            (original.height*scale).roundToInt().coerceAtLeast(25),true) else original
        try {
            val pixels=IntArray(small.width*small.height)
            small.getPixels(pixels,0,small.width,0,0,small.width,small.height)
            val gray=IntArray(pixels.size) { i ->
                val p=pixels[i];(Color.red(p)*77+Color.green(p)*150+Color.blue(p)*29+128) shr 8
            }
            val corrected=GlyphV4PhotoNormalizer.correct(gray,small.width,small.height)
            val colors=IntArray(corrected.size) { Color.rgb(corrected[it],corrected[it],corrected[it]) }
            return Bitmap.createBitmap(colors,small.width,small.height,Bitmap.Config.ARGB_8888)
        } finally { if(small!==original)small.recycle() }
    }

    private fun sha(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
