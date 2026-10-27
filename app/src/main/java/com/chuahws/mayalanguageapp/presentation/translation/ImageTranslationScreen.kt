package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.integration.ml_kit_ocr.MlKitOcrService
import com.chuahws.mayalanguageapp.presentation.components.AccentIcon
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.TranslationResultCard
import com.chuahws.mayalanguageapp.presentation.theme.MayaPink
import kotlinx.coroutines.launch

@Composable
fun ImageTranslationScreen(
    viewModel: TranslationViewModel,
    onSaveEntry: (DictionaryEntry) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val ocrService = remember {
        MlKitOcrService()
    }

    var imageUri by remember {
        mutableStateOf<Uri?>(null)
    }
    var showCamera by remember {
        mutableStateOf(false)
    }
    var readingImage by remember {
        mutableStateOf(false)
    }
    var imageMessage by remember {
        mutableStateOf<String?>(null)
    }

    val cameraAvailable = remember(context) {
        context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_CAMERA_ANY
        )
    }

    LaunchedEffect(Unit) {
        viewModel.setDirection(
            TranslationDirection.MAYA_TO_ENGLISH
        )
    }

    fun readImage(uri: Uri) {
        // A newly selected image starts a fresh OCR/translation attempt.
        // This removes the previous extracted text and translation result
        // only after the user actually chooses or captures a new image.
        viewModel.clear()
        imageUri = uri
        readingImage = true
        imageMessage = null

        scope.launch {
            runCatching {
                ocrService.readText(context, uri)
            }.onSuccess { text ->
                val extractedText =
                    if (
                        state.direction ==
                            TranslationDirection.MAYA_TO_ENGLISH
                    ) {
                        viewModel.filterMayaOcrText(text)
                    } else {
                        text
                            .lineSequence()
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .filter { line ->
                                line.any(Char::isLetter)
                            }
                            .joinToString("\n")
                    }

                readingImage = false

                if (text.isBlank()) {
                    imageMessage =
                        "No text was found. Try a clearer image."
                } else if (extractedText.isBlank()) {
                    viewModel.updateInput("")
                    imageMessage =
                        "No readable Maya text was found. Try a clearer image or type the text instead."
                } else {
                    viewModel.updateInput(extractedText)
                }
            }.onFailure {
                readingImage = false
                imageMessage =
                    "Couldn’t read this image. Try another one."
            }
        }
    }

    val galleryLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                readImage(uri)
            }
        }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                showCamera = true
                imageMessage = null
            } else {
                imageMessage =
                    "Camera permission is required to take a photo. You can still use Gallery."
            }
        }

    fun openCamera() {
        if (!cameraAvailable) {
            imageMessage =
                "No usable camera hardware is available on this device. You can still use Gallery."
            return
        }

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            showCamera = true
            imageMessage = null
        } else {
            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    if (showCamera) {
        CameraCaptureScreen(
            onCancel = {
                showCamera = false
            },
            onImageCaptured = { uri ->
                showCamera = false
                readImage(uri)
            },
            onError = { message ->
                showCamera = false
                imageMessage = message
            },
        )
        return
    }

    val preview by produceState<Bitmap?>(
        initialValue = null,
        key1 = imageUri,
    ) {
        value = imageUri?.let { uri ->
            runCatching {
                context.contentResolver
                    .openInputStream(uri)
                    ?.use(
                        BitmapFactory::decodeStream
                    )
            }.getOrNull()
        }
    }

    val source =
        if (state.direction ==
            TranslationDirection.MAYA_TO_ENGLISH
        ) "Maya" else "English"

    val target =
        if (state.direction ==
            TranslationDirection.MAYA_TO_ENGLISH
        ) "English" else "Maya"

    val outputLanguageTag =
        if (state.direction ==
            TranslationDirection.MAYA_TO_ENGLISH
        ) "en-US" else "yua"

    AppGradientBackground {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        start = 18.dp,
                        top = 18.dp,
                        end = 18.dp,
                        bottom = 112.dp,
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AccentIcon(
                            icon = Icons.Rounded.ImageSearch,
                            color = MayaPink,
                        )
                        Column {
                            Text(
                                text = "Translate from a photo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text =
                                    if (imageUri == null) {
                                        "Choose a clear photo with readable text."
                                    } else {
                                        "Take another photo or choose a different one."
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = ::openCamera,
                            enabled =
                                cameraAvailable &&
                                    !readingImage &&
                                    !state.loading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Rounded.CameraAlt,
                                contentDescription = null,
                            )
                            Text(
                                if (imageUri == null) {
                                    "Camera"
                                } else {
                                    "Take photo"
                                },
                                modifier =
                                    Modifier.padding(start = 8.dp),
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                galleryLauncher.launch("image/*")
                            },
                            enabled =
                                !readingImage &&
                                    !state.loading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                            )
                            Text(
                                if (imageUri == null) {
                                    "Gallery"
                                } else {
                                    "Choose another"
                                },
                                modifier =
                                    Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            if (!cameraAvailable) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text =
                            "No usable camera hardware is available on this device. You can still choose an image from Gallery.",
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            preview?.let { bitmap ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 180.dp,
                                max = 420.dp,
                            ),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            if (readingImage) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement =
                            Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator()
                        Column {
                            Text(
                                text = "Reading your photo…",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "This may take a moment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            imageMessage?.let {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Extracted text",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "$source to $target",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        FilledTonalIconButton(
                            onClick = viewModel::swapDirection,
                            enabled = !state.loading,
                        ) {
                            Icon(
                                Icons.Rounded.SwapHoriz,
                                contentDescription =
                                    "Swap languages",
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.input,
                        onValueChange = viewModel::updateInput,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !readingImage &&
                            !state.loading,
                        placeholder = {
                            Text("Text from your photo will appear here")
                        },
                        supportingText = {
                            Text(
                                if (
                                    state.direction ==
                                        TranslationDirection.MAYA_TO_ENGLISH
                                ) {
                                    "Only likely Maya text is kept. Check it before translating."
                                } else {
                                    "Check the text before translating."
                                }
                            )
                        },
                        minLines = 4,
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.translate(InputType.IMAGE)
                },
                enabled =
                    !state.loading &&
                        !readingImage &&
                        state.input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 15.dp),
            ) {
                Text(
                    text = "Translate",
                    fontWeight = FontWeight.SemiBold,
                )
            }

            state.message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TranslationResultCard(
                output = state.output,
                entry = state.matchedEntry,
                alternatives = state.alternativeEntries,
                outputLanguageTag = outputLanguageTag,
                onSave =
                    state.matchedEntry?.let { entry ->
                        {
                            onSaveEntry(entry)
                        }
                    },
                onSaveAlternative = onSaveEntry,
            )
        }
    }
}
