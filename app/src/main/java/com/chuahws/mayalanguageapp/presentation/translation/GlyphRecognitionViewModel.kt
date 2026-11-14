package com.chuahws.mayalanguageapp.presentation.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphImageLoader
import com.chuahws.mayalanguageapp.integration.glyph.GlyphMultiDetector
import com.chuahws.mayalanguageapp.integration.glyph.GlyphDetectedRegion
import com.chuahws.mayalanguageapp.integration.glyph.GlyphNetwork
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV2Result
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV3Result
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV3Screening
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV4Result
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
}

/** One AUTOMATICALLY detected block. A block may contain more than one sign. */
internal data class MultiGlyphItem(
    val number: Int,
    val region: GlyphDetectedRegion,
    val reading: String?,
    val englishGloss: String?,
    val note: String,
)

internal data class GlyphUiState(
    val bitmap: Bitmap? = null,
    val crop: GlyphCrop = GlyphCrop(),
    val result: GlyphNetwork.Result? = null,
    val resultV2: GlyphV2Result? = null,
    val resultV3: GlyphV3Result? = null,
    val resultV4: GlyphV4Result? = null,
    val multiple: List<MultiGlyphItem> = emptyList(),
    val multipleProgress: String? = null,
    val model: GlyphModelMode = GlyphModelMode.TINY_V1,
    val busy: Boolean = false,
    val message: String? = null,
)

private data class GlyphInferenceResult(
    val v1: GlyphNetwork.Result? = null,
    val v2: GlyphV2Result? = null,
    val v3: GlyphV3Result? = null,
    val v4: GlyphV4Result? = null,
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
            resultV4 = null, multiple = emptyList(), multipleProgress = null, message = null,
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
            resultV4 = null, multiple = emptyList(), multipleProgress = null, message = null,
        )
    }

    fun rotate() {
        val image = mutable.value.bitmap ?: return
        if (mutable.value.busy) return
        val request = ++version
        val model = mutable.value.model
        mutable.value = mutable.value.copy(
            busy = true, result = null, resultV2 = null, resultV3 = null,
            resultV4 = null, multiple = emptyList(), multipleProgress = null, message = null,
        )
        work = viewModelScope.launch {
            try {
                val rotated = withContext(Dispatchers.Default) {
                    Bitmap.createBitmap(
                        image, 0, 0, image.width, image.height,
                        Matrix().apply { postRotate(90f) }, true,
                    )
                }
                if (request == version) mutable.value = GlyphUiState(
                    bitmap = rotated, model = model,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == version) mutable.value = mutable.value.copy(
                    busy = false,
                    message = "Could not rotate the photo. Please choose it again.",
                )
            }
        }
    }

    /**
     * Scan a panel AUTOMATICALLY: detect a regular grid of glyph BLOCKS,
     * crop each block, and try the selected model on every block offline.
     * This is not full-inscription translation or validated sign segmentation.
     */
    fun scanAll() {
        val snapshot = mutable.value
        val image = snapshot.bitmap ?: return
        if (snapshot.busy) return

        val request = ++version
        work?.cancel()
        mutable.value = snapshot.copy(
            busy = true, result = null, resultV2 = null, resultV3 = null,
            resultV4 = null, multiple = emptyList(),
            multipleProgress = "Finding glyph blocks…", message = null,
        )
        work = viewModelScope.launch {
            try {
                val regions = withContext(Dispatchers.Default) {
                    GlyphMultiDetector.detect(image)
                }
                if (request != version) return@launch
                if (regions.isEmpty()) {
                    mutable.value = mutable.value.copy(
                        busy = false, multipleProgress = null,
                        message = "Could not automatically separate glyph blocks. " +
                            "Use a straight-on photo where the glyphs form clearly visible rows and columns. " +
                            "You can still scan a single glyph using the crop tool.",
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
                                            index + 1, region, result.reading, result.englishGloss,
                                            "Closed-set guess — NOT a verified reading",
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
                        busy = false, multiple = results, multipleProgress = null, message = null,
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
            resultV4 = null, multiple = emptyList(), multipleProgress = null, message = null,
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
                        }
                    } finally {
                        if (cropped !== image) cropped.recycle()
                    }
                }
                if (request == version) mutable.value = mutable.value.copy(
                    busy = false, result = outcome.v1, resultV2 = outcome.v2,
                    resultV3 = outcome.v3, resultV4 = outcome.v4,
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
                    message = "Glyph inference failed. Please check that this app build contains its local model assets.",
                )
            }
        }
    }
}
