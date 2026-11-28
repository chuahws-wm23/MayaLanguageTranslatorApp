package com.chuahws.mayalanguageapp.presentation.history

import com.chuahws.mayalanguageapp.domain.model.GlyphScanCandidate
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ReportActivityChartTest {
    @Test
    fun groupsTheLastSevenLocalDaysAndSeparatesUnmatchedScans() {
        val clock = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 24, 12, 0)
        }
        val now = clock.timeInMillis
        fun date(daysAgo: Int) = (clock.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }.timeInMillis
        val matched = TranslationRecord(
            glyphScanRecord = true,
            createdAtMillis = now,
            glyphCandidates = listOf(GlyphScanCandidate(twkmSign = 122, visualOverlap = .95)),
        )
        val days = weeklyScanActivity(listOf(
            matched,
            matched.copy(createdAtMillis = date(1), glyphCandidates = emptyList()),
            matched.copy(createdAtMillis = date(6)),
            matched.copy(createdAtMillis = date(7)),
            matched.copy(createdAtMillis = now + 1),
            matched.copy(glyphScanRecord = false),
        ), now)
        assertEquals(7, days.size)
        assertEquals(3, days.sumOf { it.total })
        assertEquals(1, days.first().suggested)
        assertEquals(1, days[5].unmatched)
        assertEquals(1, days.last().suggested)
    }
}
