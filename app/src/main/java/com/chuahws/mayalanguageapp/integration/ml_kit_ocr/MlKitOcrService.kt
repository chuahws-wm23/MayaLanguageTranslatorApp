package com.chuahws.mayalanguageapp.integration.ml_kit_ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class MlKitOcrService {
    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun readText(
        context: Context,
        imageUri: Uri,
    ): String {
        val image = InputImage.fromFilePath(context, imageUri)
        return recognizer.process(image).await().text.trim()
    }
}
