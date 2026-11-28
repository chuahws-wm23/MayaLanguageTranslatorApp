package com.chuahws.mayalanguageapp.integration.glyph

/**
 * Compares the ink in two centered 64×64 drawings. Each direction must explain
 * the other drawing's strokes, so a matching fragment cannot explain a whole sign.
 */
internal object GlyphStrokeAgreement {
    private const val SIDE = 64
    private const val ROW_BYTES = SIDE / Byte.SIZE_BITS
    private const val TOLERANCE = 2

    fun compare(query: ByteArray, gallery: ByteArray, referenceOffset: Int): Float {
        require(query.size == SIDE * ROW_BYTES)
        require(referenceOffset in 0..(gallery.size - SIDE * ROW_BYTES))
        val scanned = rows(query, 0)
        val reference = rows(gallery, referenceOffset)
        val scannedInk = scanned.sumOf { it.countOneBits() }
        val referenceInk = reference.sumOf { it.countOneBits() }
        if (scannedInk == 0 || referenceInk == 0) return 0f

        val scannedNeighborhood = expand(scanned)
        val referenceNeighborhood = expand(reference)
        var best = 0f
        // Centering and resampling can move thinner strokes by a few pixels.
        for (dy in -TOLERANCE..TOLERANCE) for (dx in -TOLERANCE..TOLERANCE) {
            var explainedScan = 0
            var explainedReference = 0
            for (y in maxOf(0, dy) until minOf(SIDE, SIDE + dy)) {
                val scanRow = y - dy
                explainedScan += (shift(scanned[scanRow], dx) and referenceNeighborhood[y]).countOneBits()
                explainedReference += (reference[y] and shift(scannedNeighborhood[scanRow], dx)).countOneBits()
            }
            val agreement = minOf(explainedScan.toFloat() / scannedInk, explainedReference.toFloat() / referenceInk)
            best = maxOf(best, agreement)
        }
        return best
    }

    private fun rows(bytes: ByteArray, offset: Int) = LongArray(SIDE) { y ->
        var row = 0L
        for (x in 0 until ROW_BYTES) {
            row = (row shl Byte.SIZE_BITS) or (bytes[offset + y * ROW_BYTES + x].toLong() and 255L)
        }
        row
    }

    private fun expand(rows: LongArray): LongArray {
        val wide = LongArray(SIDE) { y ->
            rows[y] or (rows[y] shl 1) or (rows[y] ushr 1) or (rows[y] shl 2) or (rows[y] ushr 2)
        }
        return LongArray(SIDE) { y ->
            var neighborhood = 0L
            for (near in maxOf(0, y - TOLERANCE)..minOf(SIDE - 1, y + TOLERANCE)) {
                neighborhood = neighborhood or wide[near]
            }
            neighborhood
        }
    }

    private fun shift(row: Long, dx: Int): Long = if (dx < 0) row shl -dx else row ushr dx
}
