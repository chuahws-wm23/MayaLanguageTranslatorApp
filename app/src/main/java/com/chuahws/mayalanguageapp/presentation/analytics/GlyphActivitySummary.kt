package com.chuahws.mayalanguageapp.presentation.analytics

import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import java.util.Calendar
import java.util.TimeZone

internal data class GlyphActivityDay(val startMillis: Long, val count: Int)
internal data class GlyphActivitySummary(
    val scans: List<TranslationRecord>,
    val days: List<GlyphActivityDay>,
) {
    val weeklyScans get() = days.sumOf { it.count }
    val activeDays get() = days.count { it.count > 0 }
    val multiScans get() = scans.count { it.glyphRegionCount > 1 }
}

/** Calendar boundaries use the device timezone, including daylight-saving transitions. */
internal fun glyphActivitySummary(
    history: List<TranslationRecord>,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): GlyphActivitySummary {
    val scans = history.filter { it.glyphScanRecord && it.createdAtMillis in 1..nowMillis }
        .sortedByDescending { it.createdAtMillis }
    val today = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val days = (6 downTo 0).map { ago ->
        val start = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -ago) }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        GlyphActivityDay(start.timeInMillis, scans.count { it.createdAtMillis >= start.timeInMillis && it.createdAtMillis < end })
    }
    return GlyphActivitySummary(scans, days)
}
