package com.chuahws.mayalanguageapp.integration.ml_kit_ocr

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun createTemporaryImageFile(context: Context): File {
    val directory = File(context.cacheDir, "images").apply {
        mkdirs()
    }

    return File.createTempFile(
        "maya_scan_",
        ".jpg",
        directory,
    )
}

fun temporaryImageUri(
    context: Context,
    file: File,
): Uri =
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

fun createTemporaryImageUri(context: Context): Uri =
    temporaryImageUri(
        context = context,
        file = createTemporaryImageFile(context),
    )
