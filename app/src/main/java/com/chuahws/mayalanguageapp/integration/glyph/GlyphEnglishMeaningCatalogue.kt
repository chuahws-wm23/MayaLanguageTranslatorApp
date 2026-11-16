package com.chuahws.mayalanguageapp.integration.glyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * SOURCE-LINKED VISUAL SEARCH of English-glossed Classic Maya logograms.
 *
 * Not a trained classifier. The English readings come from the pinned TWKM
 * source catalogue, limited to scholarly confidence levels 1..3 and signs
 * with at least two explicitly CC BY 4.0 standardized graph drawings.
 * Visual shape similarity has NOT been calibrated on independent photos.
 */
internal class GlyphEnglishMeaningCatalogue private constructor(
    val references: List<GlyphEnglishReference>,
    private val bits: ByteArray,
) {
    companion object {
        const val INDEX_ASSET = "maya_twkm_logogram_index.json"
        const val IMAGE_ASSET = "maya_twkm_logogram_bitmap.bin"
        private const val SIDE = 64
        private const val REF_BYTES = SIDE*SIDE/8
        @Volatile private var cached: GlyphEnglishMeaningCatalogue? = null

        fun fromAssetsOrNull(context: Context): GlyphEnglishMeaningCatalogue? {
            val app = context.applicationContext
            val available = app.assets.list("")?.toSet().orEmpty()
            if(INDEX_ASSET !in available || IMAGE_ASSET !in available) return null
            return cached ?: synchronized(this) {
                cached ?: load(app).also { cached=it }
            }
        }

        private fun load(context: Context): GlyphEnglishMeaningCatalogue {
            val index=JSONObject(context.assets.open(INDEX_ASSET)
                .bufferedReader().use { it.readText() })
            require(index.getInt("format_version")==1)
            require(index.getInt("side")==SIDE && index.getInt("bytes_per_image")==REF_BYTES)
            require(index.getString("drawing_license")=="CC BY 4.0")
            val n=index.getInt("count")
            val rows=index.getJSONArray("entries")
            require(n in 100..5000 && rows.length()==n)
            val imageBits=context.assets.open(IMAGE_ASSET).use { it.readBytes() }
            require(imageBits.size == n*REF_BYTES)
            val references=List(n) { indexInData ->
                val r=rows.getJSONObject(indexInData)
                val scholarlyLevel=r.getInt("level")
                val sign=r.getInt("sign")
                val gloss=r.getString("english")
                require(scholarlyLevel in 1..3 && sign>0 && gloss.isNotBlank())
                GlyphEnglishReference(
                    offset=indexInData,
                    twkmSign=sign,
                    reading=r.getString("reading"),
                    english=gloss,
                    kind=r.getString("category"),
                    scholarlyLevel=scholarlyLevel,
                    graph=r.getString("graph"),
                )
            }
            require(references.map { it.twkmSign }.toSet().size==
                index.getInt("meaning_sign_count"))
            return GlyphEnglishMeaningCatalogue(references,imageBits)
        }

        /**
         * Uncalibrated cosine of binary shape masks, in [0..1].
         * Does not provide a percentage probability of correct translation.
         */
        internal fun overlap(left: ByteArray, right: ByteArray): Float =
            GlyphVisualCatalogue.binaryCosine(left,right)
    }

    /** Entries for browsing, one representative drawing per sign identity. */
    val distinctSigns: List<GlyphEnglishReference> =
        references.distinctBy { it.twkmSign }.sortedBy { it.english }

    fun imageFor(reference: GlyphEnglishReference): Bitmap {
        val start = reference.offset*REF_BYTES
        require(start>=0 && start+REF_BYTES<=bits.size)
        val pixels=IntArray(SIDE*SIDE)
        for(i in pixels.indices) {
            val mask=bits[start+(i ushr 3)].toInt() and 255
            pixels[i]=if((mask and (0x80 ushr (i and 7)))!=0) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels,SIDE,SIDE,Bitmap.Config.ARGB_8888)
    }

    fun search(
        singleSignCrop: Bitmap,
        phonograms: GlyphVisualCatalogue,
        top: Int = 3,
    ): List<GlyphEnglishMatch> {
        require(top in 1..10)
        val query=phonograms.canonicalBitplane(singleSignCrop) ?: return emptyList()
        val bySign=HashMap<Int,GlyphEnglishMatch>()
        for(ref in references) {
            val at=ref.offset*REF_BYTES
            var qInk=0;var rInk=0;var both=0
            for(i in query.indices) {
                val a=query[i].toInt() and 255
                val b=bits[at+i].toInt() and 255
                qInk+=Integer.bitCount(a)
                rInk+=Integer.bitCount(b)
                both+=Integer.bitCount(a and b)
            }
            val sim=if(qInk>=6 && rInk>=6)
                (both/sqrt(qInk.toDouble()*rInk)).toFloat() else 0f
            val current=bySign[ref.twkmSign]
            if(current==null || current.visualOverlap<sim)
                bySign[ref.twkmSign]=GlyphEnglishMatch(ref,sim)
        }
        return bySign.values.sortedWith(
            compareByDescending<GlyphEnglishMatch> { it.visualOverlap }
                .thenBy { it.reference.twkmSign }
        ).take(top)
    }
}

/** Glosses are SOURCE-PROPOSED, not proved by the user's photograph. */
internal data class GlyphEnglishReference(
    val offset: Int,
    val twkmSign: Int,
    val reading: String,
    val english: String,
    val kind: String,
    /** TWKM reading-evidence scale 1=most established; 8=most speculative. */
    val scholarlyLevel: Int,
    val graph: String,
)

internal data class GlyphEnglishMatch(
    val reference: GlyphEnglishReference,
    /** Visual cosine resemblance; NOT historical or model confidence. */
    val visualOverlap: Float,
)
