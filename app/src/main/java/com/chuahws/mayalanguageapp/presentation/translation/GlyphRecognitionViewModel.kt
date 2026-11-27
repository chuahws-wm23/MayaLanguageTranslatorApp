package com.chuahws.mayalanguageapp.presentation.translation

import com.chuahws.mayalanguageapp.integration.glyph.displayableReference
import com.chuahws.mayalanguageapp.integration.glyph.GlyphReferenceDecision
import com.chuahws.mayalanguageapp.integration.glyph.referenceDecision
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.integration.glyph.GlyphScreenPhoto
import com.chuahws.mayalanguageapp.integration.glyph.GlyphWhitespaceDetector
import com.chuahws.mayalanguageapp.integration.glyph.GlyphRegionMatcher
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCaptionCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphArtworkBounds
import com.chuahws.mayalanguageapp.integration.glyph.GlyphImageQuality
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.integration.glyph.GlyphPanelGrid
import com.chuahws.mayalanguageapp.integration.glyph.GlyphPanelGuard
import com.chuahws.mayalanguageapp.integration.glyph.GlyphImageLoader
import com.chuahws.mayalanguageapp.integration.glyph.GlyphMultiDetector
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV18Detector
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV11SegDetector
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualCatalogue
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualMatch
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMeaningCatalogue
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMatch
import com.chuahws.mayalanguageapp.integration.glyph.GlyphDetectedRegion
import com.chuahws.mayalanguageapp.integration.glyph.GlyphNetwork
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV2Result
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV3Result
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV3Screening
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV4Result
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV25Result
import com.chuahws.mayalanguageapp.integration.glyph.MayaGlyphRecognizerV25
import com.chuahws.mayalanguageapp.integration.glyph.MayaGlyphRecognizerV4
import com.chuahws.mayalanguageapp.integration.glyph.MayaGlyphRecognizer
import com.chuahws.mayalanguageapp.integration.glyph.MayaGlyphRecognizerV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class GlyphModelMode {
    TINY_V1,
    RESEARCH_V2,
    RESEARCH_V3,
    RESEARCH_V4,
    RESEARCH_25,
}

/** One AUTOMATICALLY detected block. A block may contain more than one sign. */
internal data class MultiGlyphItem(
    val number: Int,
    val region: GlyphDetectedRegion,
    val reading: String?,
    val englishGloss: String?,
    val note: String,
    val visualMatches: List<GlyphVisualMatch> = emptyList(),
    val englishMatches: List<GlyphEnglishMatch> = emptyList(),
)

internal data class GlyphUiState(
    val bitmap: Bitmap? = null,
    val activeMode: String? = null,
    val resultRevision: Int = 0,
    val savedScan: TranslationRecord? = null,
    val saveError: String? = null,
    val referenceMatches: List<GlyphEnglishMatch>? = null,
    val referenceComparisonImage: Bitmap? = null,
    val scanBoxes: List<GlyphCrop> = emptyList(),
    val referenceHints: List<String> = emptyList(),
    val crop: GlyphCrop = GlyphCrop(),
    val result: GlyphNetwork.Result? = null,
    val resultV2: GlyphV2Result? = null,
    val resultV3: GlyphV3Result? = null,
    val resultV4: GlyphV4Result? = null,
    val resultV25: GlyphV25Result? = null,
    val multiple: List<MultiGlyphItem> = emptyList(),
    val individualComponents: Boolean = false,
    val multipleProgress: String? = null,
    val assistedGrid: Boolean = false,
    val detectorNote: String? = null,
    val model: GlyphModelMode = GlyphModelMode.RESEARCH_V4,
    val busy: Boolean = false,
    val message: String? = null,
)

private data class GlyphInferenceResult(
    val v1: GlyphNetwork.Result? = null,
    val v2: GlyphV2Result? = null,
    val v3: GlyphV3Result? = null,
    val v4: GlyphV4Result? = null,
    val v25: GlyphV25Result? = null,
)

/** Keeps unverified glyph predictions out of the modern-Yucatec dictionary/history pipeline. */
internal class GlyphRecognitionViewModel(
    application: Application,
    private val detectTrainedRegions: (Bitmap) -> List<GlyphDetectedRegion>?,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, { image ->
        GlyphV18Detector.fromAssetsOrNull(application)?.detect(image)
    })

    private val mutable = MutableStateFlow(GlyphUiState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var version = 0
    // Inference revisions belong to this model instance, including after rotation.
    var recordedResultRevision: Int = -1

    fun updateHistoryStatus(revision: Int, record: TranslationRecord? = null, error: String? = null) {
        val current = mutable.value
        if (revision == current.resultRevision && !current.busy) {
            mutable.value = current.copy(savedScan = record, saveError = error)
        }
    }

    fun clear() {
        version++
        work?.cancel()
        val chosenModel = mutable.value.model
        mutable.value = GlyphUiState(model = chosenModel)
        // Never recycle a Bitmap that might still be rendered by Compose.
    }

    fun setModel(model: GlyphModelMode) {
        if (mutable.value.busy || mutable.value.model == model) return
        version++
        mutable.value = mutable.value.copy(
            model = model, result = null, resultV2 = null, resultV3 = null,
            resultV4 = null, resultV25 = null, multiple = emptyList(), individualComponents = false,
            multipleProgress = null, message = null,
        )
    }

    fun load(uri: Uri, ownedCapture: Boolean = false) {
        clear()
        val request = version
        val chosenModel = mutable.value.model
        mutable.value = GlyphUiState(model = chosenModel, busy = true)
        work = viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    GlyphImageLoader.load(getApplication(), uri, ownedCapture)
                }
                if (request == version) mutable.value = GlyphUiState(
                    bitmap = image, model = chosenModel,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == version) mutable.value = GlyphUiState(
                    model = chosenModel,
                    message = "Could not read this image. Choose a clear JPG or PNG, or take another photo.",
                )
            }
        }
    }

    fun setCrop(crop: GlyphCrop) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(
            crop = crop, result = null, resultV2 = null, resultV3 = null,
            resultV4 = null, resultV25 = null, multiple = emptyList(), individualComponents = false,
            multipleProgress = null, message = null,
        )
    }

    /**
     * Scan a panel AUTOMATICALLY: detect a regular grid of glyph BLOCKS,
     * crop each block, and try the selected model on every block offline.
     * This is not full-inscription translation or validated sign segmentation.
     */
    fun scanAll() = scanPanel(null)

    fun scanPicture() {
        val current = mutable.value
        if (current.bitmap == null || current.busy) return
        mutable.value = current.copy(savedScan = null, saveError = null)
        scanPanel(grid = null, allowSingleFallback = true)
    }

    /**
     * Scan graphic COMPONENTS with the separately installed v11 mask model.
     * Never apply a TWKM recognizer or propose a reading for these shapes:
     * Roboflow object codes are not verified TWKM or syllable mappings.
     */
    fun scanComponents() {
        val snapshot = mutable.value
        val image = snapshot.bitmap ?: return
        if (snapshot.busy) return
        if (!GlyphV11SegDetector.isInstalled(getApplication())) {
            mutable.value = snapshot.copy(
                message = "Individual-sign segmentation model is not installed. " +
                    "Export best.pt to ONNX and install maya_signs_v11_seg.onnx in app/src/main/assets.",
            )
            return
        }
        val request = ++version
        work?.cancel()
        mutable.value = snapshot.copy(
            busy = true, result = null, resultV2 = null, resultV3 = null,
            resultV4 = null, resultV25 = null, multiple = emptyList(),
            individualComponents = true, assistedGrid = false,
            detectorNote = null, multipleProgress = "Finding individual glyph components…",
            message = null,
        )
        work = viewModelScope.launch {
            try {
                val components = withContext(Dispatchers.Default) {
                    val selected = if (snapshot.crop == GlyphCrop()) image else {
                        val (x,y,w,h) = snapshot.crop.pixels(image.width,image.height)
                        Bitmap.createBitmap(image,x,y,w,h)
                    }
                    try {
                        val detected = GlyphV11SegDetector.fromAssetsOrNull(getApplication())
                            ?.detect(selected).orEmpty()
                        if (selected === image) detected
                        else detected.map { GlyphPanelGrid.toOriginal(snapshot.crop,it) }
                    } finally {
                        if (selected !== image) selected.recycle()
                    }
                }
                if (request != version) return@launch
                val items = withContext(Dispatchers.Default) {
                    val gallery = runCatching {
                        GlyphVisualCatalogue.fromAssetsOrNull(getApplication())
                    }.getOrNull()
                    val meanings = runCatching {
                        GlyphEnglishMeaningCatalogue.fromAssetsOrNull(getApplication())
                    }.getOrNull()
                    components.mapIndexed { index, region ->
                        currentCoroutineContext().ensureActive()
                        val pair = if(gallery == null)
                            emptyList<GlyphVisualMatch>() to emptyList<GlyphEnglishMatch>()
                        else {
                            val (x,y,w,h)=region.crop.pixels(image.width,image.height)
                            val single=Bitmap.createBitmap(image,x,y,w,h)
                            try {
                                val sounds=runCatching { gallery.search(single,number=3) }
                                    .getOrDefault(emptyList())
                                val words=if(meanings!=null)
                                    runCatching { meanings.search(single,gallery,3) }
                                        .getOrDefault(emptyList())
                                else emptyList()
                                sounds to words
                            } finally {
                                if(single !== image) single.recycle()
                            }
                        }
                        val (sounds,words)=pair
                        MultiGlyphItem(
                            index+1,region,null,null,
                            if(words.isEmpty() && sounds.isEmpty())
                                "No reliable reading. This is only a detected graphic component."
                            else
                                "Possible-looking English word signs and syllables. " +
                                    "Visual overlap is NOT calibrated translation confidence.",
                            visualMatches=sounds,
                            englishMatches=words,
                        )
                    }
                }
                mutable.value = mutable.value.copy(
                    busy = false, multiple = items, individualComponents = true,
                    multipleProgress = null,
                    detectorNote = "Experimental v11 masks isolate components. " +
                        "The English word-sign and syllable catalogues offer similar-looking drawings. " +
                        "These are NOT verified translations or photo recognition probabilities.",
                    message = if (items.isEmpty())
                        "No individual components passed the mask filter. Try a clear drawing, " +
                        "a smaller region, or use the original block detector."
                    else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == version) {
                    mutable.value = mutable.value.copy(
                        busy = false, multipleProgress = null,
                        message = "Component segmentation failed. Check that the v11 " +
                            "ONNX model has raw [1,37,N] and [1,32,128,128] outputs.",
                    )
                }
            }
        }
    }

    /** Use one full-panel selection and a user-set grid when the strict automatic
     * seam detector cannot separate densely carved or uneven blocks. */
    fun scanGrid(rows: Int, columns: Int) = scanPanel(rows to columns)

    private fun scanPanel(grid: Pair<Int, Int>?, allowSingleFallback: Boolean = false) {
        val snapshot = mutable.value
        val image = snapshot.bitmap ?: return
        if (snapshot.busy) return

        val request = ++version
        work?.cancel()
        mutable.value = snapshot.copy(
            busy = true, activeMode = "multiple", referenceMatches = null, result = null, resultV2 = null, resultV3 = null,
            resultV4 = null, resultV25 = null, multiple = emptyList(), individualComponents = false,
            assistedGrid = false, detectorNote = null, referenceComparisonImage = null, scanBoxes = emptyList(),
            multipleProgress = if (grid == null) "Finding glyph blocks…" else "Preparing grid…",
            message = null,
        )
        work = viewModelScope.launch {
            var cleaned: Bitmap? = null
            try {
                if (allowSingleFallback && snapshot.crop == GlyphCrop()) {
                    withContext(Dispatchers.Default) { cleaned = GlyphScreenPhoto.clean(image) }
                }
                val workingImage = cleaned ?: image
                val (regions, detectorNote) = withContext(Dispatchers.Default) {
                    if (grid != null) {
                        GlyphPanelGrid.regions(snapshot.crop, grid.first, grid.second) to
                            "User-chosen approximate grid. Adjust rows and columns as needed."
                    } else {
                        // The regular-grid heuristic can mistake strokes inside one
                        // sign for boundaries. Keep it out of the main scanner.
                        val selected = if (snapshot.crop == GlyphCrop()) workingImage else {
                            val (x, y, w, h) = snapshot.crop.pixels(image.width, image.height)
                            Bitmap.createBitmap(workingImage, x, y, w, h)
                        }
                        try {
                            val trained = if (cleaned != null) null else runCatching {
                                detectTrainedRegions(selected)
                            }.getOrNull()
                            // One proposal can be a curl or another part of a larger sign.
                            // Require two regions for automatic panel mode.
                            val trainedPanel = trained.orEmpty().takeIf {
                                it.size >= if (allowSingleFallback) 2 else 1
                            }.orEmpty()
                            val whitespace = if (trainedPanel.isEmpty())
                                GlyphWhitespaceDetector.detect(selected) else emptyList()
                            val separated = when {
                                trainedPanel.isNotEmpty() -> trainedPanel
                                whitespace.isNotEmpty() -> whitespace
                                allowSingleFallback -> emptyList()
                                else -> GlyphMultiDetector.detect(selected)
                            }
                            val note = when {
                                trainedPanel.isNotEmpty() ->
                                    "Trained offline YOLO11n v18 detected these block candidates. " +
                                        "Boxes and reading order are provisional, not translations."
                                whitespace.isNotEmpty() ->
                                    "Separated drawing regions found from clear background gutters. " +
                                        "These are layout proposals, not verified sign boundaries or linguistic reading order."
                                allowSingleFallback ->
                                    "No separate glyph regions detected."
                                trained != null ->
                                    "v18 model found no usable boxes; used the original " +
                                        "grid/seam fallback instead."
                                else ->
                                    "No active trained v18 detector (or it could not load). " +
                                        "Using existing offline grid/seam detection."
                            }
                            val restored = if (snapshot.crop == GlyphCrop()) separated else {
                                separated.map { region ->
                                    GlyphPanelGrid.toOriginal(snapshot.crop, region)
                                }
                            }
                            restored to note
                        } finally {
                            if (selected !== workingImage) selected.recycle()
                        }
                    }
                }
                if (request != version) return@launch
                if (regions.isEmpty()) {
                    if (allowSingleFallback && tryIsolatedPicture(workingImage, request)) return@launch
                    mutable.value = mutable.value.copy(
                        busy = false, multipleProgress = null,
                        message = if (allowSingleFallback) {
                            "No clear glyph region found. Try a closer photo with space around each complete glyph."
                        } else {
                            "The glyph regions could not be separated. Try a closer photo of one complete glyph " +
                                "or a clearer picture with space between glyphs."
                        },
                    )
                    return@launch
                }
                mutable.value = mutable.value.copy(scanBoxes = regions.map { it.crop })
                val results = withContext(Dispatchers.Default) {
                    val visualCatalogue = runCatching {
                        GlyphVisualCatalogue.fromAssetsOrNull(getApplication())
                    }.getOrNull()
                    val meaningCatalogue = runCatching {
                        GlyphEnglishMeaningCatalogue.fromAssetsOrNull(getApplication())
                    }.getOrNull()
                    regions.mapIndexed { index, region ->
                        currentCoroutineContext().ensureActive()
                        if (request == version) {
                            mutable.value = mutable.value.copy(
                                multipleProgress = "Recognising block ${index + 1} of ${regions.size}…",
                            )
                        }
                        val (x, y, w, h) = region.crop.pixels(workingImage.width, workingImage.height)
                        try {
                            require(w >= 25 && h >= 25)
                            val cropped = Bitmap.createBitmap(workingImage, x, y, w, h)
                            try {
                                when (snapshot.model) {
                                    GlyphModelMode.TINY_V1 -> {
                                        val result = MayaGlyphRecognizer.fromAssets(getApplication())
                                            .recognize(cropped)
                                        MultiGlyphItem(
                                            index + 1, region, result.suggested?.reading,
                                            result.suggested?.english,
                                            if (result.suggested == null)
                                                "No reliable supported match"
                                            else "Unverified experimental candidate",
                                        )
                                    }
                                    GlyphModelMode.RESEARCH_V2 -> {
                                        val result = MayaGlyphRecognizerV2.fromAssets(getApplication())
                                            .recognize(cropped)
                                        MultiGlyphItem(
                                            index + 1, region, result.reading, result.englishGloss,
                                            "Closed-set guess — NOT a verified reading",
                                        )
                                    }
                                    GlyphModelMode.RESEARCH_V3 -> {
                                        val result = MayaGlyphRecognizerV2.fromAssets(getApplication())
                                            .recognizeWithScreening(
                                                cropped, GlyphV3Screening.fromAssets(getApplication()),
                                            )
                                        MultiGlyphItem(
                                            index + 1, region, result.possibleMatch?.reading,
                                            result.possibleMatch?.englishGloss,
                                            if (result.screenedOut)
                                                "No reliable supported match"
                                            else "Screened but UNVERIFIED candidate",
                                        )
                                    }
                                    GlyphModelMode.RESEARCH_V4 -> {
                                        // A detected block can contain multiple signs. Use the
                                        // same guarded drawing search as the single-glyph page.
                                        val comparison = if (visualCatalogue != null && meaningCatalogue != null)
                                            GlyphRegionMatcher.compare(cropped, meaningCatalogue, visualCatalogue)
                                        else null
                                        MultiGlyphItem(
                                            index + 1, region, null, null,
                                            comparison?.note ?: "Reference catalogue unavailable; no reading proposed.",
                                            englishMatches = comparison?.matches.orEmpty(),
                                        )
                                    }
                                    GlyphModelMode.RESEARCH_25 -> {
                                        // A YOLO glyph block may contain multiple independent signs.
                                        // Do not feed it into this *single-sign* classifier.
                                        MultiGlyphItem(
                                            index + 1, region, null, null,
                                            "Experimental 25-sign model expects ONE isolated sign. " +
                                                "Use Inspect and crop a single sign; " +
                                                "automatic multi-sign block reading is not validated.",
                                        )
                                    }
                                }
                            } finally {
                                if (cropped !== workingImage) cropped.recycle()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            MultiGlyphItem(
                                index + 1, region, null, null,
                                "Could not read this region; try a clearer photo",
                            )
                        }
                    }
                }
                if (request == version) {
                    mutable.value = mutable.value.copy(
                        busy = false, multiple = results, multipleProgress = null, resultRevision = request,
                        assistedGrid = grid != null, detectorNote = detectorNote,
                        message = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == version) {
                    mutable.value = mutable.value.copy(
                        busy = false, multipleProgress = null,
                        message = "Automatic panel scan failed. Try a clearer photo with complete glyph blocks.",
                    )
                }
            } finally {
                cleaned?.recycle()
            }
        }
    }

    /** Full uploaded image; automatic preprocessing only, no manual crop UI. */
    fun compareReference() {
        val snapshot=mutable.value
        val image=snapshot.bitmap ?: return
        if(snapshot.busy)return
        val request=++version
        work?.cancel()
        mutable.value=snapshot.copy(busy=true,activeMode="single",referenceMatches=null,referenceHints=emptyList(),
            multiple=emptyList(),message=null,detectorNote=null,referenceComparisonImage=null,scanBoxes=emptyList())
        work=viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.Default) {
                    require(!GlyphPanelGuard.looksLikeDensePanel(image)) {
                        "This picture looks like a dense panel. Try a clearer picture with space around each glyph."
                    }
                    compareIsolatedPicture(image)
                }
                if (request == version) mutable.value = mutable.value.copy(
                    busy = false, referenceMatches = result.first, referenceHints = result.second,
                    referenceComparisonImage = result.third, resultRevision = request,
                ) else result.third?.takeUnless { it === image }?.recycle()
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(invalid: IllegalArgumentException) {
                if(request==version)mutable.value=mutable.value.copy(busy=false,
                    message=invalid.message ?: "This photo could not be compared. Choose another clear photo.")
            }
            catch(_: Exception) {
                if(request==version)mutable.value=mutable.value.copy(busy=false,
                    message="This photo could not be compared. Choose another clear photo and try again.")
            }
        }
    }

    private fun compareIsolatedPicture(image: Bitmap): Triple<List<GlyphEnglishMatch>, List<String>, Bitmap?> {
        val words=requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(getApplication()))
        val sounds=requireNotNull(GlyphVisualCatalogue.fromAssetsOrNull(getApplication()))
        val original = words.search(image, sounds, 5)
        val hints = com.chuahws.mayalanguageapp.integration.glyph.GlyphImageQuality.hints(image)
        val decision = original.referenceDecision()
        if (decision == GlyphReferenceDecision.SHOW_REFERENCE || decision == GlyphReferenceDecision.SYLLABLE_REFERENCE) {
            return Triple(original, hints, null)
        }
        val crop = GlyphCaptionCrop.find(image) ?: return Triple(original, hints, null)
        val (x, y, width, height) = crop.pixels(image.width, image.height)
        val glyph = Bitmap.createBitmap(image, x, y, width, height)
        var retained = false
        return try {
            val retry = words.search(glyph, sounds, 5)
            if (retry.displayableReference() != null) {
                retained = true
                Triple(retry, hints, glyph)
            } else Triple(original, hints, null)
        } finally {
            if (!retained && glyph !== image) glyph.recycle()
        }
    }

    private suspend fun tryIsolatedPicture(image: Bitmap, request: Int): Boolean {
        val (box, unreadableHints) = withContext<Pair<GlyphCrop?, List<String>>?>(Dispatchers.Default) {
            if (GlyphPanelGuard.looksLikeDensePanel(image)) return@withContext null
            val detected = GlyphArtworkBounds.find(image)
            detected to (if (detected == null) GlyphImageQuality.hints(image) else emptyList())
        } ?: return false
        if (request != version) return true
        if (box == null) {
            mutable.value = mutable.value.copy(
                activeMode = "single", busy = false, multipleProgress = null, referenceMatches = emptyList(),
                referenceHints = unreadableHints, resultRevision = request,
            )
            return true
        }
        mutable.value = mutable.value.copy(
            activeMode = "single", scanBoxes = listOf(box), multipleProgress = "Comparing the boxed glyph…",
        )
        var preview: Bitmap? = null
        var keepPreview = false
        try {
            val (matches, hints) = withContext(Dispatchers.Default) {
                val (x, y, width, height) = box.pixels(image.width, image.height)
                val glyph = Bitmap.createBitmap(image, x, y, width, height)
                if (glyph !== image) preview = glyph
                val words = requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(getApplication()))
                val sounds = requireNotNull(GlyphVisualCatalogue.fromAssetsOrNull(getApplication()))
                words.search(glyph, sounds, 5) to GlyphImageQuality.hints(glyph)
            }
            if (request == version) {
                mutable.value = mutable.value.copy(
                    busy = false, multipleProgress = null, referenceMatches = matches, referenceHints = hints,
                    referenceComparisonImage = preview, resultRevision = request,
                )
                keepPreview = true
            }
        } finally {
            if (!keepPreview) preview?.recycle()
        }
        return true
    }

    fun recognize() {
        val snapshot = mutable.value
        val image = snapshot.bitmap ?: return
        if (snapshot.busy) return
        val request = ++version
        mutable.value = snapshot.copy(
            busy = true, result = null, resultV2 = null, resultV3 = null,
            resultV4 = null, resultV25 = null, multiple = emptyList(), multipleProgress = null, message = null,
        )
        work = viewModelScope.launch {
            try {
                val outcome: GlyphInferenceResult =
                    withContext(Dispatchers.Default) {
                    val (x, y, w, h) = snapshot.crop.pixels(image.width, image.height)
                    require(w >= 25 && h >= 25) {
                        "Crop is too small. Select the complete glyph."
                    }
                    val cropped = Bitmap.createBitmap(image, x, y, w, h)
                    try {
                        // A whole inscription is not a single sign. This is a
                        // conservative image-complexity warning, not a recogniser.
                        if (snapshot.model in setOf(GlyphModelMode.RESEARCH_V4, GlyphModelMode.RESEARCH_25) &&
                            snapshot.crop == GlyphCrop() &&
                            GlyphPanelGuard.looksLikeDensePanel(cropped)
                        ) {
                            throw IllegalArgumentException(
                                "This image appears to contain many small glyphs. " +
                                "Use Scan all glyphs or Grid-assisted scan; " +
                                "do not treat the entire inscription as one sign."
                            )
                        }
                        when (snapshot.model) {
                            GlyphModelMode.TINY_V1 ->
                                GlyphInferenceResult(v1 = MayaGlyphRecognizer.fromAssets(getApplication()).recognize(cropped))
                            GlyphModelMode.RESEARCH_V2 ->
                                GlyphInferenceResult(v2 = MayaGlyphRecognizerV2.fromAssets(getApplication()).recognize(cropped))
                            GlyphModelMode.RESEARCH_V3 ->
                                GlyphInferenceResult(v3 = MayaGlyphRecognizerV2.fromAssets(getApplication())
                                    .recognizeWithScreening(cropped, GlyphV3Screening.fromAssets(getApplication())))
                            GlyphModelMode.RESEARCH_V4 ->
                                GlyphInferenceResult(v4 = MayaGlyphRecognizerV4.fromAssets(getApplication())
                                    .recognize(cropped))
                            GlyphModelMode.RESEARCH_25 ->
                                GlyphInferenceResult(v25 = MayaGlyphRecognizerV25.fromAssets(getApplication())
                                    .recognize(cropped))
                        }
                    } finally {
                        if (cropped !== image) cropped.recycle()
                    }
                }
                if (request == version) mutable.value = mutable.value.copy(
                    busy = false, result = outcome.v1, resultV2 = outcome.v2,
                    resultV3 = outcome.v3, resultV4 = outcome.v4, resultV25 = outcome.v25,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) {
                if (request == version) mutable.value = mutable.value.copy(
                    busy = false, message = error.message ?: "Select one clear glyph and try again.",
                )
            } catch (_: Exception) {
                if (request == version) mutable.value = mutable.value.copy(
                    busy = false,
                    message = if (snapshot.model == GlyphModelMode.RESEARCH_25)
                        "The optional 25-sign model could not load or run. Install the verified " +
                            "four-file research artifact in app/src/main/assets, then rebuild."
                    else "Glyph inference failed. Please check that this app build contains its local model assets.",
                )
            }
        }
    }
}
