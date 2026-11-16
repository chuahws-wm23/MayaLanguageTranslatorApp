package com.chuahws.mayalanguageapp.integration.glyph

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/**
 * Conservative XY-cut layout proposals for separated drawings on a plain background.
 * Detects whitespace, NOT Maya signs. Connected/overlapping carvings need the trained
 * detector or manual crop review; an individual sign can itself have separate parts.
 */
internal object GlyphWhitespaceDetector {
    private data class Box(val l: Int, val t: Int, val r: Int, val b: Int, val ink: Int) {
        val width get() = r - l
        val height get() = b - t
    }

    fun detect(image: Bitmap): List<GlyphDetectedRegion> {
        val scale = minOf(1f, 960f / maxOf(image.width, image.height))
        val copy = if (scale < 1f) Bitmap.createScaledBitmap(image,
            maxOf(1, (image.width * scale).roundToInt()),
            maxOf(1, (image.height * scale).roundToInt()), true) else image
        return try {
            val pixels = IntArray(copy.width * copy.height)
            copy.getPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
            val gray = IntArray(pixels.size) { i ->
                val p = pixels[i]
                val lum = (Color.red(p)*77 + Color.green(p)*150 + Color.blue(p)*29 + 128) shr 8
                (lum*Color.alpha(p) + 255*(255-Color.alpha(p)) + 127)/255
            }
            detect(gray, copy.width, copy.height)
        } finally { if (copy !== image) copy.recycle() }
    }

    fun detect(gray: IntArray, width: Int, height: Int): List<GlyphDetectedRegion> {
        require(width > 0 && height > 0 && gray.size == width*height && gray.all { it in 0..255 })
        if (width < 50 || height < 50) return emptyList()
        // A plain-background detector must not turn stone texture into a grid.
        val border = gray.indices.filter { it < width || it >= (height-1)*width || it%width == 0 || it%width == width-1 }
            .map { gray[it] }.sorted()
        if (border[border.size/2] < 180) return emptyList()
        val mask = GlyphVisualCatalogue.foregroundMask(gray, width, height) ?: return emptyList()
        fun trim(l: Int, t: Int, r: Int, b: Int): Box? {
            var left=r; var top=b; var right=l; var bottom=t; var ink=0
            for (y in t until b) for (x in l until r) if (mask[y*width+x]) {
                left=minOf(left,x); top=minOf(top,y); right=maxOf(right,x+1); bottom=maxOf(bottom,y+1); ink++
            }
            return if (ink >= 20 && right-left >= 20 && bottom-top >= 20) Box(left,top,right,bottom,ink) else null
        }
        val root=trim(0,0,width,height) ?: return emptyList()
        val leaves=mutableListOf<Box>()
        var overflow=false
        fun split(box: Box, depth: Int) {
            if (overflow) return
            if (leaves.size >= 48) { overflow=true; return }
            if (depth >= 12) { leaves.add(box); return }
            data class Cut(val first: Box, val second: Box, val strength: Float)
            var best: Cut?=null
            for (horizontal in listOf(true,false)) {
                val length=if(horizontal) box.height else box.width
                val profile=IntArray(length)
                for(y in box.t until box.b)for(x in box.l until box.r)if(mask[y*width+x])
                    profile[if(horizontal)y-box.t else x-box.l]++
                var start=0
                while(start<length) {
                    if(profile[start]!=0) { start++; continue }
                    var end=start+1
                    while(end<length && profile[end]==0)end++
                    val gap=end-start
                    // Ignore narrow holes and very small disconnected ornaments.
                    // Compare spatial extent, not ink mass: a valid thin-stroked
                    // sign must not disappear beside a dense drawing.
                    if(start>0 && end<length && gap>=maxOf(12,(length*.08f).roundToInt())) {
                        val mid=(start+end)/2
                        val first=if(horizontal)trim(box.l,box.t,box.r,box.t+mid) else trim(box.l,box.t,box.l+mid,box.b)
                        val second=if(horizontal)trim(box.l,box.t+mid,box.r,box.b) else trim(box.l+mid,box.t,box.r,box.b)
                        if(first!=null && second!=null &&
                            minOf(first.width*first.height,second.width*second.height).toFloat()/
                                (first.width*first.height+second.width*second.height) >= .18f) {
                            val strength=gap.toFloat()/length
                            if(best==null || strength>best!!.strength)best=Cut(first,second,strength)
                        }
                    }
                    start=end
                }
            }
            val cut=best
            if(cut==null)leaves.add(box) else { split(cut.first,depth+1); split(cut.second,depth+1) }
        }
        split(root,0)
        if(overflow || leaves.size !in 2..48)return emptyList()
        // Group vertically overlapping drawings into geometric rows. This is NOT
        // an epigraphic reading order (Maya inscriptions may use paired columns).
        val rows=mutableListOf<MutableList<Box>>()
        for(box in leaves.sortedWith(compareBy<Box>{it.t}.thenBy{it.l})) {
            val row=rows.lastOrNull()
            if(row!=null && box.t < row.maxOf{it.b} && box.b > row.minOf{it.t})row.add(box)
            else rows.add(mutableListOf(box))
        }
        return rows.flatMapIndexed { row, boxes ->
            boxes.sortedBy{it.l}.mapIndexed { column, box ->
                val pad=2
                GlyphDetectedRegion(GlyphCrop(
                    maxOf(0,box.l-pad).toFloat()/width, maxOf(0,box.t-pad).toFloat()/height,
                    minOf(width,box.r+pad).toFloat()/width, minOf(height,box.b+pad).toFloat()/height), row+1,column+1)
            }
        }
    }
}
