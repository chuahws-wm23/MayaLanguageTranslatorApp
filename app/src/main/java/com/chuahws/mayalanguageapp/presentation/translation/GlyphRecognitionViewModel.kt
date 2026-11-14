package com.chuahws.mayalanguageapp.presentation.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphImageLoader
import com.chuahws.mayalanguageapp.integration.glyph.GlyphSampleStore
import com.chuahws.mayalanguageapp.integration.glyph.GlyphNetwork
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV2Result
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV3Result
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV3Screening
import com.chuahws.mayalanguageapp.integration.glyph.MayaGlyphRecognizer
import com.chuahws.mayalanguageapp.integration.glyph.MayaGlyphRecognizerV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class GlyphModelMode {
    TINY_V1,
    RESEARCH_V2,
    RESEARCH_V3,
}

internal data class GlyphUiState(
    val bitmap: Bitmap? = null,
    val crop: GlyphCrop = GlyphCrop(),
    val result: GlyphNetwork.Result? = null,
    val resultV2: GlyphV2Result? = null,
    val resultV3: GlyphV3Result? = null,
    val model: GlyphModelMode = GlyphModelMode.TINY_V1,
    val busy: Boolean = false,
    val message: String? = null,
    val imageSource: String = "gallery",
    val sampleBusy: Boolean = false,
    val sampleCount: Int = 0,
    val sampleMessage: String? = null,
)

/** Keeps unverified glyph predictions out of the modern-Yucatec dictionary/history pipeline. */
internal class GlyphRecognitionViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(
        GlyphUiState(sampleCount = GlyphSampleStore.count(application))
    )
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var version = 0

    fun clear() {
        version++
        work?.cancel()
        val chosenModel = mutable.value.model
        val samples = mutable.value.sampleCount
        mutable.value = GlyphUiState(model = chosenModel, sampleCount = samples)
        // Never recycle a Bitmap that might still be rendered by Compose.
    }

    fun setModel(model: GlyphModelMode) {
        if (mutable.value.busy || mutable.value.sampleBusy || mutable.value.model == model) return
        version++
        mutable.value = mutable.value.copy(
            model = model, result = null, resultV2 = null, resultV3 = null, message = null,
        )
    }

    fun load(uri: Uri, ownedCapture: Boolean = false) {
        clear()
        val request = version
        val chosenModel = mutable.value.model
        val saved = mutable.value.sampleCount
        val source = if (ownedCapture) "camera" else "gallery"
        mutable.value = GlyphUiState(model = chosenModel, busy = true, imageSource = source, sampleCount = saved)
        work = viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    GlyphImageLoader.load(getApplication(), uri, ownedCapture)
                }
                if (request == version) mutable.value = GlyphUiState(
                    bitmap = image, model = chosenModel, imageSource = source, sampleCount = saved,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == version) mutable.value = GlyphUiState(
                    model = chosenModel, imageSource = source, sampleCount = saved,
                    message = "Could not read this image. Choose a clear JPG or PNG, or take another photo.",
                )
            }
        }
    }

    fun setCrop(crop: GlyphCrop) {
        if (mutable.value.busy || mutable.value.sampleBusy) return
        mutable.value = mutable.value.copy(
            crop = crop, result = null, resultV2 = null, resultV3 = null, message = null,
        )
    }

    fun rotate() {
        val image = mutable.value.bitmap ?: return
        if (mutable.value.busy || mutable.value.sampleBusy) return
        val request = ++version
        val model = mutable.value.model
        mutable.value = mutable.value.copy(
            busy = true, result = null, resultV2 = null, resultV3 = null, message = null,
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
                    imageSource = mutable.value.imageSource,
                    sampleCount = mutable.value.sampleCount,
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

    /** Explicit opt-in only. Save a crop and UNVERIFIED user annotation locally. */
    fun saveExample(
        label: String,
        drawingGroup: String,
        sourceReference: String,
        rightsConfirmed: Boolean,
    ) {
        val snapshot = mutable.value
        val bitmap = snapshot.bitmap ?: return
        if (snapshot.busy || snapshot.sampleBusy) return
        val token = ++version
        mutable.value = snapshot.copy(sampleBusy = true, sampleMessage = null)
        work = viewModelScope.launch {
            try {
                val modelId = when (snapshot.model) {
                    GlyphModelMode.TINY_V1 -> "v1"
                    GlyphModelMode.RESEARCH_V2 -> "v2"
                    GlyphModelMode.RESEARCH_V3 -> "v3"
                }
                withContext(Dispatchers.IO) {
                    GlyphSampleStore.save(
                        getApplication(), bitmap, snapshot.crop,
                        GlyphSampleStore.Annotation(
                            label = label, drawingGroup = drawingGroup,
                            sourceReference = sourceReference,
                            permissionConfirmed = rightsConfirmed,
                            inputSource = snapshot.imageSource, modelUsed = modelId,
                        ),
                    )
                }
                if (token == version) {
                    mutable.value = mutable.value.copy(
                        sampleBusy = false,
                        sampleCount = GlyphSampleStore.count(getApplication()),
                        sampleMessage = "Saved locally as an unverified research example. Nothing was uploaded.",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == version) {
                    mutable.value = mutable.value.copy(
                        sampleBusy = false,
                        sampleMessage = error.message ?: "Couldn't save this cropped sample.",
                    )
                }
            }
        }
    }

    fun exportExamples(uri: Uri) {
        if (mutable.value.busy || mutable.value.sampleBusy) return
        val token = ++version
        mutable.value = mutable.value.copy(sampleBusy = true, sampleMessage = null)
        work = viewModelScope.launch {
            try {
                val exported = withContext(Dispatchers.IO) {
                    GlyphSampleStore.exportZip(getApplication(), uri)
                }
                if (token == version) mutable.value = mutable.value.copy(
                    sampleBusy = false,
                    sampleMessage = "Exported " + exported + " annotated samples to your selected ZIP file.",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == version) mutable.value = mutable.value.copy(
                    sampleBusy = false,
                    sampleMessage = error.message ?: "Could not export research examples.",
                )
            }
        }
    }

    fun deleteExamples() {
        if (mutable.value.busy || mutable.value.sampleBusy) return
        val token = ++version
        mutable.value = mutable.value.copy(sampleBusy = true, sampleMessage = null)
        work = viewModelScope.launch {
            try {
                val removed = withContext(Dispatchers.IO) {
                    GlyphSampleStore.deleteAll(getApplication())
                }
                if (token == version) mutable.value = mutable.value.copy(
                    sampleBusy = false, sampleCount = 0,
                    sampleMessage = "Deleted " + removed + " local research samples.",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == version) mutable.value = mutable.value.copy(
                    sampleBusy = false,
                    sampleMessage = error.message ?: "Could not clear research samples.",
                )
            }
        }
    }

    fun recognize() {
        val snapshot = mutable.value
        val image = snapshot.bitmap ?: return
        if (snapshot.busy || snapshot.sampleBusy) return
        val request = ++version
        mutable.value = snapshot.copy(
            busy = true, result = null, resultV2 = null, resultV3 = null, message = null,
        )
        work = viewModelScope.launch {
            try {
                val outcome: Triple<GlyphNetwork.Result?, GlyphV2Result?, GlyphV3Result?> =
                    withContext(Dispatchers.Default) {
                    val (x, y, w, h) = snapshot.crop.pixels(image.width, image.height)
                    require(w >= 25 && h >= 25) {
                        "Crop is too small. Select the complete glyph."
                    }
                    val cropped = Bitmap.createBitmap(image, x, y, w, h)
                    try {
                        when (snapshot.model) {
                            GlyphModelMode.TINY_V1 ->
                                Triple(MayaGlyphRecognizer.fromAssets(getApplication()).recognize(cropped), null, null)
                            GlyphModelMode.RESEARCH_V2 ->
                                Triple(null, MayaGlyphRecognizerV2.fromAssets(getApplication()).recognize(cropped), null)
                            GlyphModelMode.RESEARCH_V3 ->
                                Triple(null, null, MayaGlyphRecognizerV2.fromAssets(getApplication())
                                    .recognizeWithScreening(cropped, GlyphV3Screening.fromAssets(getApplication())))
                        }
                    } finally {
                        if (cropped !== image) cropped.recycle()
                    }
                }
                if (request == version) mutable.value = mutable.value.copy(
                    busy = false, result = outcome.first, resultV2 = outcome.second,
                    resultV3 = outcome.third,
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
