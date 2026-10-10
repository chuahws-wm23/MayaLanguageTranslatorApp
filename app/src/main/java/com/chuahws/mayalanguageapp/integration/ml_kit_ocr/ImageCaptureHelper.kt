package com.chuahws.mayalanguageapp.integration.ml_kit_ocr

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun createTemporaryImageUri(context: Context): Uri {
    val directory = File(context.cacheDir, "images").apply {
        mkdirs()
    }

    val file = File.createTempFile(
        "maya_scan_",
        ".jpg",
        directory,
    )

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}
