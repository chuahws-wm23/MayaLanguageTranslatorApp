package com.chuahws.mayalanguageapp.presentation.translation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.integration.ml_kit_ocr.MlKitOcrService
import com.chuahws.mayalanguageapp.integration.ml_kit_ocr.createTemporaryImageUri
import com.chuahws.mayalanguageapp.presentation.components.TranslationResultCard
import kotlinx.coroutines.launch

@Composable
fun ImageTranslationScreen(
    viewModel: TranslationViewModel,
    onSaveEntry: (DictionaryEntry) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val ocrService = remember { MlKitOcrService() }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var readingImage by remember { mutableStateOf(false) }
    var imageMessage by remember { mutableStateOf<String?>(null) }

    fun readImage(uri: Uri) {
        imageUri = uri
        readingImage = true
        imageMessage = null

        scope.launch {
            runCatching {
                ocrService.readText(context, uri)
            }.onSuccess { text ->
                readingImage = false
                if (text.isBlank()) {
                    imageMessage = "No text was found in this image."
                } else {
                    viewModel.updateInput(text)
                }
            }.onFailure {
                readingImage = false
                imageMessage = "Could not read this image."
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) readImage(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
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
                    ?.use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }

    val source = if (
        state.direction == TranslationDirection.MAYA_TO_ENGLISH
    ) "Yucatec Maya" else "English"

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Scan text",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Take a clear photo or choose one from your gallery.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    val uri = createTemporaryImageUri(context)
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                Text("Camera", modifier = Modifier.padding(start = 8.dp))
            }

            FilledTonalButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                Text("Gallery", modifier = Modifier.padding(start = 8.dp))
            }
        }

        preview?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected image",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f),
                contentScale = ContentScale.Crop,
            )
        }

        if (readingImage) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator()
                Text("Reading text…")
            }
        }

        imageMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$source → " +
                    if (source == "English") "Yucatec Maya" else "English",
                style = MaterialTheme.typography.titleMedium,
            )

            FilledTonalIconButton(onClick = viewModel::swapDirection) {
                Icon(
                    Icons.Rounded.SwapHoriz,
                    contentDescription = "Swap languages",
                )
            }
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::updateInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Detected text") },
            supportingText = {
                Text("Check the text before translating.")
            },
            minLines = 4,
        )

        Button(
            onClick = { viewModel.translate(InputType.IMAGE) },
            enabled = !state.loading && state.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Translate")
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
            onSave = state.matchedEntry?.let { entry ->
                { onSaveEntry(entry) }
            },
        )
    }
}
