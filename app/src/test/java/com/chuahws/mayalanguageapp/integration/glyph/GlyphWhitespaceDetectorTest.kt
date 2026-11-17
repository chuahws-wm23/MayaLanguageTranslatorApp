package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphWhitespaceDetectorTest {
    private fun panel(rows: Int, columns: Int): Triple<IntArray,Int,Int> {
        val w=columns*100+40;val h=rows*100+40
        val gray=IntArray(w*h){255}
        for(r in 0 until rows)for(c in 0 until columns)
            for(y in 30+r*100 until 90+r*100)for(x in 30+c*100 until 90+c*100)
                if(x%100<38 || x%100>81 || y%100<38 || y%100>81)gray[y*w+x]=0
        return Triple(gray,w,h)
    }
    @Test fun detectsTwoByTwoAndSingleRowWithoutRequiringDarkGridSeams() {
        for((rows,cols) in listOf(2 to 2,1 to 3,3 to 1)) {
            val(g,w,h)=panel(rows,cols); val found=GlyphWhitespaceDetector.detect(g,w,h)
            assertEquals(rows*cols,found.size)
            assertEquals(rows,found.maxOf{it.row});assertEquals(cols,found.maxOf{it.column})
        }
    }
    @Test fun blankAndSingleRingHaveNoInventedMultipleRegions() {
        assertTrue(GlyphWhitespaceDetector.detect(IntArray(200*200){255},200,200).isEmpty())
        val(g,w,h)=panel(1,1);assertTrue(GlyphWhitespaceDetector.detect(g,w,h).isEmpty())
    }
    @Test fun refusesDarkTexturedBackground() {
        val(g,w,h)=panel(2,2)
        for(i in g.indices)g[i]=if(g[i]==255)100 else 0
        assertTrue(GlyphWhitespaceDetector.detect(g,w,h).isEmpty())
    }
    @Test fun tinyDetachedMarkDoesNotBecomeAnotherGlyph() {
        val(g,w,h)=panel(1,1)
        for(y in 110..114)for(x in 30..34)g[y*w+x]=0
        assertTrue(GlyphWhitespaceDetector.detect(g,w,h).isEmpty())
    }
    @Test fun thinStrokedGlyphIsNotMergedIntoItsDenseNeighbour() {
        val w=240;val h=140;val gray=IntArray(w*h){255}
        for(c in 0..1)for(y in 30 until 90)for(x in 30+c*100 until 90+c*100) {
            val thickness=if(c==0)1 else 12
            if(x<30+c*100+thickness || x>=90+c*100-thickness || y<30+thickness || y>=90-thickness)
                gray[y*w+x]=0
        }
        assertEquals(2,GlyphWhitespaceDetector.detect(gray,w,h).size)
    }

    @Test fun lowDetailFragmentIsPreservedForReviewInsteadOfOversegmented() {
        val w=240;val h=140;val gray=IntArray(w*h){255}
        for(t in 0 until 20) {
            gray[(30+t)*w+30+t]=0
            gray[(66+t)*w+30+t]=0
        }
        for(y in 30 until 90)for(x in 130 until 190)
            if(x<138 || x>=182 || y<38 || y>=82)gray[y*w+x]=0
        assertEquals(2,GlyphWhitespaceDetector.detect(gray,w,h).size)
    }

}
