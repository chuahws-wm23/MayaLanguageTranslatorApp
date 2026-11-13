package com.chuahws.mayalanguageapp.integration.glyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.roundToInt

/** Reads an app capture or a granted gallery URI. Does not invoke text OCR. */
object GlyphImageLoader {
    fun load(context: Context, uri: Uri, ownedCapture: Boolean = false): Bitmap {
        val resolver = context.contentResolver
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Bounds-only decode returns null by design; validate the stream separately.
            (resolver.openInputStream(uri) ?: error("Could not open image")).use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            require(bounds.outWidth >= 25 && bounds.outHeight >= 25) { "Choose a larger, readable image." }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = (resolver.openInputStream(uri) ?: error("Could not open image")).use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("Unsupported or damaged image.")
            var current = decoded
            try {
                val longest = maxOf(current.width, current.height)
                if (longest > 2048) {
                    val scale = 2048f / longest
                    val small = Bitmap.createScaledBitmap(current,
                        (current.width * scale).roundToInt().coerceAtLeast(1),
                        (current.height * scale).roundToInt().coerceAtLeast(1), true)
                    if (small !== current) current.recycle()
                    current = small
                }
                val orientation = resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
                val matrix = Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> { setRotate(180f); postScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                        ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                    }
                }
                if (!matrix.isIdentity) {
                    val upright = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
                    if (upright !== current) current.recycle()
                    current = upright
                }
                return current
            } catch (e: Exception) {
                current.recycle()
                throw e
            }
        } finally {
            // Only a fresh capture created by this screen is disposable. Never delete gallery input.
            if (ownedCapture && uri.authority == "${context.packageName}.fileprovider") {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }
}
