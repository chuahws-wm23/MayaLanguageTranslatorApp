package com.chuahws.mayalanguageapp.presentation.translation

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun CameraCaptureScreen(
    onCancel: () -> Unit,
    onImageCaptured: (Uri) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) {
        ContextCompat.getMainExecutor(context)
    }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var cameraProvider by remember {
        mutableStateOf<ProcessCameraProvider?>(null)
    }
    var imageCapture by remember {
        mutableStateOf<ImageCapture?>(null)
    }
    var cameraReady by remember {
        mutableStateOf(false)
    }
    var capturing by remember {
        mutableStateOf(false)
    }
    var cameraMessage by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(
        context,
        lifecycleOwner,
        previewView,
    ) {
        runCatching {
            getCameraProvider(context)
        }.onSuccess { provider ->
            cameraProvider = provider

            val selector = when {
                provider.hasCamera(
                    CameraSelector.DEFAULT_BACK_CAMERA
                ) ->
                    CameraSelector.DEFAULT_BACK_CAMERA

                provider.hasCamera(
                    CameraSelector.DEFAULT_FRONT_CAMERA
                ) ->
                    CameraSelector.DEFAULT_FRONT_CAMERA

                else -> {
                    onError(
                        "No usable camera was found on this device."
                    )
                    return@onSuccess
                }
            }

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(
                        previewView.surfaceProvider
                    )
                }

            val capture = ImageCapture.Builder()
                .setCaptureMode(
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                )
                .build()

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    capture,
                )
            }.onSuccess {
                imageCapture = capture
                cameraReady = true
                cameraMessage = null
            }.onFailure {
                cameraMessage =
                    "The camera could not be started on this device."
            }
        }.onFailure {
            cameraMessage =
                "The camera could not be opened."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    fun takePhoto() {
        val capture = imageCapture

        if (
            capture == null ||
            !cameraReady ||
            capturing
        ) {
            return
        }

        capturing = true
        cameraMessage = null

        val outputFile =
            createTemporaryImageFile(context)
        val outputOptions =
            ImageCapture.OutputFileOptions
                .Builder(outputFile)
                .build()

        capture.takePicture(
            outputOptions,
            mainExecutor,
            object :
                ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(
                    outputFileResults:
                        ImageCapture.OutputFileResults,
                ) {
                    capturing = false
                    onImageCaptured(
                        temporaryImageUri(
                            context = context,
                            file = outputFile,
                        )
                    )
                }

                override fun onError(
                    exception: ImageCaptureException,
                ) {
                    capturing = false
                    cameraMessage =
                        "Could not capture the photo. Please try again."
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.55f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp,
                    ),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = onCancel,
                    enabled = !capturing,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close camera",
                    )
                }

                Text(
                    text = "Take a photo of the text",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Color.Black.copy(alpha = 0.55f)
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            cameraMessage?.let {
                Text(
                    text = it,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (!cameraReady && cameraMessage == null) {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Starting camera…",
                        color = Color.White,
                    )
                }
            }

            Button(
                onClick = ::takePhoto,
                enabled =
                    cameraReady &&
                        !capturing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (capturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text("Saving photo…")
                } else {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = null,
                    )
                    Text(
                        text = "Take Photo",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private suspend fun getCameraProvider(
    context: Context,
): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future =
            ProcessCameraProvider.getInstance(context)
        val executor =
            ContextCompat.getMainExecutor(context)

        future.addListener(
            {
                runCatching {
                    future.get()
                }.onSuccess { provider ->
                    if (continuation.isActive) {
                        continuation.resume(provider)
                    }
                }.onFailure { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            error
                        )
                    }
                }
            },
            executor,
        )

        continuation.invokeOnCancellation {
            future.cancel(true)
        }
    }
