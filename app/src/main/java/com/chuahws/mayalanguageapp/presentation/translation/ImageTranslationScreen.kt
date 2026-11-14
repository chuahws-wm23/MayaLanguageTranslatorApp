package com.chuahws.mayalanguageapp.presentation.translation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphNetwork
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV2Result
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.TranslationResultCard

/** Photo entry point: new images use glyph recognition, not Latin-script OCR. */
@Composable
fun ImageTranslationScreen(
    viewModel: TranslationViewModel,
    onSaveEntry: (DictionaryEntry) -> Unit,
    replay: TranslationReplay? = null,
) {
    var legacy by remember(replay?.requestId) { mutableStateOf(replay != null) }
    if (legacy && replay != null) {
        LegacyPhotoReplay(viewModel, onSaveEntry, replay, onScanGlyph = { legacy = false })
        return
    }
    GlyphPhotoScreen()
}

@Composable
private fun GlyphPhotoScreen(glyphModel: GlyphRecognitionViewModel = viewModel(key = "glyph-photo-v1")) {
    val context = LocalContext.current
    val state by glyphModel.state.collectAsStateWithLifecycle()
    var showCamera by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val hasCamera = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    DisposableEffect(glyphModel) { onDispose { glyphModel.clear() } }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { permissionMessage = null; glyphModel.load(uri) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { permissionMessage = null; showCamera = true }
        else permissionMessage = "Camera permission was denied. You can still choose an image from Gallery."
    }
    BackHandler(enabled = showCamera) { showCamera = false }
    if (showCamera) {
        CameraCaptureScreen(
            onCancel = { showCamera = false },
            onImageCaptured = { uri -> showCamera = false; glyphModel.load(uri, ownedCapture = true) },
            onError = { error -> showCamera = false; permissionMessage = error },
        )
        return
    }
    AppGradientBackground {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Maya glyph recognition", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Choose one glyph drawing. Crop away words, borders and other symbols before recognising it.")
                    FilledTonalButton(
                        onClick = {
                            permissionMessage = null
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                showCamera = true
                            } else permission.launch(Manifest.permission.CAMERA)
                        }, enabled = hasCamera && !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Take a photo")
                    }
                    FilledTonalButton(
                        onClick = { gallery.launch("image/*") }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Choose an image")
                    }
                    if (!hasCamera) Text("No camera found. Use Gallery instead.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose recognition model", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.model == GlyphModelMode.TINY_V1) {
                            Button(onClick = { glyphModel.setModel(GlyphModelMode.TINY_V1) },
                                enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Model v1", maxLines = 1) }
                        } else {
                            OutlinedButton(onClick = { glyphModel.setModel(GlyphModelMode.TINY_V1) },
                                enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Model v1", maxLines = 1) }
                        }
                        if (state.model == GlyphModelMode.RESEARCH_V2) {
                            Button(onClick = { glyphModel.setModel(GlyphModelMode.RESEARCH_V2) },
                                enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Research v2", maxLines = 1) }
                        } else {
                            OutlinedButton(onClick = { glyphModel.setModel(GlyphModelMode.RESEARCH_V2) },
                                enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Research v2", maxLines = 1) }
                        }
                    }
                    Text(
                        if (state.model == GlyphModelMode.TINY_V1)
                            "v1: Deer, Hawk, Jaguar, Star, Cacao (KAKAW). Unknown drawings may be rejected."
                        else
                            "v2: God/deity, Resplendent, Siip deity, House/first, Cloud/mist. " +
                                "Deer and Cacao are NOT included. It always chooses one of these five signs.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            state.bitmap?.let { image ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Select one glyph", style = MaterialTheme.typography.titleMedium)
                        Text("Drag a rectangle over the complete glyph. Leave a small clear margin.", style = MaterialTheme.typography.bodySmall)
                        GlyphCropPreview(image, state.crop, !state.busy, glyphModel::setCrop)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { glyphModel.setCrop(GlyphCrop()) }, enabled = !state.busy,
                                modifier = Modifier.weight(1f)) { Text("Full image") }
                            OutlinedButton(onClick = glyphModel::rotate, enabled = !state.busy,
                                modifier = Modifier.weight(1f)) { Text("Rotate") }
                        }
                    }
                }
                Button(onClick = glyphModel::recognize, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Recognise glyph")
                }
            }
            if (state.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Processing on this device…")
                }
            }
            (permissionMessage ?: state.message)?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            state.result?.let { GlyphResultCard(it) }
            state.resultV2?.let { GlyphV2ResultCard(it) }
            Text(
                if (state.model == GlyphModelMode.TINY_V1)
                    "TinyGlyphNet v1 · Offline recognition · 29 KB weights. " +
                        "Real-world accuracy is not established."
                else
                    "Research v2 · Offline MobileNetV3 + SVM (ONNX). " +
                        "35/38 original catalogue drawings correct in exploratory five-fold cross-validation; " +
                        "phone-photo accuracy is NOT established.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("All suggested readings require specialist review. Glyph scans are not saved as verified translations.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GlyphResultCard(result: GlyphNetwork.Result) {
    val clipboard = LocalClipboardManager.current
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val candidate = result.suggested
            Text(if (candidate != null) "Possible match — unverified" else "No reliable supported match",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (candidate != null) {
                Text(candidate.english, style = MaterialTheme.typography.headlineSmall)
                Text("Proposed reading: ${candidate.reading}")
                val source = when (candidate.id) {
                    "DEER" -> "TWKM reference 796st"
                    "HAWK" -> "TWKM reference 748st"
                    "JAGUAR" -> "TWKM reference 524st"
                    "STAR" -> "TWKM reference 510bt"
                    else -> "User-supplied cacao example; independent label review pending"
                }
                Text(source, style = MaterialTheme.typography.bodySmall)
                if (candidate.id == "KAKAW") Text("KAKAW is treated as a whole glyph-block example, not a single word-sign.",
                    style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick = {
                    clipboard.setText(AnnotatedString("Possible Maya glyph: ${candidate.reading} — ${candidate.english}. Experimental; unverified."))
                }, modifier = Modifier.fillMaxWidth()) { Text("Copy possible meaning") }
            } else {
                Text("Try a clearer crop of one supported drawing. An unfamiliar glyph may not be covered by this model.")
            }
            Text("Other model suggestions (not verified translations)", style = MaterialTheme.typography.labelLarge)
            result.alternatives.filter { it.id != candidate?.id }.forEach { alternative ->
                Text("${alternative.english} · ${alternative.reading}", style = MaterialTheme.typography.bodyMedium)
            }
            Text("A strong model score can still be wrong. No confidence percentage is shown.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GlyphV2ResultCard(result: GlyphV2Result) {
    val clipboard = LocalClipboardManager.current
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Research v2 — experimental suggestion",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(result.englishGloss, style = MaterialTheme.typography.headlineSmall)
            Text("Proposed reading: ${result.reading}")
            Text("TWKM sign #${result.signNumber}", style = MaterialTheme.typography.bodySmall)
            Surface(color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium) {
                Text("Not a verified translation. Model v2 ALWAYS picks one of five signs even when " +
                    "your drawing is unknown. Do not use this result as evidence of an identified glyph. " +
                    "No calibrated confidence or unknown-glyph detection is available.",
                    Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(
                onClick = { clipboard.setText(AnnotatedString(
                    "Unverified Maya glyph candidate: ${result.reading} — ${result.englishGloss} (TWKM sign ${result.signNumber}). " +
                        "Experimental closed-set recognition; not a confirmed translation."
                )) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copy with disclaimer") }
        }
    }
}

@Composable
private fun LegacyPhotoReplay(
    viewModel: TranslationViewModel,
    onSaveEntry: (DictionaryEntry) -> Unit,
    replay: TranslationReplay,
    onScanGlyph: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(replay.requestId) {
        viewModel.replayStoredTranslation(replay.sourceText, replay.direction, InputType.IMAGE)
    }
    AppGradientBackground {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Text from an older photo scan", style = MaterialTheme.typography.titleLarge)
            Text("This History item contains recognised text, not a glyph image. New photos use glyph recognition.")
            Text(replay.sourceText)
            Button(onClick = onScanGlyph, modifier = Modifier.fillMaxWidth()) { Text("Scan a Maya glyph instead") }
            if (state.loading) CircularProgressIndicator()
            state.message?.let { Text(it) }
            state.advice?.let { Text(it) }
            TranslationResultCard(
                output = state.output, entry = state.matchedEntry, alternatives = state.alternativeEntries,
                outputLanguageTag = if (state.direction == com.chuahws.mayalanguageapp.domain.model.TranslationDirection.MAYA_TO_ENGLISH) "en-US" else "yua",
                onSave = state.matchedEntry?.let { entry -> { onSaveEntry(entry) } },
                onSaveAlternative = onSaveEntry,
                approximate = state.approximate,
                needsReview = state.needsReview,
            )
        }
    }
}
