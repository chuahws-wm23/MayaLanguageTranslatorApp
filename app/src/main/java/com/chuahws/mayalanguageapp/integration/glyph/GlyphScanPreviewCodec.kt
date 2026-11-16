package com.chuahws.mayalanguageapp.integration.glyph

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Tiny, bounded preview of the manually selected glyph, stored alongside the
 * user's History / Saved record. This is not the original photo and must not
 * be used as reliable training data. No image bytes are uploaded until the
 * user explicitly starts visual matching or saves a completed experimental scan.
 */
object GlyphScanPreviewCodec {
    private const val SIDE = 360
    private const val MAX_JPEG_BYTES = 110_000
    private const val MAX_BASE64_CHARS = 160_000

    fun encode(bitmap: Bitmap, crop: GlyphCrop): String {
        require(!bitmap.isRecycled)
        val (x,y,w,h) = crop.pixels(bitmap.width,bitmap.height)
        val selected = Bitmap.createBitmap(bitmap,x,y,w,h)
        try {
            val ratio = minOf(1f, SIDE.toFloat()/max(w,h))
            val width = max(1,(w*ratio).roundToInt())
            val height = max(1,(h*ratio).roundToInt())
            val small = if (width == w && height == h) selected
                else Bitmap.createScaledBitmap(selected,width,height,true)
            try {
                for (quality in listOf(78,65,52,38,24)) {
                    val output=ByteArrayOutputStream()
                    if (small.compress(Bitmap.CompressFormat.JPEG,quality,output)) {
                        val bytes=output.toByteArray()
                        if (bytes.size <= MAX_JPEG_BYTES) {
                            val value=Base64.encodeToString(bytes,Base64.NO_WRAP)
                            if (value.length <= MAX_BASE64_CHARS) return value
                        }
                    }
                }
                error("Could not fit glyph preview into a safe storage size")
            } finally {
                if(small !== selected) small.recycle()
            }
        } finally {
            if(selected !== bitmap) selected.recycle()
        }
    }

    /** Reject untrusted or damaged stored previews before allocating a bitmap. */
    fun decode(encoded: String): Bitmap? {
        if (encoded.isEmpty() || encoded.length > MAX_BASE64_CHARS) return null
        return runCatching {
            val bytes=Base64.decode(encoded,Base64.DEFAULT)
            require(bytes.size in 1..MAX_JPEG_BYTES)
            val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts)
            require(opts.outWidth in 1..SIDE && opts.outHeight in 1..SIDE)
            BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        }.getOrNull()
    }
}
