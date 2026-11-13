package com.chuahws.mayalanguageapp.presentation.translation

import android.net.Uri
import android.view.Surface as AndroidSurface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chuahws.mayalanguageapp.integration.ml_kit_ocr.createTemporaryImageFile
import com.chuahws.mayalanguageapp.integration.ml_kit_ocr.temporaryImageUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraCaptureScreen(onCancel: () -> Unit, onImageCaptured: (Uri) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val active = remember { AtomicBoolean(true) }
    val capturedCallback by rememberUpdatedState(onImageCaptured)
    val errorCallback by rememberUpdatedState(onError)
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context, lifecycleOwner) {
        try {
            val camera = ProcessCameraProvider.getInstance(context).await()
            provider = camera
            val selector = when {
                camera.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                camera.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> { errorCallback("No usable camera was found. Choose an image from Gallery."); return@LaunchedEffect }
            }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(previewView.display?.rotation ?: AndroidSurface.ROTATION_0).build()
            camera.unbindAll()
            camera.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            imageCapture = capture
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = "Could not start the camera. Close it and try Gallery instead."
        }
    }
    DisposableEffect(Unit) {
        active.set(true)
        onDispose { active.set(false); provider?.unbindAll() }
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        if (capturing) return
        capturing = true
        message = null
        val file = try { createTemporaryImageFile(context) } catch (_: Exception) {
            capturing = false; message = "Could not create a temporary photo. Check available storage."; return
        }
        try {
            capture.targetRotation = previewView.display?.rotation ?: AndroidSurface.ROTATION_0
            capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (!active.get()) { file.delete(); return }
                        capturing = false
                        try { capturedCallback(temporaryImageUri(context, file)) }
                        catch (_: Exception) { file.delete(); message = "Could not open the captured photo. Please try again." }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        if (active.get()) { capturing = false; message = "Could not capture the photo. Please try again." }
                    }
                })
        } catch (_: Exception) {
            file.delete(); capturing = false; message = "Could not capture the photo. Please try again."
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Color.Black.copy(alpha = .65f)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalIconButton(onClick = onCancel, enabled = !capturing) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close camera")
                }
                Text("Take a photo of one Maya glyph", Modifier.weight(1f), color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .65f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Keep the full drawing visible. You can crop and rotate it next.", color = Color.White)
            message?.let { Text(it, color = Color.White) }
            if (imageCapture == null && message == null) {
                CircularProgressIndicator(); Text("Starting camera…", color = Color.White)
            }
            Button(onClick = ::takePhoto, enabled = imageCapture != null && !capturing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text(if (capturing) "Saving photo…" else "Take photo")
            }
        }
    }
}
