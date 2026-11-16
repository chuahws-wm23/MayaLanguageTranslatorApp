package com.chuahws.mayalanguageapp.integration.glyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Offline visual catalogue, NOT a glyph translation model.
 *
 * Contains only TWKM standardised drawings explicitly licensed CC BY 4.0,
 * with SOURCE-PROPOSED phonogram labels. It excludes user-supplied chart
 * illustrations whose redistribution rights were not established.
 *
 * Matching compares one manually cropped / segmented SIGN with 382 pictures.
 * Raw dot/cosine scores are appearance similarities, NEVER correctness odds.
 */
internal class GlyphVisualCatalogue private constructor(
    val references: List<GlyphVisualReference>,
    private val bitmapBits: ByteArray,
) {
    companion object {
        const val INDEX_ASSET = "maya_twkm_phonogram_index.json"
        const val IMAGE_ASSET = "maya_twkm_phonogram_bitmap.bin"
        const val SIDE = 64
        private const val BYTES_PER_REF = SIDE * SIDE / 8

        @Volatile private var cached: GlyphVisualCatalogue? = null

        fun fromAssetsOrNull(context: Context): GlyphVisualCatalogue? {
            val app = context.applicationContext
            val names = app.assets.list("")?.toSet().orEmpty()
            if (INDEX_ASSET !in names || IMAGE_ASSET !in names) return null
            return cached ?: synchronized(this) {
                cached ?: load(app).also { cached = it }
            }
        }

        private fun load(context: Context): GlyphVisualCatalogue {
            val index = JSONObject(context.assets.open(INDEX_ASSET).bufferedReader().use {
                it.readText()
            })
            require(index.getInt("format_version") == 1 && index.getInt("side") == SIDE)
            require(index.getInt("bytes_per_image") == BYTES_PER_REF)
            require(index.getString("drawing_license") == "CC BY 4.0")
            val rows = index.getJSONArray("entries")
            val count = index.getInt("count")
            require(count in 80..5000 && rows.length() == count) {
                "Unexpected offline catalogue size"
            }
            val images = context.assets.open(IMAGE_ASSET).use { it.readBytes() }
            require(images.size == count * BYTES_PER_REF) {
                "Mismatched offline image catalogue"
            }
            val references = List(rows.length()) { indexInFile ->
                val ref = rows.getJSONObject(indexInFile)
                val syllable = ref.getString("syllable")
                require(syllable.matches(Regex("[a-z']{1,5}")))
                GlyphVisualReference(
                    offset = indexInFile,
                    syllable = syllable,
                    twkmSign = ref.getInt("twkm_sign"),
                    graph = ref.getString("graph"),
                )
            }
            return GlyphVisualCatalogue(references, images)
        }

        /** Similarity of two binary bitplanes, NOT a probability. */
        internal fun binaryCosine(a: ByteArray,b: ByteArray): Float {
            require(a.size == BYTES_PER_REF && b.size == BYTES_PER_REF)
            var inkA = 0
            var inkB = 0
            var both = 0
            for (i in a.indices) {
                val av = a[i].toInt() and 255
                val bv = b[i].toInt() and 255
                inkA += Integer.bitCount(av)
                inkB += Integer.bitCount(bv)
                both += Integer.bitCount(av and bv)
            }
            if (inkA < 6 || inkB < 6) return 0f
            return (both / sqrt(inkA.toDouble()*inkB)).toFloat()
        }

        /** A close drawing is useful only when it is also distinct from the next reading. */
        internal fun hasClearVisualMatch(best: Float, next: Float): Boolean =
            best >= .60f && best - next >= .10f

        /** Select ink before resizing so a dark frame never becomes the sign. */
        internal fun foregroundMask(gray: IntArray, width: Int, height: Int): BooleanArray? {
            require(width > 0 && height > 0 && gray.size == width * height)
            val border = ArrayList<Int>(2 * width + 2 * height)
            val hist = IntArray(256)
            for (y in 0 until height) for (x in 0 until width) {
                val value = gray[y * width + x]
                require(value in 0..255)
                hist[value]++
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    border.add(value)
                }
            }
            border.sort()
            val darkBorder = border[border.size / 2] < 110
            val total = gray.size.toDouble()
            val weighted = hist.indices.sumOf { it.toDouble() * hist[it] }
            var count = 0.0
            var sum = 0.0
            var threshold = 128
            var greatest = -1.0
            for (t in 0..254) {
                count += hist[t].toDouble()
                sum += t * hist[t].toDouble()
                if (count <= 0.0 || count >= total) continue
                val a = sum / count
                val b = (weighted - sum) / (total - count)
                val score = count * (total - count) * (a - b) * (a - b)
                if (score > greatest) {
                    greatest = score
                    threshold = t
                }
            }
            val lightFraction = gray.count { it > threshold }.toFloat() / gray.size
            val darkInkInsideLightFigure = darkBorder && lightFraction > .55f
            val outside = BooleanArray(gray.size)
            if (darkInkInsideLightFigure) {
                // On a white filled figure, dark interior lines are the drawing.
                // Flood from all four edges to discard the black surrounding field.
                val queue = IntArray(gray.size)
                var tail = 0
                fun enqueue(index: Int) {
                    if (!outside[index] && gray[index] <= threshold) {
                        outside[index] = true
                        queue[tail++] = index
                    }
                }
                for (x in 0 until width) {
                    enqueue(x)
                    enqueue((height - 1) * width + x)
                }
                for (y in 0 until height) {
                    enqueue(y * width)
                    enqueue(y * width + width - 1)
                }
                var head = 0
                while (head < tail) {
                    val index = queue[head++]
                    val x = index % width
                    if (x > 0) enqueue(index - 1)
                    if (x + 1 < width) enqueue(index + 1)
                    if (index >= width) enqueue(index - width)
                    if (index + width < gray.size) enqueue(index + width)
                }
            }
            val ink = BooleanArray(gray.size) { index ->
                when {
                    darkInkInsideLightFigure -> gray[index] <= threshold && !outside[index]
                    darkBorder -> gray[index] > threshold
                    else -> gray[index] <= threshold
                }
            }
            return ink.takeIf { mask -> mask.count { it }.toFloat() / mask.size in .003f.. .85f }
        }
    }

    /** Distinct phonogram values represented by at least one licensed reference. */
    val syllables: List<String> = references.map { it.syllable }.distinct().sorted()

    fun forSyllable(value: String): List<GlyphVisualReference> =
        references.filter { it.syllable == value }

    /** White background with dark graph strokes, generated from licensed binary artwork. */
    fun referenceBitmap(reference: GlyphVisualReference): Bitmap {
        val start = reference.offset * BYTES_PER_REF
        require(start in 0..bitmapBits.size-BYTES_PER_REF)
        val pixels = IntArray(SIDE*SIDE)
        for (i in pixels.indices) {
            val v = bitmapBits[start+(i ushr 3)].toInt() and 255
            pixels[i] = if ((v and (0x80 ushr (i and 7))) != 0) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels,SIDE,SIDE,Bitmap.Config.ARGB_8888)
    }

    /**
     * Searches the SAME licensed gallery, returning at most three DIFFERENT
     * proposed syllable values. Matching is heuristic, uncalibrated.
     */
    fun search(image: Bitmap, number: Int = 3): List<GlyphVisualMatch> {
        require(number in 1..10)
        val query = canonicalBitplane(image) ?: return emptyList()
        val best = HashMap<String,GlyphVisualMatch>()
        for (reference in references) {
            val off = reference.offset * BYTES_PER_REF
            var nQ = 0
            var nR = 0
            var intersection = 0
            for (i in query.indices) {
                val a = query[i].toInt() and 255
                val b = bitmapBits[off+i].toInt() and 255
                nQ += Integer.bitCount(a)
                nR += Integer.bitCount(b)
                intersection += Integer.bitCount(a and b)
            }
            val score = if (nQ >= 6 && nR >= 6)
                (intersection / sqrt(nQ.toDouble()*nR)).toFloat() else 0f
            val old = best[reference.syllable]
            if (old == null || score > old.visualSimilarity) {
                best[reference.syllable] = GlyphVisualMatch(reference,score)
            }
        }
        val ranked = best.values.sortedWith(
            compareByDescending<GlyphVisualMatch> { it.visualSimilarity }
                .thenBy { it.reference.syllable }
        )
        if (ranked.isEmpty() || !hasClearVisualMatch(
                ranked[0].visualSimilarity, ranked.getOrNull(1)?.visualSimilarity ?: 0f,
            )) return emptyList()
        return ranked.take(number)
    }

    /**
     * Converts a single cropped photo to a centered 64x64 foreground bitplane.
     * A whole block/image with multiple glyphs is NOT equivalent to one sign.
     * This is a simple photograph baseline, not calibrated shadow correction.
     */
    internal fun canonicalBitplane(bitmap: Bitmap): ByteArray? {
        if (bitmap.width < 20 || bitmap.height < 20) return null
        val scale = min(1f, 1024f / max(bitmap.width,bitmap.height))
        val w = max(1,(bitmap.width*scale).roundToInt())
        val h = max(1,(bitmap.height*scale).roundToInt())
        val resized = if (w != bitmap.width || h != bitmap.height)
            Bitmap.createScaledBitmap(bitmap,w,h,true) else bitmap
        try {
            val argb = IntArray(w*h)
            resized.getPixels(argb,0,w,0,0,w,h)
            val gray = IntArray(w*h)
            for(y in 0 until h)for(x in 0 until w){
                val p=argb[y*w+x]
                val a=Color.alpha(p)
                val lum=(Color.red(p)*77+Color.green(p)*150+Color.blue(p)*29+128) shr 8
                val value = (lum*a+255*(255-a)+127)/255
                gray[y*w+x]=value
            }
            val foreground = foregroundMask(gray, w, h) ?: return null
            var minX=w;var minY=h;var maxX=-1;var maxY=-1
            for(y in 0 until h)for(x in 0 until w){
                if(foreground[y*w+x]){
                    minX=min(minX,x);maxX=max(maxX,x)
                    minY=min(minY,y);maxY=max(maxY,y)
                }
            }
            val cw=maxX-minX+1
            val ch=maxY-minY+1
            val blackOnWhite = IntArray(cw*ch) { index ->
                val x=index%cw + minX
                val y=index/cw + minY
                if(foreground[y*w+x]) Color.WHITE else Color.BLACK
            }
            val cropped = Bitmap.createBitmap(blackOnWhite,cw,ch,Bitmap.Config.ARGB_8888)
            try {
                val factor=56f/max(cw,ch)
                val sw=max(1,(cw*factor).roundToInt())
                val sh=max(1,(ch*factor).roundToInt())
                val scaled = Bitmap.createScaledBitmap(cropped,sw,sh,true)
                try {
                    val px=IntArray(sw*sh)
                    scaled.getPixels(px,0,sw,0,0,sw,sh)
                    val packed=ByteArray(BYTES_PER_REF)
                    val dx=(SIDE-sw)/2
                    val dy=(SIDE-sh)/2
                    var totalInk=0
                    for(y in 0 until sh)for(x in 0 until sw){
                        // scaled image uses white foreground on black background.
                        if(Color.red(px[y*sw+x])>127){
                            val bit=(dy+y)*SIDE+dx+x
                            packed[bit ushr 3]=(packed[bit ushr 3].toInt() or
                                (0x80 ushr (bit and 7))).toByte()
                            totalInk++
                        }
                    }
                    return if(totalInk>=6 && totalInk <= SIDE*SIDE*.42f)packed else null
                }finally{if(scaled !== cropped)scaled.recycle()}
            }finally{cropped.recycle()}
        }finally{if(resized !== bitmap)resized.recycle()}
    }
}

/** A phonogram's reading is a sound, NOT an English translation. */
internal data class GlyphVisualReference(
    val offset: Int,
    val syllable: String,
    val twkmSign: Int,
    val graph: String,
)

internal data class GlyphVisualMatch(
    val reference: GlyphVisualReference,
    /** Cosine of centered binary foreground shapes. NOT calibrated confidence. */
    val visualSimilarity: Float,
)
