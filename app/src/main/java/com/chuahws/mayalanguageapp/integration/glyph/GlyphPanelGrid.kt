package com.chuahws.mayalanguageapp.integration.glyph

/**
 * Fallback grid splitting when the strict automatic detector cannot infer
 * reliable block boundaries. The user selects the panel once and gives its
 * approximate row/column counts. This is NOT glyph recognition.
 */
internal object GlyphPanelGrid {
    fun regions(panel: GlyphCrop, rows: Int, columns: Int): List<GlyphDetectedRegion> {
        require(rows in 2..12 && columns in 2..12) {
            "Choose 2 to 12 rows and columns."
        }
        val cellWidth = (panel.right - panel.left) / columns
        val cellHeight = (panel.bottom - panel.top) / rows
        val paddingX = cellWidth * .01f
        val paddingY = cellHeight * .01f
        return buildList {
            for (row in 0 until rows) for (column in 0 until columns) {
                add(GlyphDetectedRegion(
                    crop = GlyphCrop(
                        panel.left + cellWidth * column + paddingX,
                        panel.top + cellHeight * row + paddingY,
                        panel.left + cellWidth * (column + 1) - paddingX,
                        panel.top + cellHeight * (row + 1) - paddingY,
                    ),
                    row = row + 1, column = column + 1,
                ))
            }
        }
    }

    /** Map a detected sub-region of a panel crop back to the source photo. */
    fun toOriginal(selection: GlyphCrop, local: GlyphDetectedRegion): GlyphDetectedRegion {
        val w = selection.right - selection.left
        val h = selection.bottom - selection.top
        return GlyphDetectedRegion(
            crop = GlyphCrop(
                selection.left + local.crop.left * w,
                selection.top + local.crop.top * h,
                selection.left + local.crop.right * w,
                selection.top + local.crop.bottom * h,
            ),
            row = local.row, column = local.column,
        )
    }
}
