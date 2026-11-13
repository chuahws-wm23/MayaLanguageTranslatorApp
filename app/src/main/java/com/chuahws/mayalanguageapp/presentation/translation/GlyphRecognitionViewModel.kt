package com.chuahws.mayalanguageapp.presentation.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphImageLoader
import com.chuahws.mayalanguageapp.integration.glyph.GlyphNetwork
import com.chuahws.mayalanguageapp.integration.glyph.MayaGlyphRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class GlyphUiState(
    val bitmap: Bitmap? = null,
    val crop: GlyphCrop = GlyphCrop(),
    val result: GlyphNetwork.Result? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

/** Keeps glyph predictions out of the modern-Yucatec dictionary/history pipeline. */
internal class GlyphRecognitionViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(GlyphUiState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var version = 0

    fun clear() {
        version++
        work?.cancel()
        mutable.value = GlyphUiState()
        // Let the renderer/GC release preview bitmaps; recycling a displayed bitmap is unsafe.
    }

    fun load(uri: Uri, ownedCapture: Boolean = false) {
        clear()
        val request = version
        mutable.value = GlyphUiState(busy = true)
        work = viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    GlyphImageLoader.load(getApplication(), uri, ownedCapture)
                }
                if (request == version) mutable.value = GlyphUiState(bitmap = image)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == version) mutable.value = GlyphUiState(
                    message = "Could not read this image. Choose a clear JPG or PNG, or take another photo.")
            }
        }
    }

    fun setCrop(crop: GlyphCrop) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(crop = crop, result = null, message = null)
    }

    fun rotate() {
        val image = mutable.value.bitmap ?: return
        if (mutable.value.busy) return
        val request = ++version
        mutable.value = mutable.value.copy(busy = true, result = null, message = null)
        work = viewModelScope.launch {
            try {
                val rotated = withContext(Dispatchers.Default) {
                    Bitmap.createBitmap(image, 0, 0, image.width, image.height,
                        Matrix().apply { postRotate(90f) }, true)
                }
                if (request == version) mutable.value = GlyphUiState(bitmap = rotated)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == version) mutable.value = mutable.value.copy(
                    busy = false, message = "Could not rotate the photo. Please choose it again.")
            }
        }
    }

    fun recognize() {
        val snapshot = mutable.value
        val image = snapshot.bitmap ?: return
        if (snapshot.busy) return
        val request = ++version
        mutable.value = snapshot.copy(busy = true, result = null, message = null)
        work = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val (x, y, w, h) = snapshot.crop.pixels(image.width, image.height)
                    require(w >= 25 && h >= 25) { "Crop is too small. Select the complete glyph." }
                    val cropped = Bitmap.createBitmap(image, x, y, w, h)
                    try {
                        MayaGlyphRecognizer.fromAssets(getApplication()).recognize(cropped)
                    } finally {
                        if (cropped !== image) cropped.recycle()
                    }
                }
                if (request == version) mutable.value = mutable.value.copy(busy = false, result = result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) {
                if (request == version) mutable.value = mutable.value.copy(
                    busy = false, message = error.message ?: "Select one clear glyph and try again.")
            } catch (_: Exception) {
                if (request == version) mutable.value = mutable.value.copy(
                    busy = false, message = "Recognition failed. Reopen the screen or update the app and try again.")
            }
        }
    }
}
