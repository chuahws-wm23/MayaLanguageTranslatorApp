package com.chuahws.mayalanguageapp.integration.ml_kit_ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MlKitOcrService {
    private val recognizer =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

    suspend fun readText(
        context: Context,
        imageUri: Uri,
    ): String {
        val bitmap = withContext(
            Dispatchers.IO
        ) {
            prepareBitmap(
                context = context,
                imageUri = imageUri,
            )
        }

        return try {
            val image =
                InputImage.fromBitmap(
                    bitmap,
                    0,
                )

            recognizer.process(image)
                .await()
                .text
                .trim()
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun prepareBitmap(
        context: Context,
        imageUri: Uri,
    ): Bitmap {
        val resolver =
            context.contentResolver

        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        resolver.openInputStream(imageUri)
            ?.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    bounds,
                )
            }
            ?: throw IllegalArgumentException(
                "Could not open the selected image."
            )

        if (
            bounds.outWidth <= 0 ||
            bounds.outHeight <= 0
        ) {
            throw IllegalArgumentException(
                "This image format could not be read."
            )
        }

        if (
            bounds.outWidth <
                MIN_IMAGE_DIMENSION ||
            bounds.outHeight <
                MIN_IMAGE_DIMENSION
        ) {
            throw IllegalArgumentException(
                "This image is too small. Choose a clearer image."
            )
        }

        var sampleSize = 1
        while (
            bounds.outWidth /
                sampleSize >
                MAX_DECODE_DIMENSION * 2 ||
            bounds.outHeight /
                sampleSize >
                MAX_DECODE_DIMENSION * 2
        ) {
            sampleSize *= 2
        }

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig =
                    Bitmap.Config.ARGB_8888
            }

        val decoded =
            resolver.openInputStream(imageUri)
                ?.use {
                    BitmapFactory.decodeStream(
                        it,
                        null,
                        options,
                    )
                }
                ?: throw IllegalArgumentException(
                    "Could not read the selected image."
                )

        val resized =
            resizeIfNeeded(decoded)

        if (
            resized !== decoded &&
            !decoded.isRecycled
        ) {
            decoded.recycle()
        }

        val rotation =
            readExifRotation(
                context = context,
                imageUri = imageUri,
            )

        if (rotation == 0f) {
            return resized
        }

        val matrix =
            Matrix().apply {
                postRotate(rotation)
            }

        val rotated =
            Bitmap.createBitmap(
                resized,
                0,
                0,
                resized.width,
                resized.height,
                matrix,
                true,
            )

        if (
            rotated !== resized &&
            !resized.isRecycled
        ) {
            resized.recycle()
        }

        return rotated
    }

    private fun resizeIfNeeded(
        bitmap: Bitmap,
    ): Bitmap {
        val largest =
            maxOf(
                bitmap.width,
                bitmap.height,
            )

        if (
            largest <=
                MAX_DECODE_DIMENSION
        ) {
            return bitmap
        }

        val scale =
            MAX_DECODE_DIMENSION
                .toFloat() /
                largest.toFloat()

        val width =
            (bitmap.width * scale)
                .roundToInt()
                .coerceAtLeast(1)
        val height =
            (bitmap.height * scale)
                .roundToInt()
                .coerceAtLeast(1)

        return Bitmap.createScaledBitmap(
            bitmap,
            width,
            height,
            true,
        )
    }

    private fun readExifRotation(
        context: Context,
        imageUri: Uri,
    ): Float =
        runCatching {
            context.contentResolver
                .openInputStream(imageUri)
                ?.use { stream ->
                    when (
                        ExifInterface(stream)
                            .getAttributeInt(
                                ExifInterface
                                    .TAG_ORIENTATION,
                                ExifInterface
                                    .ORIENTATION_NORMAL,
                            )
                    ) {
                        ExifInterface
                            .ORIENTATION_ROTATE_90,
                        ExifInterface
                            .ORIENTATION_TRANSPOSE ->
                            90f

                        ExifInterface
                            .ORIENTATION_ROTATE_180,
                        ExifInterface
                            .ORIENTATION_FLIP_VERTICAL ->
                            180f

                        ExifInterface
                            .ORIENTATION_ROTATE_270,
                        ExifInterface
                            .ORIENTATION_TRANSVERSE ->
                            270f

                        else -> 0f
                    }
                } ?: 0f
        }.getOrDefault(0f)

    private companion object {
        const val MAX_DECODE_DIMENSION =
            2_048
        const val MIN_IMAGE_DIMENSION =
            32
    }
}
