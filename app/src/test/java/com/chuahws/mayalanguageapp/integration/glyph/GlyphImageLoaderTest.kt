package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.assertEquals
import org.junit.Test

/** Keeps valid decoded glyph images usable even if EXIF metadata is unavailable. */
class GlyphImageLoaderTest {
    @Test
    fun missingExifStreamFallsBackToNormalOrientation() {
        assertEquals(1, GlyphImageLoader.safeExifOrientation {
            throw java.io.IOException("This gallery provider could not reopen the stream.")
        })
    }

    @Test
    fun invalidExifDataFallsBackToNormalOrientation() {
        assertEquals(1, GlyphImageLoader.safeExifOrientation {
            throw IllegalArgumentException("Malformed metadata")
        })
    }

    @Test
    fun validExifOrientationIsPreserved() {
        assertEquals(6, GlyphImageLoader.safeExifOrientation { 6 })
        assertEquals(8, GlyphImageLoader.safeExifOrientation { 8 })
    }
}
