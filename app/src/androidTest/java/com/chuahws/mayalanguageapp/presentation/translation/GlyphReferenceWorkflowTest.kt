package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chuahws.mayalanguageapp.domain.model.GlyphScanCapture
import com.chuahws.mayalanguageapp.integration.glyph.*
import com.chuahws.mayalanguageapp.presentation.theme.MayaTranslateTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/** Reference-only runtime smoke tests. These do NOT measure novel-photo accuracy. */
@RunWith(AndroidJUnit4::class)
class GlyphReferenceWorkflowTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun uploadedReferenceRetrievesItsActualArtworkAndFiveDistinctAlternatives() {
        val words = requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val sounds = requireNotNull(GlyphVisualCatalogue.fromAssetsOrNull(context))
        // Test actual licensed images, not a hardcoded prediction or fake confidence.
        listOf(32, 44, 548).forEach { sign ->
            val reference = words.references.first { it.twkmSign == sign }
            val original = words.imageFor(reference)
            val folder = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File(folder, "glyph-runtime-$sign.png")
            try {
                file.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val loaded = GlyphImageLoader.load(context, uri)
                try {
                    val matches = words.search(loaded, sounds, 5)
                    assertEquals(5, matches.size)
                    assertEquals(5, matches.map { it.reference.twkmSign }.distinct().size)
                    assertEquals(sign, matches.first().reference.twkmSign)
                    assertTrue(matches.first().identicalNormalizedDrawing)
                    assertEquals(reference.graph, matches.first().reference.graph)
                    assertTrue(matches.all { it.visualOverlap.isFinite() })
                } finally { if (loaded !== original) loaded.recycle() }
            } finally { original.recycle(); file.delete() }
        }
    }

    @Test fun comparisonUiShowsFiveImagesAndClearsThemWhenPhotoChangesInBothThemes() {
        val words = requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val reference = words.references.first { it.twkmSign == 32 }
        val photo = words.imageFor(reference)
        val blank = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val selected = mutableStateOf(photo)
        val dark = mutableStateOf(false)
        var saved: GlyphScanCapture? = null
        compose.setContent {
            MayaTranslateTheme(darkTheme = dark.value) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EnglishMeaningExploreCard(selected.value, GlyphCrop(), busy = false,
                        matchOnly = true, onSearchCompleted = { saved = it })
                }
            }
        }
        compose.onNodeWithText("Compare with the archive").performClick()
        compose.waitUntil(60_000) { saved != null }
        compose.onAllNodes(numericSimilarity).assertCountEquals(5)
        assertEquals(5, saved!!.candidates.size)
        compose.onNodeWithContentDescription("Reference drawing for ${reference.reading}")
            .assertExists()
        screenshot("reference-results-light")
        compose.runOnIdle { dark.value = true }
        compose.onAllNodes(numericSimilarity).assertCountEquals(5)
        screenshot("reference-results-dark")
        compose.runOnIdle { selected.value = blank; saved = null }
        compose.waitForIdle()
        compose.onAllNodes(numericSimilarity).assertCountEquals(0)
        compose.onNodeWithText("Compare with the archive").performScrollTo().performClick()
        compose.waitUntil(60_000) { saved != null }
        assertTrue(saved!!.candidates.isEmpty())
        compose.onNodeWithText("No usable sign strokes found.", substring = true).assertExists()
        // Bitmaps remain alive until the Compose test activity is disposed.
    }
    @Test fun separatedReferencePanelsReturnOneCorrectReferencePerDetectedRegion() {
        val words = requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val sounds = requireNotNull(GlyphVisualCatalogue.fromAssetsOrNull(context))
        val signs = listOf(32,44,548,4)
        for ((rows, columns) in listOf(2 to 2, 1 to 3)) {
            val width=columns*176+48; val height=rows*176+48
            val panel=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(panel);canvas.drawColor(Color.WHITE)
            try {
                for(i in 0 until rows*columns) {
                    val ref=words.references.first { it.twkmSign==signs[i] }
                    val image=words.imageFor(ref)
                    try {
                        val x=48+(i%columns)*176;val y=48+(i/columns)*176
                        canvas.drawBitmap(image,null,Rect(x,y,x+128,y+128),null)
                    } finally { image.recycle() }
                }
                val regions=GlyphWhitespaceDetector.detect(panel)
                assertEquals("Wrong region count for $rows x $columns reference fixture",rows*columns,regions.size)
                regions.forEachIndexed { index, region ->
                    assertEquals(index/columns+1,region.row)
                    assertEquals(index%columns+1,region.column)
                    val(x,y,w,h)=region.crop.pixels(width,height)
                    val crop=Bitmap.createBitmap(panel,x,y,w,h)
                    try {
                        val matches=words.search(crop,sounds,5)
                        assertEquals("Wrong sign for reference-fixture region ${index+1}",
                            signs[index],matches.first().reference.twkmSign)
                        assertEquals(5,matches.size)
                    } finally { if(crop !== panel)crop.recycle() }
                }
            } finally { panel.recycle() }
        }
        val blank=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try { assertTrue(GlyphWhitespaceDetector.detect(blank).isEmpty()) } finally { blank.recycle() }
    }

    @Test fun recordWholeCataloguePanelDiagnosticWithoutCallingItPhotoAccuracy() {
        val words=requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val sounds=requireNotNull(GlyphVisualCatalogue.fromAssetsOrNull(context))
        val outcomes=JSONArray()
        var expectedCount=0;var proposedCount=0;var matchedCount=0;var top1=0;var top5=0
        for (batch in words.distinctSigns.chunked(4)) {
            val width=400;val height=400
            val panel=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(panel);canvas.drawColor(Color.WHITE)
            val truths=mutableListOf<GlyphCrop>()
            try {
                batch.forEachIndexed { i, ref ->
                    val image=words.imageFor(ref)
                    try {
                        val px=IntArray(64*64);image.getPixels(px,0,64,0,0,64,64)
                        val ink=px.indices.filter { Color.red(px[it])<128 }
                        val left=ink.minOf { it%64 };val right=ink.maxOf { it%64 }+1
                        val top=ink.minOf { it/64 };val bottom=ink.maxOf { it/64 }+1
                        val x=48+(i%2)*176;val y=48+(i/2)*176
                        canvas.drawBitmap(image,null,Rect(x,y,x+128,y+128),null)
                        truths.add(GlyphCrop((x+2*left)/400f,(y+2*top)/400f,
                            (x+2*right)/400f,(y+2*bottom)/400f))
                    } finally { image.recycle() }
                }
                val regions=GlyphWhitespaceDetector.detect(panel)
                expectedCount+=batch.size;proposedCount+=regions.size
                val unused=regions.indices.toMutableSet()
                batch.forEachIndexed { i, ref ->
                    val candidate=unused.maxByOrNull { overlap(truths[i],regions[it].crop) }
                    val paired=candidate?.takeIf { overlap(truths[i],regions[it].crop)>=.5f }
                    val row=JSONObject().put("expected_sign",ref.twkmSign).put("graph",ref.graph)
                    if(paired==null) {
                        row.put("detected",false).put("top5",JSONArray())
                    } else {
                        unused.remove(paired);matchedCount++
                        val region=regions[paired]
                        val(x,y,w,h)=region.crop.pixels(width,height)
                        val crop=Bitmap.createBitmap(panel,x,y,w,h)
                        val result=try { GlyphRegionMatcher.compare(crop,words,sounds) }
                            finally { if(crop !== panel)crop.recycle() }
                        val signs=result.matches.map { it.reference.twkmSign }
                        if(signs.firstOrNull()==ref.twkmSign)top1++
                        if(ref.twkmSign in signs)top5++
                        row.put("detected",true).put("iou",overlap(truths[i],region.crop).toDouble())
                            .put("top5",JSONArray(signs)).put("note",result.note)
                    }
                    outcomes.put(row)
                }
            } finally { panel.recycle() }
        }
        assertEquals(words.distinctSigns.size,expectedCount)
        val report=JSONObject().put("study","Constructed panels from existing reference drawings; not independent photos")
            .put("expected_regions",expectedCount).put("proposed_regions",proposedCount)
            .put("correct_regions_iou_at_least_0_5",matchedCount)
            .put("correct_detected_and_top1",top1).put("correct_detected_and_top5",top5)
            .put("outcomes",outcomes)
        File(requireNotNull(context.getExternalFilesDir("screenshots")),"catalogue-multi-diagnostic.json")
            .writeText(report.toString(2))
    }

    private fun overlap(a: GlyphCrop,b: GlyphCrop): Float {
        val intersection=maxOf(0f,minOf(a.right,b.right)-maxOf(a.left,b.left))*
            maxOf(0f,minOf(a.bottom,b.bottom)-maxOf(a.top,b.top))
        val union=(a.right-a.left)*(a.bottom-a.top)+(b.right-b.left)*(b.bottom-b.top)-intersection
        return if(union>0f)intersection/union else 0f
    }

    private val numericSimilarity = SemanticsMatcher("numeric appearance similarity") { node ->
        node.config.contains(SemanticsProperties.Text) &&
            node.config[SemanticsProperties.Text].any { text ->
                text.text.startsWith("Similarity ") &&
                    text.text.removePrefix("Similarity ").toFloatOrNull() != null
            }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val folder = requireNotNull(context.getExternalFilesDir("screenshots"))
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

}
