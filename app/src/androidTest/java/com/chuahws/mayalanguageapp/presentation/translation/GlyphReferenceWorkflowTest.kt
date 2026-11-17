package com.chuahws.mayalanguageapp.presentation.translation

import androidx.compose.ui.geometry.Offset
import com.chuahws.mayalanguageapp.presentation.profile.ProfileScreen
import com.chuahws.mayalanguageapp.domain.model.UserProfile
import android.graphics.Matrix
import android.app.Application
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.presentation.dictionary.GlyphDictionaryHubScreen
import com.chuahws.mayalanguageapp.presentation.home.HomeScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chuahws.mayalanguageapp.presentation.history.HistoryScreen
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.model.GlyphScanCandidate
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

    @Test fun learningLessonsQuizAndNumberChallengeWorkAndSaveProgress() {
        fun learningText(text: String, substring: Boolean = false): SemanticsNodeInteraction {
            compose.onNodeWithTag("learning-content").performScrollToNode(hasText(text, substring = substring))
            return compose.onNodeWithText(text, substring = substring)
        }
        val learner = "runtime-learning"
        val preferences = context.getSharedPreferences("maya_learning_$learner", 0)
        preferences.edit().clear().commit()
        compose.setContent { MayaTranslateTheme { com.chuahws.mayalanguageapp.presentation.learn.LearnScreen(learner) } }
        learningText("Discover Maya writing").assertIsDisplayed()
        screenshot("learning-hub")
        learningText("Words and syllables").performClick()
        learningText("Mark lesson complete").performClick()
        learningText("1 of 4 lessons completed", substring = true).assertIsDisplayed()
        assertTrue(preferences.getStringSet("completed", emptySet())!!.contains("signs"))
        learningText("Practise with a quiz").performClick()
        com.chuahws.mayalanguageapp.presentation.learn.LearningContent.questions.forEachIndexed { index, q ->
            learningText(q.choices[q.answer]).performClick()
            learningText("Correct", substring = false).assertIsDisplayed()
            if (index == 0) screenshot("learning-quiz-feedback")
            learningText(if (index == 7) "See results" else "Next question").performClick()
        }
        learningText("Practice complete").assertIsDisplayed()
        assertEquals(8, preferences.getInt("quiz_best", 0))
        learningText("← Learning hub").performClick()
        learningText("Open numbers playground").performClick()
        learningText("Challenge").performClick()
        learningText("Check number").performClick()
        learningText("Keep trying").assertIsDisplayed()
        repeat(3) { learningText("− one").performClick() }
        learningText("− one").assertIsNotEnabled()
        repeat(17) { learningText("+ one").performClick() }
        learningText("Check number").performClick()
        learningText("Correct — you built 37").assertIsDisplayed()
        screenshot("learning-number-challenge")
        preferences.edit().clear().commit()
    }

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
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("Reference drawing for ${reference.reading}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Reference drawing for ${reference.reading}").assertExists()
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

    @Test fun multiResultsShowDetectedRegionsWithoutManualCropControls() {
        val words=requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val sounds=requireNotNull(GlyphVisualCatalogue.fromAssetsOrNull(context))
        val panel=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(panel);canvas.drawColor(Color.WHITE)
        listOf(32,44,548,4).forEachIndexed { i, sign ->
            val image=words.imageFor(words.references.first { it.twkmSign==sign })
            try {
                val x=48+(i%2)*176;val y=48+(i/2)*176
                canvas.drawBitmap(image,null,Rect(x,y,x+128,y+128),null)
            }
            finally { image.recycle() }
        }
        val regions=GlyphWhitespaceDetector.detect(panel)
        assertEquals(4,regions.size)
        val items=regions.mapIndexed { i,region ->
            val(x,y,w,h)=region.crop.pixels(panel.width,panel.height)
            val crop=Bitmap.createBitmap(panel,x,y,w,h)
            val comparison=try { GlyphRegionMatcher.compare(crop,words,sounds) }
                finally { if(crop !== panel)crop.recycle() }
            MultiGlyphItem(i+1,region,null,null,comparison.note,englishMatches=comparison.matches)
        }
        compose.setContent {
            MayaTranslateTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MultiGlyphResultsPanel(panel,items)
                }
            }
        }
        compose.onNodeWithContentDescription("Scan image with 4 numbered glyph boxes").assertExists()
        compose.onNodeWithContentDescription("Detected glyph region 1").assertExists()
        compose.onAllNodesWithText("Inspect / correct crop").assertCountEquals(0)
        for(number in 1..4)compose.onNodeWithContentDescription("Show region $number").assertIsDisplayed()
        screenshot("multi-region-review")
        compose.onNodeWithContentDescription("Show region 4").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Detected glyph region 4").assertExists()

    }

    @Test fun scannerBoxTapsAndRegionControlsStayInSync() {
        val words=requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val panel=Bitmap.createBitmap(400,224,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(panel);canvas.drawColor(Color.WHITE)
        listOf(32,44).forEachIndexed { i, sign ->
            val image=words.imageFor(words.references.first { it.twkmSign==sign })
            try { canvas.drawBitmap(image,null,Rect(48+i*176,48,176+i*176,176),null) }
            finally { image.recycle() }
        }
        val folder=File(context.cacheDir,"images").apply { mkdirs() }
        val file=File(folder,"interactive-regions.png")
        try { file.outputStream().use { panel.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { panel.recycle() }
        val model=GlyphRecognitionViewModel(context.applicationContext as Application)
        compose.setContent { MayaTranslateTheme { GlyphPhotoScreen(
            onRecordGlyphScan={_,_->},onToggleGlyphFavorite={},savedGlyphIds=emptySet(),
            onOpenHistory={},onOpenFavourites={},glyphModel=model) } }
        try {
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
            compose.runOnIdle { model.load(uri) }
            compose.waitUntil(30_000) { model.state.value.bitmap!=null && !model.state.value.busy }
            compose.onNodeWithText("Multiple glyphs").performScrollTo().performClick()
            compose.waitUntil(60_000) { model.state.value.multiple.size==2 && !model.state.value.busy }
            val overlay=compose.onNodeWithContentDescription("Scan image with 2 numbered glyph boxes")
            overlay.performScrollTo()
            val image=requireNotNull(model.state.value.bitmap)
            val target=model.state.value.multiple[1].region.crop
            overlay.performTouchInput {
                val viewport=GlyphImageViewport.fit(image.width,image.height,width.toFloat(),height.toFloat())
                click(Offset(viewport.left+(target.left+target.right)/2*viewport.width,
                    viewport.top+(target.top+target.bottom)/2*viewport.height))
            }
            overlay.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Region 2 selected"))
            compose.onAllNodesWithContentDescription("Show region 2").assertCountEquals(1)
            compose.onNodeWithContentDescription("Show region 2").assertIsSelected()
            compose.onNodeWithContentDescription("Detected glyph region 2").assertExists()
            screenshot("scanner-tappable-regions")
            compose.onAllNodesWithContentDescription("Previous region").assertCountEquals(0)
            compose.onAllNodesWithContentDescription("Next region").assertCountEquals(0)
            // The chips live inside a horizontal list. First reveal the result
            // vertically, then scroll the number row and tap its visible chip.
            compose.onNodeWithContentDescription("Detected glyph region 2").performScrollTo()
            compose.onNodeWithContentDescription("Show region 1").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Detected glyph region 1").assertExists()
            overlay.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Region 1 selected"))
            compose.onNodeWithContentDescription("Show region 2").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Detected glyph region 2").performScrollTo().assertExists()
            screenshot("scanner-region-result-controls")
            overlay.performScrollTo().performTouchInput { click(Offset(5f,5f)) }
            overlay.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Region 2 selected"))
        } finally { file.delete() }
    }

    @Test fun historyShowsPictureMeaningAndSearchableScanDetails() {
        val words=requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val reference=words.references.first { it.twkmSign==32 }
        val picture=words.imageFor(reference)
        val packed=try { GlyphScanPreviewCodec.encode(picture,GlyphCrop()) } finally { picture.recycle() }
        val record=TranslationRecord(translationId="history-ui-fixture",userId="fixture",glyphScanRecord=true,
            glyphImageBase64=packed,glyphCandidates=listOf(GlyphScanCandidate(
                twkmSign=reference.twkmSign,reading=reference.reading,englishGloss=reference.english)))
        compose.setContent { MayaTranslateTheme { HistoryScreen(listOf(record),onDelete={}) } }
        compose.onNodeWithText(reference.english).assertExists()
        compose.onNodeWithContentDescription("Saved preview of the scanned Maya glyph").assertExists()
        screenshot("history-picture-and-meaning")
        compose.onNodeWithText("Search meaning, reading or sign number").performTextInput("no-matching-fixture")
        compose.onAllNodesWithText(reference.english).assertCountEquals(0)
        compose.onNodeWithText("Search meaning, reading or sign number").performTextClearance()
        compose.onNodeWithText("Search meaning, reading or sign number").performImeAction()
        compose.onNodeWithText(reference.english).performClick()
        compose.onNodeWithText("Scan details").assertExists()
    }

    @Test fun historyPaginatesTenEntriesResetsSearchAndClampsAfterDeletion() {
        val records=mutableStateOf((1..13).map { number -> TranslationRecord(
            translationId="paging-$number",userId="fixture",createdAtMillis=1700000000000L+number*1000L,glyphScanRecord=true,
            glyphCandidates=listOf(GlyphScanCandidate(twkmSign=number,reading="READING-$number",englishGloss="Meaning $number"))) })
        compose.setContent { MayaTranslateTheme { HistoryScreen(records.value,onDelete={ record ->
            records.value=records.value.filterNot { it.translationId==record.translationId }
        }) } }
        compose.onNodeWithText("1–10 of 13 entries").assertExists()
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.onNodeWithText("11–13 of 13 entries").assertExists()
        screenshot("history-entry-pagination")
        compose.onNodeWithText("Search meaning, reading or sign number").performTextInput("Meaning 13")
        compose.onNodeWithText("Search meaning, reading or sign number").performImeAction()
        compose.onNodeWithText("1–1 of 1 entry").assertExists()
        compose.onNode(hasText("Meaning 13") and !hasSetTextAction()).assertExists()
        compose.onNodeWithText("Search meaning, reading or sign number").performTextClearance()
        compose.onNodeWithText("Search meaning, reading or sign number").performImeAction()
        compose.onNodeWithText("1–10 of 13 entries").assertExists()
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.runOnIdle { records.value=records.value.takeLast(10) }
        compose.onNodeWithText("1–10 of 10 entries").assertExists()
        compose.onAllNodesWithContentDescription("Next page").assertCountEquals(0)
    }

    @Test fun profileRefreshKeepsEditingRecoveryAndAccountActionsWorking() {
        val profile=mutableStateOf(UserProfile(userId="profile-ui-fixture",displayName="Maya explorer",
            email="explorer@example.com",createdAtMillis=1700000000000L))
        val dark=mutableStateOf(false)
        var resetCount=0
        var signOutCount=0
        compose.setContent { MayaTranslateTheme(darkTheme=dark.value) { androidx.compose.runtime.key(dark.value) { ProfileScreen(
            profile=profile.value,email=profile.value.email,onOpenMenu={},
            onUpdateName={ profile.value=profile.value.copy(displayName=it) },
            onResetPassword={ resetCount++ },onOpenAdmin={},onSignOut={ signOutCount++ }) } } }
        compose.onNodeWithText("Your profile").assertExists()
        compose.onAllNodesWithText("Admin dashboard").assertCountEquals(0)
        screenshot("profile-archive-light")
        compose.onNodeWithContentDescription("Profile photo settings").performClick()
        compose.onNodeWithText("Choose photo").assertExists()
        screenshot("profile-photo-settings")
        compose.onNodeWithContentDescription("Back to profile").performClick()
        compose.onNodeWithText("Edit profile").performClick()
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        compose.onNodeWithText("Display name").performTextReplacement("  Updated explorer  ")
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Updated explorer",profile.value.displayName) }
        compose.onNodeWithText("Password recovery").performScrollTo().performClick()
        compose.onNodeWithText("Send password reset link").performScrollTo().performClick()
        assertEquals(1,resetCount)
        compose.onNodeWithContentDescription("Back to profile").performClick()
        compose.onNodeWithText("About the archive").performScrollTo().performClick()
        compose.onNodeWithText("Photo recognition is experimental.",substring=true).assertExists()
        compose.onNodeWithContentDescription("Back to profile").performClick()
        // Render the second palette with fresh nodes so screenshot capture
        // does not omit cached child layers during an in-place theme switch.
        compose.runOnIdle { dark.value=true }
        compose.onNodeWithText("Updated explorer").assertIsDisplayed()
        compose.onNodeWithText("explorer@example.com").assertIsDisplayed()
        compose.onNodeWithContentDescription("Default profile photo").assertIsDisplayed()
        screenshot("profile-archive-dark")
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        assertEquals(1,signOutCount)
    }

    @Test fun dictionaryUsesLargeDrawingsAndOpensAlternateReferenceViews() {
        val words=requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val sounds=requireNotNull(GlyphVisualCatalogue.fromAssetsOrNull(context))
        val ref=words.distinctSigns.first()
        val count=words.references.count { it.twkmSign==ref.twkmSign }
        compose.setContent { MayaTranslateTheme { GlyphDictionaryHubScreen() } }
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("Next page").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.onNodeWithText("2").assertExists()
        compose.onNodeWithText("Search meaning, reading or number").performTextInput(ref.twkmSign.toString())
        compose.onNodeWithText("Search meaning, reading or number").performImeAction()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("Reference drawing for ${ref.english}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(ref.english).performScrollTo()
        compose.onNodeWithContentDescription("Reference drawing for ${ref.english}", useUnmergedTree = true)
            .assertHeightIsAtLeast(120.dp)
        screenshot("dictionary-large-word-cards")
        compose.onNodeWithText(ref.english).performClick()
        compose.onNodeWithText("Drawing 1 of $count").assertExists()
        compose.onNodeWithContentDescription("Next drawing").performClick()
        compose.onNodeWithText("Drawing 2 of $count").assertExists()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("Reference drawing for ${ref.english}").fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("dictionary-enlarged-reference", dialog = true)
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Syllables").performClick()
        compose.waitForIdle()
        val syllable=sounds.syllables.first()
        compose.onNodeWithText(syllable).performScrollTo()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("Reference drawing for $syllable").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Reference drawing for $syllable", useUnmergedTree = true)
            .assertHeightIsAtLeast(120.dp)
        screenshot("dictionary-large-syllables")
        compose.onNodeWithText("Search syllable or sign number").performTextInput("zzzzzz-fixture")
        compose.onAllNodesWithText(syllable).assertCountEquals(0)
    }

    @Test fun refreshedHomeKeepsItsPrimaryScanActionAccessible() {
        var scanned=false
        compose.setContent { MayaTranslateTheme { HomeScreen(
            displayName="Maya explorer",onImageTranslation={ scanned=true },
            onSaved={},onHistory={},onAnalytics={},onFeedback={},onDictionary={},onProfile={}) } }
        compose.onNodeWithText("Scan a glyph").assertExists()
        screenshot("home-archive-design")
        compose.onNodeWithText("Scan a glyph").performClick()
        assertTrue(scanned)
    }

    @Test fun scannerOnlyOffersSingleOrMultipleAfterPhotoAndShowsNearestBelowNoMatch() {
        val words=requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val original=words.imageFor(words.references.first { it.twkmSign==32 })
        val rotated=Bitmap.createBitmap(original,0,0,original.width,original.height,
            Matrix().apply { postRotate(11f) },true)
        val folder=File(context.cacheDir,"images").apply { mkdirs() }
        val file=File(folder,"scanner-mode-fixture.png")
        try { file.outputStream().use { rotated.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { if(rotated!==original)rotated.recycle();original.recycle() }
        val model=GlyphRecognitionViewModel(context.applicationContext as Application)
        val recorded=java.util.concurrent.atomic.AtomicInteger()
        compose.setContent { MayaTranslateTheme { GlyphPhotoScreen(
            onRecordGlyphScan={_,_->recorded.incrementAndGet()},onToggleGlyphFavorite={},savedGlyphIds=emptySet(),
            onOpenHistory={},onOpenFavourites={},glyphModel=model) } }
        compose.onAllNodesWithText("Single glyph").assertCountEquals(0)
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
        compose.runOnIdle { model.load(uri) }
        compose.waitUntil(30_000) { model.state.value.bitmap!=null && !model.state.value.busy }
        compose.onNodeWithText("Multiple glyphs").assertExists()
        screenshot("scanner-photo-mode-selection")
        compose.onNodeWithText("Single glyph").performScrollTo().performClick()
        compose.waitUntil(60_000) { model.state.value.referenceMatches!=null && !model.state.value.busy }
        compose.onNodeWithText("Closest reference found").performScrollTo().assertExists()
        compose.onNodeWithText("Nearest references").assertExists()
        compose.onAllNodes(numericSimilarity).assertCountEquals(5)
        compose.onAllNodesWithText("Frame your glyph").assertCountEquals(0)
        compose.onAllNodesWithText("Reset crop").assertCountEquals(0)
        compose.onAllNodesWithText("Advanced AI models & research settings").assertCountEquals(0)
        val reading=model.state.value.referenceMatches!!.first().reference.reading
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("Reference drawing for $reading").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("Reference drawing for $reading")[0].performScrollTo()
        screenshot("scanner-simple-nearest-results")
        compose.waitUntil(20_000) { recorded.get()==1 }
        val exactPhoto=words.imageFor(words.references.first { it.twkmSign==32 })
        try { file.outputStream().use { exactPhoto.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { exactPhoto.recycle() }
        compose.runOnIdle { model.load(uri) }
        compose.waitUntil(30_000) { model.state.value.bitmap!=null && !model.state.value.busy }
        compose.onAllNodes(numericSimilarity).assertCountEquals(0)
        compose.onNodeWithText("Single glyph").performScrollTo().performClick()
        compose.waitUntil(60_000) { model.state.value.referenceMatches!=null && !model.state.value.busy }
        compose.onNodeWithText("Reference drawing matched").performScrollTo().assertExists()
        compose.onAllNodesWithText("Closest reference found").assertCountEquals(0)
        compose.onAllNodesWithText("Nearest references").assertCountEquals(0)
        compose.onAllNodes(numericSimilarity).assertCountEquals(1)
        compose.waitUntil(20_000) { recorded.get()==2 }
        screenshot("scanner-exact-reference")
        val blank=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try { file.outputStream().use { blank.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { blank.recycle() }
        compose.runOnIdle { model.load(uri) }
        compose.waitUntil(30_000) { model.state.value.bitmap!=null && !model.state.value.busy }
        compose.onNodeWithText("Single glyph").performScrollTo().performClick()
        compose.waitUntil(60_000) { model.state.value.referenceMatches!=null && !model.state.value.busy }
        compose.onNodeWithText("No readable glyph found").assertExists()
        compose.onNodeWithText("Photo tips").performScrollTo().assertExists()
        compose.onNodeWithText("Contrast is low.",substring=true).assertExists()
        compose.onAllNodes(numericSimilarity).assertCountEquals(0)
        screenshot("scanner-unreadable-photo-tips")
        file.delete()
    }

    @Test fun originalReferenceArtworkHasVerifiedSourceResolution() {
        val bitmap=requireNotNull(GlyphReferenceArtwork.load(context,"32vb"))
        assertEquals(512,bitmap.width)
        assertEquals(242,bitmap.height)
    }

    private val numericSimilarity = SemanticsMatcher("numeric appearance similarity") { node ->
        node.config.contains(SemanticsProperties.Text) &&
            node.config[SemanticsProperties.Text].any { text ->
                text.text.startsWith("Similarity ") &&
                    text.text.removePrefix("Similarity ").toFloatOrNull() != null
            }
    }

    private fun screenshot(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val node = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        val bitmap = node.captureToImage().asAndroidBitmap()
        try {
            val folder = requireNotNull(context.getExternalFilesDir("screenshots"))
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

}
