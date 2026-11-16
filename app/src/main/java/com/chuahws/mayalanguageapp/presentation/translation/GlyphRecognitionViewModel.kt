package com.chuahws.mayalanguageapp.presentation.translation

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphPanelGrid
import com.chuahws.mayalanguageapp.integration.glyph.GlyphPanelGuard
import com.chuahws.mayalanguageapp.integration.glyph.GlyphImageLoader
import com.chuahws.mayalanguageapp.integration.glyph.GlyphMultiDetector
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV18Detector
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV11SegDetector
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualCatalogue
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualMatch
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
)

internal data class GlyphUiState(
    val bitmap: Bitmap? = null,
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
internal class GlyphRecognitionViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(GlyphUiState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var version = 0

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
                    components.mapIndexed { index, region ->
                        currentCoroutineContext().ensureActive()
                        val suggestions = gallery?.let { referenceSet ->
                            val (x,y,w,h) = region.crop.pixels(image.width,image.height)
                            val isolated = Bitmap.createBitmap(image,x,y,w,h)
                            try {
                                runCatching { referenceSet.search(isolated,number=3) }
                                    .getOrDefault(emptyList())
                            } finally {
                                if (isolated !== image) isolated.recycle()
                            }
                        }.orEmpty()
                        MultiGlyphItem(
                            index+1, region, null, null,
                            if(suggestions.isEmpty())
                                "No usable proposed sound match. This is only a detected graphic component."
                            else
                                "Top three proposed syllable references by image shape. " +
                                    "Visual overlap is NOT calibrated reading confidence.",
                            visualMatches = suggestions,
                        )
                    }
                }
                mutable.value = mutable.value.copy(
                    busy = false, multiple = items, individualComponents = true,
                    multipleProgress = null,
                    detectorNote = "Experimental v11 masks isolate components; the 86-syllable " +
                        "TWKM catalogue supplies possible-looking reference pictures where available. " +
                        "These are NOT verified readings or photo recognition probabilities.",
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

    private fun scanPanel(grid: Pair<Int, Int>?) {
        val snapshot = mutable.value
        val image = snapshot.bitmap ?: return
        if (snapshot.busy) return

        val request = ++version
        work?.cancel()
        mutable.value = snapshot.copy(
            busy = true, result = null, resultV2 = null, resultV3 = null,
            resultV4 = null, resultV25 = null, multiple = emptyList(), individualComponents = false,
            assistedGrid = false, detectorNote = null,
            multipleProgress = if (grid == null) "Finding glyph blocks…" else "Preparing grid…",
            message = null,
        )
        work = viewModelScope.launch {
            try {
                val (regions, detectorNote) = withContext(Dispatchers.Default) {
                    if (grid != null) {
                        GlyphPanelGrid.regions(snapshot.crop, grid.first, grid.second) to
                            "User-chosen approximate grid. Adjust rows and columns as needed."
                    } else {
                        // Run the trained v18 block detector FIRST when its ONNX
                        // asset has been added after validation. Otherwise keep
                        // the existing offline seam/grid fallback.
                        val selected = if (snapshot.crop == GlyphCrop()) image else {
                            val (x, y, w, h) = snapshot.crop.pixels(image.width, image.height)
                            Bitmap.createBitmap(image, x, y, w, h)
                        }
                        try {
                            val trained = runCatching {
                                GlyphV18Detector.fromAssetsOrNull(getApplication())?.detect(selected)
                            }.getOrNull()
                            val separated = if (!trained.isNullOrEmpty()) trained
                                else GlyphMultiDetector.detect(selected)
                            val note = when {
                                !trained.isNullOrEmpty() ->
                                    "Trained offline YOLO11n v18 detected these block candidates. " +
                                        "Boxes and reading order are provisional, not translations."
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
                            if (selected !== image) selected.recycle()
                        }
                    }
                }
                if (request != version) return@launch
                if (regions.isEmpty()) {
                    mutable.value = mutable.value.copy(
                        busy = false, multipleProgress = null,
                        message = "No reliable glyph-block boundaries found. " +
                            "Try Grid-assisted scan: drag one crop over the entire panel, " +
                            "choose its approximate rows and columns, then scan. " +
                            "You do not need to crop individual glyphs.",
                    )
                    return@launch
                }
                val results = withContext(Dispatchers.Default) {
                    regions.mapIndexed { index, region ->
                        currentCoroutineContext().ensureActive()
                        if (request == version) {
                            mutable.value = mutable.value.copy(
                                multipleProgress = "Recognising block ${index + 1} of ${regions.size}…",
                            )
                        }
                        val (x, y, w, h) = region.crop.pixels(image.width, image.height)
                        try {
                            require(w >= 25 && h >= 25)
                            val cropped = Bitmap.createBitmap(image, x, y, w, h)
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
                                        val result = MayaGlyphRecognizerV4.fromAssets(getApplication())
                                            .recognize(cropped)
                                        MultiGlyphItem(
                                            index + 1, region,
                                            if (result.reviewRecommended) null else result.reading,
                                            if (result.reviewRecommended) null else result.englishGloss,
                                            result.reviewReason
                                                ?: "Two lighting views agree; still an UNVERIFIED ten-sign guess",
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
                                if (cropped !== image) cropped.recycle()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            MultiGlyphItem(
                                index + 1, region, null, null,
                                "Could not read this block; try a clearer crop",
                            )
                        }
                    }
                }
                if (request == version) {
                    mutable.value = mutable.value.copy(
                        busy = false, multiple = results, multipleProgress = null,
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
            }
        }
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
