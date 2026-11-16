package com.chuahws.mayalanguageapp.presentation.translation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.integration.glyph.GlyphNetwork
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV2Result
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV3Result
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV4Result
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV25Result
import com.chuahws.mayalanguageapp.integration.glyph.MayaGlyphRecognizerV25
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV11SegDetector
import com.chuahws.mayalanguageapp.integration.glyph.GlyphV4CorrectionReport
import com.chuahws.mayalanguageapp.integration.glyph.MayaGlyphRecognizerV4
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
    val screenScroll = rememberScrollState()
    val screenScope = rememberCoroutineScope()
    var showCamera by remember { mutableStateOf(false) }
    var showAssistedGrid by remember { mutableStateOf(false) }
    var showAdvancedPanelTools by remember { mutableStateOf(false) }
    var showResearchModelOptions by remember { mutableStateOf(false) }
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var browseSyllables by rememberSaveable { mutableStateOf(false) }
    var showSoundComparison by remember { mutableStateOf(false) }
    var showAdvancedTools by remember { mutableStateOf(false) }
    var gridRows by remember { mutableIntStateOf(9) }
    var gridColumns by remember { mutableIntStateOf(9) }
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
            modifier = Modifier.fillMaxWidth().verticalScroll(screenScroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Maya glyph explorer", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("Discover possible English meanings and learn the history behind Maya signs.",
                        style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !showLibrary, onClick = { showLibrary = false },
                            label = { Text("Scan a picture") }, modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = showLibrary, onClick = { showLibrary = true },
                            label = { Text("Explore signs") }, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (!showLibrary) {
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1 · Choose an image", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Upload a clear glyph or inscription. Then select one sign to compare " +
                        "with the reference catalogue.", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { gallery.launch("image/*") }, enabled = !state.busy,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.bitmap == null) "Gallery" else "Change photo", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                permissionMessage = null
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                    == PackageManager.PERMISSION_GRANTED) {
                                    showCamera = true
                                } else permission.launch(Manifest.permission.CAMERA)
                            }, enabled = hasCamera && !state.busy,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Camera", maxLines = 1)
                        }
                    }
                    if (!hasCamera) Text("No camera found. Use Gallery instead.", style = MaterialTheme.typography.bodySmall)
                }
            }
            }
            if (showLibrary) {
                Text("Browse Maya glyphs", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !browseSyllables, onClick = { browseSyllables = false },
                        label = { Text("Meanings (135)", maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = browseSyllables, onClick = { browseSyllables = true },
                        label = { Text("Syllables (86)", maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (browseSyllables) {
                    GlyphCataloguePanel(bitmap = null, crop = GlyphCrop(), busy = false,
                        browseOnly = true)
                } else {
                    EnglishMeaningExploreCard(bitmap = null, crop = GlyphCrop(), busy = false,
                        browseOnly = true)
                }
            } else {
            if (state.bitmap != null) {
                OutlinedButton(
                    onClick = { showAdvancedTools = !showAdvancedTools },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (showAdvancedTools) "Hide research settings" else
                        "Advanced AI models & research settings")
                }
            }
            if (showAdvancedTools && state.bitmap != null) {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        when (state.model) {
                            GlyphModelMode.RESEARCH_V4 -> "Research v4 · 10 supported signs · offline"
                            GlyphModelMode.RESEARCH_25 -> "TWKM 25-sign study · optional research model"
                            else -> "Experimental legacy recognition mode"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(
                        onClick = { showResearchModelOptions = !showResearchModelOptions },
                        enabled = !state.busy,
                    ) { Text(if (showResearchModelOptions) "Hide research options" else "Change research model (advanced)") }
                    if (showResearchModelOptions) {
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
                    if (state.model == GlyphModelMode.RESEARCH_V3) {
                        Button(
                            onClick = { glyphModel.setModel(GlyphModelMode.RESEARCH_V3) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Research v3 (screened)") }
                    } else {
                        OutlinedButton(
                            onClick = { glyphModel.setModel(GlyphModelMode.RESEARCH_V3) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Research v3 (screened)") }
                    }
                    if (state.model == GlyphModelMode.RESEARCH_V4) {
                        Button(
                            onClick = { glyphModel.setModel(GlyphModelMode.RESEARCH_V4) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Research v4 (10 signs)") }
                    } else {
                        OutlinedButton(
                            onClick = { glyphModel.setModel(GlyphModelMode.RESEARCH_V4) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Research v4 (10 signs)") }
                    }
                    if (state.model == GlyphModelMode.RESEARCH_25) {
                        Button(
                            onClick = { glyphModel.setModel(GlyphModelMode.RESEARCH_25) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("TWKM 25 (research)") }
                    } else {
                        OutlinedButton(
                            onClick = { glyphModel.setModel(GlyphModelMode.RESEARCH_25) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("TWKM 25 (research)") }
                    }
                    }
                    val modelDescription = when (state.model) {
                        GlyphModelMode.TINY_V1 ->
                            "v1: Deer, Hawk, Jaguar, Star, Cacao (KAKAW). Unknown drawings may be rejected."
                        GlyphModelMode.RESEARCH_V2 ->
                            "v2: God/deity, Resplendent, Siip deity, House/first, Cloud/mist. " +
                                "Deer and Cacao are NOT included. v2 always chooses one of its five signs."
                        GlyphModelMode.RESEARCH_V3 ->
                            "v3: Same five signs as v2, with experimental unknown-glyph screening. " +
                                "It can reject valid drawings and still accept incorrect ones. Deer/Cacao NOT included."
                        GlyphModelMode.RESEARCH_V4 ->
                            "v4 (default): Ten TWKM sign categories. Works on photos chosen from Gallery or Camera " +
                                "without a catalogue lookup. It compares two photo treatments and " +
                                "flags unstable results. Unknown signs can still be misidentified; Deer/Cacao are NOT included."
                        GlyphModelMode.RESEARCH_25 ->
                            "EXPERIMENTAL research: 25 sign IDs (NOT 25 verified readings). " +
                                "Choose ONE isolated sign, not a multi-sign block. " +
                                "Requires separately installed ONNX research assets; " +
                                "catalogue accuracy is not phone-photo accuracy."
                    }
                    Text(modelDescription, style = MaterialTheme.typography.bodySmall)
                    if (state.model == GlyphModelMode.RESEARCH_25 &&
                        !MayaGlyphRecognizerV25.isInstalled(context)) {
                        Text(
                            "Experimental 25-sign model not installed. Download the verified four-file " +
                                "model artifact and copy it into app/src/main/assets before rebuilding.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            }
            state.bitmap?.let { image ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("2 · Select one glyph", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text("Drag a rectangle around one complete sign. " +
                            "For an inscription with many signs, use component scanning instead.",
                            style = MaterialTheme.typography.bodySmall)
                        GlyphCropPreview(image, state.crop, !state.busy, glyphModel::setCrop)
                        if (state.crop != GlyphCrop()) {
                            TextButton(
                                onClick = { glyphModel.setCrop(GlyphCrop()) },
                                enabled = !state.busy,
                            ) { Text("Reset crop") }
                        }
                    }
                }
                EnglishMeaningExploreCard(
                    bitmap = image, crop = state.crop, busy = state.busy,
                    matchOnly = true,
                )
                if (GlyphV11SegDetector.isInstalled(context)) {
                    OutlinedButton(
                        onClick = glyphModel::scanComponents,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Multiple glyphs? Find individual components") }
                }
                TextButton(
                    onClick = { showSoundComparison = !showSoundComparison },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (showSoundComparison) "Hide syllable comparison" else
                        "Also compare syllable sounds (optional)")
                }
                if (showSoundComparison) {
                    GlyphCataloguePanel(
                        bitmap = image, crop = state.crop, busy = state.busy,
                        matchOnly = true,
                    )
                }
                if (showAdvancedTools) {
                    Button(onClick = glyphModel::recognize, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Recognise with selected research model")
                    }
                }
                if (showAdvancedTools) {
                OutlinedButton(
                    onClick = { showAdvancedPanelTools = !showAdvancedPanelTools },
                    enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                ) { Text(if (showAdvancedPanelTools) "Hide dense-panel tools" else
                    "Block detection and manual grid") }
                if (showAdvancedPanelTools) {
                    if (GlyphV11SegDetector.isInstalled(context)) {
                        FilledTonalButton(
                            onClick = glyphModel::scanComponents,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("Find individual glyph components (research)") }
                    } else {
                        Text(
                            "Individual-component model not installed. Export your trained v11 " +
                                "best.pt as maya_signs_v11_seg.onnx into app/src/main/assets " +
                                "and rebuild to enable the research-only component scan.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = glyphModel::scanAll, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Detect inscription blocks (advanced)")
                    }
                Text(
                    "Uses a trained offline v18 block detector when installed and validated; " +
                        "otherwise retains the original regular-grid fallback. " +
                        "No need to crop each block manually. Research v4 recognition " +
                        "still provides only unverified ten-sign suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { showAssistedGrid = !showAssistedGrid },
                    enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                ) { Text(if (showAssistedGrid) "Hide grid-assisted scan" else "Grid-assisted scan (dense panels)") }
                if (showAssistedGrid) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("For photos with many closely packed symbols",
                                style = MaterialTheme.typography.titleSmall)
                            Text("Drag one crop over the whole glyph panel above, then choose " +
                                "its approximate rows and columns. This makes approximate " +
                                "blocks without cropping each glyph separately. " +
                                "Readings remain experimental and may be wrong.",
                                style = MaterialTheme.typography.bodySmall)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Rows: $gridRows")
                                    Slider(
                                        value = gridRows.toFloat(),
                                        onValueChange = { gridRows = it.toInt().coerceIn(2, 12) },
                                        valueRange = 2f..12f, steps = 9,
                                        enabled = !state.busy,
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Columns: $gridColumns")
                                    Slider(
                                        value = gridColumns.toFloat(),
                                        onValueChange = { gridColumns = it.toInt().coerceIn(2, 12) },
                                        valueRange = 2f..12f, steps = 9,
                                        enabled = !state.busy,
                                    )
                                }
                            }
                            Button(
                                onClick = { glyphModel.scanGrid(gridRows, gridColumns) },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            ) { Text("Scan $gridRows × $gridColumns approximate blocks") }
                        }
                    }
                }
                }
                }
            }
            if (state.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(state.multipleProgress ?: "Processing on this device…")
                }
            }
            (permissionMessage ?: state.message)?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            state.result?.let { GlyphResultCard(it) }
            state.resultV2?.let { GlyphV2ResultCard(it) }
            state.resultV3?.let { GlyphV3ResultCard(it) }
            state.resultV4?.let { result ->
                GlyphV4ResultCard(
                    result = result,
                    image = state.bitmap,
                    crop = state.crop,
                    onTryAnother = { gallery.launch("image/*") },
                )
            }
            state.resultV25?.let { result -> GlyphV25ResultCard(result) }
            state.bitmap?.let { bitmap ->
                if (state.multiple.isNotEmpty()) {
                    MultiGlyphResultsPanel(
                        bitmap = bitmap,
                        results = state.multiple,
                        assisted = state.assistedGrid,
                        individualComponents = state.individualComponents,
                        detectorNote = state.detectorNote,
                        onInspect = { selected ->
                            glyphModel.setCrop(selected)
                            screenScope.launch { screenScroll.animateScrollTo(0) }
                        },
                    )
                }
            }
            val modelLimitations = when (state.model) {
                GlyphModelMode.TINY_V1 ->
                    "TinyGlyphNet v1 · Offline model · Real-world accuracy is not established."
                GlyphModelMode.RESEARCH_V2 ->
                    "Research v2 · Offline MobileNetV3 + SVM. 35/38 correct in exploratory " +
                        "catalogue cross-validation. The model cannot reject unfamiliar signs."
                GlyphModelMode.RESEARCH_V3 ->
                    "Research v3 · v2 model plus small reference-feature screening. " +
                        "24/38 catalog originals correctly accepted; 90.6% mean rejection on " +
                        "100 unknown sign drawings tested over five fold models. " +
                        "NOT independent phone-photo accuracy."
                GlyphModelMode.RESEARCH_V4 ->
                    "Research v4 · Same fine-tuned MobileNetV3 weights, ten sign categories; " +
                        "two photo views (original and shadow-corrected). Agreement is NOT proof " +
                        "that an unfamiliar sign is supported. Previous 7/10 test came from " +
                        "catalogue drawings only; real photo accuracy is still unmeasured."
                GlyphModelMode.RESEARCH_25 ->
                    "TWKM 25-sign experimental frozen MobileNetV3+SVM: 17/25 on a small, " +
                        "previously exposed catalogue cohort; only 12/25 were correctly accepted. " +
                        "No independent photo accuracy established; do not treat signs as translated text."
            }
            if (showAdvancedTools) Text(
                modelLimitations, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("All suggested readings require specialist review. Glyph scans are not saved as verified translations.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            }
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
private fun GlyphV3ResultCard(result: GlyphV3Result) {
    val clipboard = LocalClipboardManager.current
    val candidate = result.possibleMatch
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (candidate == null) "No reliable supported match"
                else "Research v3 — possible match (unverified)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (candidate != null) {
                Text(candidate.englishGloss, style = MaterialTheme.typography.headlineSmall)
                Text("Proposed reading: ${candidate.reading}")
                Text("TWKM sign #${candidate.signNumber}", style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(
                            "Experimental Maya glyph candidate: ${candidate.reading} — " +
                                "${candidate.englishGloss}, TWKM sign ${candidate.signNumber}. " +
                                "Unverified, may be incorrect.",
                        ))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Copy with disclaimer") }
            } else {
                Text(
                    "This model could not safely distinguish the drawing from unrelated signs. " +
                        "Crop one glyph, or try a clearer picture. It might also be outside " +
                        "the five supported v3 sign classes.",
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "Experimental TWKM-based screening only. It may reject correct signs or " +
                        "accept unknown ones. No verified translation or confidence percentage " +
                        "is available. Real phone-photo accuracy has not been established.",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun GlyphV25ResultCard(result: GlyphV25Result) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TWKM 25 · experimental visual identification",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (result.accepted && result.proposedSignNumber != null)
                    "Possible catalogue sign #${result.proposedSignNumber} (UNVERIFIED)"
                else "Not identified / needs review",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(result.explanation, style = MaterialTheme.typography.bodySmall)
            Text("This result is NOT a Classical Maya reading or an English translation. " +
                "Do not use it as an archaeological identification without expert review.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GlyphV4ResultCard(result: GlyphV4Result, image: Bitmap?, crop: GlyphCrop, onTryAnother: () -> Unit) {
    val context = LocalContext.current
    val reference = remember(context, result.signNumber) {
        runCatching {
            context.assets.open("research_v4_ref_${result.signNumber}.png").use {
                BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
    }
    val clipboard = LocalClipboardManager.current
    var showCorrection by remember(result) { mutableStateOf(false) }
    var selectedCorrection by remember(result) { mutableStateOf("UNKNOWN_OR_OTHER") }
    // Explicit user rejection: do not present an unsupported glyph as a translation.
    var markedUnsupported by remember(result) { mutableStateOf(false) }
    var sourceDescription by remember(result) { mutableStateOf("") }
    var notes by remember(result) { mutableStateOf("") }
    var exportContent by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val data = exportContent
            val wrote = data != null && runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(data.toByteArray(Charsets.UTF_8))
                    stream.flush()
                } ?: error("Could not open the selected file")
            }.isSuccess
            Toast.makeText(
                context,
                if (wrote) "Unverified review report saved" else "Could not save the review report",
                Toast.LENGTH_SHORT,
            ).show()
        }
        exportContent = null
    }


    // User opt-in only. Save the exact selected crop alongside a correction CSV.
    // No image or annotation is uploaded or silently used for training.
    val imageExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                val source = requireNotNull(image) { "Source picture no longer available" }
                val (x, y, w, h) = crop.pixels(source.width, source.height)
                require(w >= 25 && h >= 25) { "Select a complete glyph before exporting" }
                val selected = Bitmap.createBitmap(source, x, y, w, h)
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        check(selected.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                            "PNG encoding failed"
                        }
                        stream.flush()
                    } ?: error("Could not open selected PNG document")
                } finally {
                    if (selected !== source) selected.recycle()
                }
            }.isSuccess
            Toast.makeText(
                context,
                if (saved) "Selected glyph crop saved" else "Could not save selected glyph crop",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (result.reviewRecommended || markedUnsupported) "No glyph confirmed · needs review"
                else "Possible glyph match · Research v4",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (markedUnsupported) {
                Text("You marked this as an unsupported or incorrect sign. " +
                    "The AI alternatives below are only guesses, not verified translations. " +
                    "Choose a different image or export this case for review.")
            } else if (result.reviewRecommended) {
                Text(result.reviewReason ?: "Try a clearer crop of one complete glyph.")
            } else {
                Text(result.reading, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(result.englishGloss, style = MaterialTheme.typography.bodyLarge)
                Text("TWKM sign #${result.signNumber}", style = MaterialTheme.typography.labelMedium)
                GlyphLearningDetails(signNumber = result.signNumber)
                if (reference != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Catalogue reference drawing",
                                style = MaterialTheme.typography.labelLarge)
                            Image(
                                bitmap = reference.asImageBitmap(),
                                contentDescription = "TWKM catalogue example for sign #${result.signNumber}",
                                modifier = Modifier.size(140.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Text(
                                "For visual comparison only — not proof of an identification.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "TWKM / University of Bonn, Christian Prager · CC BY 4.0",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (result.alternatives.isNotEmpty()) {
                Text("Top 3 model candidates", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                result.alternatives.take(3).forEachIndexed { index, choice ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${choice.reading} · #${choice.signNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f))
                        Text(String.format(java.util.Locale.US, "%.1f%%", choice.relativeScorePercent),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    "These percentages are relative neural-network scores, NOT calibrated " +
                        "confidence or the chance a Maya reading is correct. " +
                        "Unfamiliar signs may still produce a high score.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "Experimental ten-sign classifier. All readings and English glosses " +
                        "are provisional; no real-world phone-photo accuracy has been established. " +
                        "A visually similar sign can still be misclassified.",
                    Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!result.reviewRecommended && !markedUnsupported) {
                FilledTonalButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(
                            "UNVERIFIED TWKM glyph candidate: ${result.reading}, #${result.signNumber} — " +
                                "${result.englishGloss}. Requires expert review.",
                        ))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Copy possible reading") }
            }
            OutlinedButton(
                onClick = { markedUnsupported = !markedUnsupported },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (markedUnsupported) "Show possible model suggestion" else "Not my glyph / unsupported sign") }
            OutlinedButton(
                onClick = { showCorrection = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Review / correct the glyph label") }
            OutlinedButton(
                onClick = onTryAnother,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Choose another image") }
        }
    }

    if (showCorrection) {
        AlertDialog(
            onDismissRequest = { showCorrection = false },
            title = { Text("Review this result") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Choose from ALL ten supported TWKM signs, or Unknown / other. " +
                        "Use the reference drawings for visual comparison. " +
                        "A specialist must verify this report before it becomes training data.",
                        style = MaterialTheme.typography.bodySmall)
                    val options = listOf("UNKNOWN_OR_OTHER" to "Unknown / other") +
                        MayaGlyphRecognizerV4.supportedSigns().map { sign ->
                            sign.signNumber.toString() to "${sign.reading} · TWKM #${sign.signNumber}"
                        }
                    options.forEach { (id, label) ->
                        val signId = id.toIntOrNull()
                        val example = remember(context, signId) {
                            signId?.let { sign ->
                                runCatching {
                                    context.assets.open("research_v4_ref_${sign}.png").use {
                                        BitmapFactory.decodeStream(it)
                                    }
                                }.getOrNull()
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedCorrection == id,
                                onClick = { selectedCorrection = id },
                            )
                            if (example != null) {
                                Image(
                                    bitmap = example.asImageBitmap(),
                                    contentDescription = "Reference for TWKM sign ${signId}",
                                    modifier = Modifier.size(48.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(label, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                        }
                    }
                    OutlinedTextField(
                        value = sourceDescription, onValueChange = { sourceDescription = it },
                        label = { Text("Image source / drawing ID (optional)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        label = { Text("Why is it wrong? (optional)") },
                        minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                    )
                    Text("To build a future training set, save BOTH the CSV and the matching cropped PNG. " +
                        "They stay on your device until you explicitly share them. " +
                        "Only include images you have permission to use.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { imageExport.launch("maya-glyph-v4-review-crop.png") },
                        enabled = image != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Export selected glyph crop (PNG)") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    exportContent = GlyphV4CorrectionReport.csv(
                        result, selectedCorrection, sourceDescription, notes,
                    )
                    showCorrection = false
                    export.launch("maya-glyph-v4-review.csv")
                }) { Text("Export CSV") }
            },
            dismissButton = {
                TextButton(onClick = { showCorrection = false }) { Text("Cancel") }
            },
        )
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
