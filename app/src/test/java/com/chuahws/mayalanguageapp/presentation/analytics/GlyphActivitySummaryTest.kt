package com.chuahws.mayalanguageapp.presentation.analytics

import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GlyphActivitySummaryTest {
    private fun timestamp(zone: TimeZone, day: Int, hour: Int = 0): Long = Calendar.getInstance(zone).apply {
        clear(); set(2026, Calendar.MARCH, day, hour, 0)
    }.timeInMillis
    private fun scan(at: Long, id: String = "$at", regions: Int = 1) = TranslationRecord(
        translationId = id, glyphScanRecord = true, createdAtMillis = at, glyphRegionCount = regions)

    @Test fun countsOnlyRealGlyphHistoryAndUsesInclusiveStartExclusiveEnd() {
        val zone = TimeZone.getTimeZone("Asia/Kuala_Lumpur")
        val now = timestamp(zone, 12, 12)
        val start = timestamp(zone, 6)
        val history = listOf(scan(start - 1, "old"), scan(start, "boundary"), scan(now, "today", 4),
            scan(now + 1, "future"), scan(0, "invalid"), TranslationRecord(createdAtMillis = now))
        val summary = glyphActivitySummary(history, now, zone)
        assertEquals(listOf("today", "boundary", "old"), summary.scans.map { it.translationId })
        assertEquals(2, summary.weeklyScans)
        assertEquals(2, summary.activeDays)
        assertEquals(1, summary.multiScans)
        assertEquals(1, summary.days.first().count)
        assertEquals(1, summary.days.last().count)
    }

    @Test fun calendarDaysRemainCorrectAcrossDaylightSavingChange() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val now = timestamp(zone, 10, 12)
        val summary = glyphActivitySummary(listOf(scan(timestamp(zone, 8, 23)), scan(timestamp(zone, 9))), now, zone)
        val transition = summary.days.first { it.startMillis == timestamp(zone, 8) }
        assertEquals(1, transition.count)
        assertEquals(2, summary.activeDays)
        val starts = summary.days.map { it.startMillis }
        assertEquals(23L * 60 * 60 * 1000, starts[5] - starts[4])
        assertEquals(2, summary.weeklyScans)
    }

    @Test fun deletingHistoryUpdatesTotalsAndEmptyDaysStayZero() {
        val now = timestamp(TimeZone.getTimeZone("UTC"), 12, 12)
        val empty = glyphActivitySummary(emptyList(), now)
        assertEquals(7, empty.days.size)
        assertEquals(0, empty.weeklyScans)
        assertEquals(0, empty.activeDays)
        assertEquals(0, empty.multiScans)
        assertTrue(empty.scans.isEmpty())
    }
}
