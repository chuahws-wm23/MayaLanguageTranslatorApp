package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.service.PhotoLanguageFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.integration.ml_kit_ocr.MlKitOcrService
import com.chuahws.mayalanguageapp.presentation.components.AccentIcon
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.TranslationGuidanceCard
import com.chuahws.mayalanguageapp.presentation.components.TranslationResultCard
import com.chuahws.mayalanguageapp.presentation.theme.MayaPink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun ImageTranslationScreen(
    viewModel: TranslationViewModel,
    onSaveEntry: (DictionaryEntry) -> Unit,
    replay: TranslationReplay? = null,
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
    // Preserve OCR from the photo so switching languages never translates
    // stale words from the previously selected direction.
    var scannedText by remember { mutableStateOf<String?>(null) }
    var reviewedText by remember { mutableStateOf<String?>(null) }
    var filteredText by remember {
        mutableStateOf<PhotoLanguageFilter.Result?>(null)
    }
    var filteringImage by remember { mutableStateOf(false) }
    var scanVersion by remember { mutableStateOf(0) }
    var reviewingOcr by remember { mutableStateOf(false) }
    var correctionDraft by remember { mutableStateOf("") }
    var yucatecSuggestions by remember {
        mutableStateOf<List<Pair<String, DictionaryEntry>>>(emptyList())
    }

    val cameraAvailable = remember(context) {
        context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_CAMERA_ANY
        )
    }

    LaunchedEffect(replay?.requestId) {
        if (replay != null) {
            // History stores the exact OCR-derived text, not the photo URI.
            // Display it in the read-only field and translate it again.
            scanVersion += 1
            imageUri = null
            scannedText = replay.sourceText
            reviewedText = null
            filteredText = null
            readingImage = false
            filteringImage = false
            imageMessage =
                "Reopened detected text from History. The original photo was not saved."
            viewModel.replayStoredTranslation(
                replay.sourceText,
                replay.direction,
                InputType.IMAGE,
            )
        } else {
            viewModel.setDirection(
                TranslationDirection.MAYA_TO_ENGLISH
            )
        }
    }

    fun readImage(uri: Uri) {
        scanVersion += 1
        val requestVersion = scanVersion
        viewModel.clear()
        imageUri = uri
        scannedText = null
        reviewedText = null
        filteredText = null
        yucatecSuggestions = emptyList()
        readingImage = true
        imageMessage = null

        scope.launch {
            try {
                val recognized = ocrService.readText(context, uri)
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .joinToString("\n")

                if (requestVersion == scanVersion) {
                    scannedText = recognized
                    reviewedText = recognized
                    if (recognized.isBlank()) {
                        imageMessage =
                            "No readable text found. Try a clearer photo or crop closer to the words."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestVersion == scanVersion) {
                    imageMessage =
                        "This image could not be read. Try another photo."
                }
            } finally {
                if (requestVersion == scanVersion) {
                    readingImage = false
                }
            }
        }
    }

    // The chosen language controls which supported OCR words are displayed.
    // The original scan is retained even when no words match this direction.
    LaunchedEffect(reviewedText, state.direction) {
        // Do not overwrite an exact replay with a fresh dictionary filter.
        if (replay != null && reviewedText == null) {
            filteringImage = false
            return@LaunchedEffect
        }
        filteredText = null
        yucatecSuggestions = emptyList()
        viewModel.updateInput("")
        val raw = reviewedText.orEmpty()
        if (raw.isBlank()) {
            // Preserve the helpful "no readable text" message from OCR.
            filteringImage = false
            return@LaunchedEffect
        }
        imageMessage = null
        filteringImage = true
        try {
            val result = viewModel.filterPhotoText(raw, state.direction)
            filteredText = result
            viewModel.updateInput(result.text)

        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            filteredText = null
            viewModel.updateInput("")
            imageMessage =
                "Couldn't check the photo words against the dictionary. Check your connection and try again."
        } finally {
            if (currentCoroutineContext().isActive) {
                filteringImage = false
            }
        }
    }

    // Optional Yucatec spelling hints are loaded after filtering, so a
    // slow dictionary query cannot hold up the photo or Translate button.
    LaunchedEffect(filteredText, state.direction) {
        yucatecSuggestions = emptyList()
        val result = filteredText
        if (
            result == null ||
            state.direction != TranslationDirection.MAYA_TO_ENGLISH ||
            result.skippedWords.isEmpty()
        ) return@LaunchedEffect

        val possible = mutableListOf<Pair<String, DictionaryEntry>>()
        for (term in result.skippedWords.take(3)) {
            val candidates = try {
                viewModel.possibleYucatecSpelling(term)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            candidates.take(2).forEach { entry ->
                possible += term to entry
            }
        }
        yucatecSuggestions = possible
            .distinctBy { it.second.entryId }
            .take(3)
    }

    fun showReview(original: String = reviewedText.orEmpty()) {
        correctionDraft = original.take(2500)
        reviewingOcr = true
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
                ocrService.loadPreviewBitmap(context, uri)
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

    if (reviewingOcr) {
        AlertDialog(
            onDismissRequest = { reviewingOcr = false },
            title = { Text("Review photo recognition") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Correct recognition mistakes here. The detected-text area on the photo screen stays read-only.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = correctionDraft,
                        onValueChange = { text ->
                            if (text.length <= 2500) correctionDraft = text
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 9,
                        label = { Text("Text recognised in photo") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reviewingOcr = false
                        viewModel.clear()
                        reviewedText = correctionDraft
                    }
                ) { Text("Apply correction") }
            },
            dismissButton = {
                TextButton(onClick = { reviewingOcr = false }) {
                    Text("Cancel")
                }
            },
        )
    }

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
                                text = "Camera",
                                maxLines = 1,
                                softWrap = false,
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
                                text = "Gallery",
                                maxLines = 1,
                                softWrap = false,
                                modifier =
                                    Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            PhotoDirectionBar(
                direction = state.direction,
                enabled = !readingImage && !filteringImage && !state.loading,
                onSwitch = {
                    viewModel.swapDirection()
                    viewModel.clear()
                    if (replay != null && reviewedText == null) {
                        // A direction switch requests a new language filter.
                        reviewedText = scannedText
                    }
                },
            )

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
                    Text(
                        text = "Detected $source words",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Only words or phrases supported by the $source dictionary are shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = {},
                        readOnly = true,
                        enabled = true,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                        placeholder = {
                            Text("Recognised $source text will appear here")
                        },
                        supportingText = {
                            Text("Read-only text extracted from this photo")
                        },
                    )

                    if (scannedText?.isNotBlank() == true) {
                        OutlinedButton(
                            onClick = { showReview() },
                            enabled = !readingImage && !filteringImage && !state.loading,
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = null,
                            )
                            Text(
                                text = "Review OCR",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            filteredText?.let { result ->
                val explanation = when {
                    result.matchedWordCount == 0 ->
                        "No supported $source words found in this photo. Try the other direction, review OCR, or take a clearer picture."
                    result.skippedWordCount > 0 ->
                        "Found ${result.matchedWordCount} supported $source word(s). ${result.skippedWordCount} other word(s) were left out because their language or dictionary match was uncertain."
                    else ->
                        "Found ${result.matchedWordCount} supported $source word(s) in the photo."
                }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = explanation,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (result.limited) {
                            Text(
                                text = "Only part of the scan was checked. Crop the photo to a shorter section for better results.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (
                            state.direction == TranslationDirection.MAYA_TO_ENGLISH &&
                            yucatecSuggestions.isNotEmpty()
                        ) {
                            Text(
                                text = "Possible Yucatec Maya spellings (check before using):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            yucatecSuggestions.forEach { (original, match) ->
                                OutlinedButton(
                                    onClick = {
                                        val raw = reviewedText.orEmpty()
                                        val corrected = raw.replace(
                                            original,
                                            match.mayaText,
                                            ignoreCase = true,
                                        )
                                        showReview(corrected)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "${original} → ${match.mayaText}",
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.translate(InputType.IMAGE)
                },
                enabled =
                    !state.loading &&
                        !readingImage &&
                        !filteringImage &&
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
                approximate = state.approximate,
                needsReview = state.needsReview,
            )

            TranslationGuidanceCard(
                advice = state.advice,
                unknownTerms = state.unknownTerms,
                ambiguousTerms = state.ambiguousTerms,
                unreliableSegments = state.unreliableSegments,
                suggestions = state.suggestions,
                direction = state.direction,
                onSuggestionSelected = { suggestion ->
                    val original = state.unknownTerms.singleOrNull()
                    val reviewed = reviewedText.orEmpty()
                    val corrected = if (original != null) {
                        reviewed.replace(
                            original,
                            suggestion,
                            ignoreCase = true,
                        )
                    } else {
                        reviewed
                    }
                    showReview(corrected)
                },
            )
        }
    }
}

@Composable
private fun PhotoDirectionBar(
    direction: TranslationDirection,
    enabled: Boolean,
    onSwitch: () -> Unit,
) {
    val from = if (direction == TranslationDirection.MAYA_TO_ENGLISH) {
        "Yucatec Maya"
    } else {
        "English"
    }
    val to = if (direction == TranslationDirection.MAYA_TO_ENGLISH) {
        "English"
    } else {
        "Yucatec Maya"
    }
    FilledTonalButton(
        onClick = onSwitch,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = 12.dp,
            vertical = 12.dp,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "FROM",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = from,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
            }
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "TO",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = to,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
            }
        }
    }
}
