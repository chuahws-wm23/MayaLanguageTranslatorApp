package com.chuahws.mayalanguageapp.presentation.translation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.GlyphScanCapture
import com.chuahws.mayalanguageapp.domain.model.GlyphScanCandidate
import com.chuahws.mayalanguageapp.domain.model.GlyphScanRecords
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.integration.glyph.GlyphScanPreviewCodec
import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.TranslationResultCard

/** Photo entry point: new images use glyph recognition, not Latin-script OCR. */
@Composable
fun ImageTranslationScreen(
    viewModel: TranslationViewModel,
    onSaveEntry: (DictionaryEntry) -> Unit,
    replay: TranslationReplay? = null,
    onRecordGlyphScan: (GlyphScanCapture, (TranslationRecord?) -> Unit) -> Unit = { _, _ -> },
    onToggleGlyphFavorite: (TranslationRecord) -> Unit = {},
    savedGlyphIds: Set<String> = emptySet(),
    onOpenHistory: () -> Unit = {},
    onOpenFavourites: () -> Unit = {},
) {
    var legacy by remember(replay?.requestId) { mutableStateOf(replay != null) }
    if (legacy && replay != null) {
        LegacyPhotoReplay(viewModel, onSaveEntry, replay, onScanGlyph = { legacy = false })
        return
    }
    GlyphPhotoScreen(
        onRecordGlyphScan = onRecordGlyphScan,
        onToggleGlyphFavorite = onToggleGlyphFavorite,
        savedGlyphIds = savedGlyphIds,
        onOpenHistory = onOpenHistory,
        onOpenFavourites = onOpenFavourites,
    )
}

@Composable
internal fun GlyphPhotoScreen(
    onRecordGlyphScan: (GlyphScanCapture, (TranslationRecord?) -> Unit) -> Unit,
    onToggleGlyphFavorite: (TranslationRecord) -> Unit,
    savedGlyphIds: Set<String>,
    onOpenHistory: () -> Unit,
    onOpenFavourites: () -> Unit,
    glyphModel: GlyphRecognitionViewModel = viewModel(key = "glyph-photo-v1"),
) {
    val context=LocalContext.current
    val state by glyphModel.state.collectAsStateWithLifecycle()
    val mode=state.activeMode
    var savedScan by remember { mutableStateOf<TranslationRecord?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val hasCamera=remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val scroll=rememberScrollState()
    DisposableEffect(glyphModel) { onDispose { glyphModel.clear() } }
    LaunchedEffect(state.bitmap,state.resultRevision) { savedScan=null;saveError=null }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) { permissionMessage=null;glyphModel.load(uri) }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted)showCamera=true else permissionMessage="Camera permission was denied. You can still choose a photo."
    }
    BackHandler(enabled=showCamera) { showCamera=false }
    if(showCamera) {
        CameraCaptureScreen(onCancel={showCamera=false},
            onImageCaptured={ uri -> showCamera=false;glyphModel.load(uri,ownedCapture=true) },
            onError={ message -> showCamera=false;permissionMessage=message })
        return
    }
    val completed=!state.busy && state.bitmap!=null &&
        ((mode=="single" && state.referenceMatches!=null) || (mode=="multiple" && state.multiple.isNotEmpty()))
    LaunchedEffect(completed,state.resultRevision) {
        if(!completed || glyphModel.recordedResultRevision==state.resultRevision)return@LaunchedEffect
        val image=state.bitmap ?: return@LaunchedEffect
        val request=state.resultRevision
        try {
            val capture=withContext(Dispatchers.Default) {
                val candidates=if(mode=="multiple")state.multiple.flatMap { region ->
                    region.englishMatches.take(1).map { it to region.number }
                } else state.referenceMatches.orEmpty().map { it to 0 }
                GlyphScanCapture(previewBase64=GlyphScanPreviewCodec.encode(image,GlyphCrop()),
                    candidates=candidates.take(10).map { (match,number) -> GlyphScanCandidate(
                        twkmSign=match.reference.twkmSign,reading=match.reference.reading,
                        englishGloss=match.reference.english,visualOverlap=match.visualOverlap.toDouble(),
                        scholarlyLevel=match.reference.scholarlyLevel,regionIndex=number) },
                    scanKind=if(mode=="multiple")"BLOCKS" else "WORD_SIGN",
                    regionCount=if(mode=="multiple")state.multiple.size else 1)
            }
            if(request==glyphModel.state.value.resultRevision && !glyphModel.state.value.busy) {
                glyphModel.recordedResultRevision=request
                onRecordGlyphScan(capture) { record ->
                    if(request==glyphModel.state.value.resultRevision && !glyphModel.state.value.busy) {
                        savedScan=record
                        if(record==null)saveError="The result is ready, but it could not be saved to history."
                    }
                }
            }
        } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(_: Exception) { if(request==glyphModel.state.value.resultRevision && !glyphModel.state.value.busy)saveError="The photo preview could not be saved to history." }
    }
    AppGradientBackground {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("THE GLYPH STUDIO",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            Text("Discover a glyph",style=MaterialTheme.typography.headlineLarge)
            Text(if(state.bitmap==null)"Choose a photo to begin." else "Choose single or multiple glyphs to compare your photo.",
                style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Card(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.extraLarge,
                colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    state.bitmap?.let { image ->
                        if(mode=="multiple")GlyphRegionOverlay(image,state.multiple)
                        else Image(image.asImageBitmap(),contentDescription="Selected glyph photo",
                            modifier=Modifier.fillMaxWidth().height(240.dp),contentScale=ContentScale.Fit)
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Button(onClick={gallery.launch("image/*")},enabled=!state.busy,
                            modifier=Modifier.weight(1f).heightIn(min=52.dp)) {
                            Icon(Icons.Rounded.PhotoLibrary,contentDescription=null,modifier=Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp));Text(if(state.bitmap==null)"Gallery" else "Change")
                        }
                        OutlinedButton(onClick={
                            if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
                                showCamera=true else permission.launch(Manifest.permission.CAMERA)
                        },enabled=hasCamera && !state.busy,modifier=Modifier.weight(1f).heightIn(min=52.dp)) {
                            Icon(Icons.Rounded.CameraAlt,contentDescription=null,modifier=Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp));Text("Camera")
                        }
                    }
                    if(state.bitmap!=null) {
                        Text("HOW MANY GLYPHS?",style=MaterialTheme.typography.labelMedium,
                            color=MaterialTheme.colorScheme.primary)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick={savedScan=null;saveError=null;glyphModel.compareReference()},
                                enabled=!state.busy,modifier=Modifier.weight(1f).heightIn(min=56.dp),
                                colors=ButtonDefaults.filledTonalButtonColors(
                                    containerColor=if(mode=="single")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor=if(mode=="single")MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)) { Text("Single glyph") }
                            FilledTonalButton(onClick={savedScan=null;saveError=null;glyphModel.scanAll()},
                                enabled=!state.busy,modifier=Modifier.weight(1f).heightIn(min=56.dp),
                                colors=ButtonDefaults.filledTonalButtonColors(
                                    containerColor=if(mode=="multiple")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor=if(mode=="multiple")MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)) { Text("Multiple glyphs") }
                        }
                    }
                }
            }
            if(state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.multipleProgress ?: "Comparing your photo…",style=MaterialTheme.typography.bodyMedium)
            }
            (permissionMessage ?: state.message)?.let {
                Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.large) {
                    Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if(!state.busy && mode=="single")state.referenceMatches?.let { GlyphReferenceResults(it) }
            if(!state.busy && mode=="multiple" && state.multiple.isNotEmpty())state.bitmap?.let {
                MultiGlyphResultsPanel(bitmap=it,results=state.multiple,showOverview=false)
            }
            saveError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            savedScan?.let { record ->
                val favorite=GlyphScanRecords.favoriteId(record.userId,record.translationId) in savedGlyphIds
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Saved to history",style=MaterialTheme.typography.titleMedium)
                        TextButton(onClick={onToggleGlyphFavorite(record)}) { Text(if(favorite)"Remove favourite" else "Add to favourites") }
                        Row {
                            TextButton(onClick=onOpenHistory) { Text("View history") }
                            TextButton(onClick=onOpenFavourites) { Text("View favourites") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
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
