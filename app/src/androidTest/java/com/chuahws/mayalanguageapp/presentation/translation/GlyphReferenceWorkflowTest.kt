package com.chuahws.mayalanguageapp.presentation.translation

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
