package com.chuahws.mayalanguageapp.presentation.translation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.integration.ml_kit_ocr.MlKitOcrService
import com.chuahws.mayalanguageapp.integration.ml_kit_ocr.createTemporaryImageUri
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
    var cameraUri by remember {
        mutableStateOf<Uri?>(null)
    }
    var readingImage by remember {
        mutableStateOf(false)
    }
    var imageMessage by remember {
        mutableStateOf<String?>(null)
    }

    val cameraAvailable = remember(context) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .resolveActivity(context.packageManager) != null
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
                        "No likely Yucatec Maya text was found. Try a clearer image or enter the Maya text manually."
                } else {
                    viewModel.updateInput(extractedText)
                }
            }.onFailure {
                readingImage = false
                imageMessage =
                    "Could not read this image. Try another image."
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

    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                cameraUri?.let(::readImage)
            }
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
        ) "Yucatec Maya" else "English"

    val target =
        if (state.direction ==
            TranslationDirection.MAYA_TO_ENGLISH
        ) "English" else "Yucatec Maya"

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
                                text = "Scan text from an image",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Choose a clear photo with readable Latin-script text.",
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
                            onClick = {
                                val uri =
                                    createTemporaryImageUri(context)
                                cameraUri = uri
                                cameraLauncher.launch(uri)
                            },
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
                                "Camera",
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
                                "Gallery",
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
                            "No camera app is available on this device. You can still choose an image from Gallery.",
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
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            if (imageUri != null) {
                FilledTonalButton(
                    onClick = {
                        galleryLauncher.launch("image/*")
                    },
                    enabled =
                        !readingImage &&
                            !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding =
                        PaddingValues(vertical = 14.dp),
                ) {
                    Icon(
                        Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                    )
                    Text(
                        text = "Choose Another Image",
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.SemiBold,
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
                                text = "Reading image…",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "ML Kit is extracting the text.",
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
                                text = "$source → $target",
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
                            Text("Detected text will appear here")
                        },
                        supportingText = {
                            Text(
                                if (
                                    state.direction ==
                                        TranslationDirection.MAYA_TO_ENGLISH
                                ) {
                                    "Only likely Yucatec Maya text is shown. English, numbers and OCR noise are removed automatically. Check and correct the text before translating."
                                } else {
                                    "Check and correct the text before translating."
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
                outputLanguageTag = outputLanguageTag,
                onSave =
                    state.matchedEntry?.let { entry ->
                        {
                            onSaveEntry(entry)
                        }
                    },
            )
        }
    }
}
