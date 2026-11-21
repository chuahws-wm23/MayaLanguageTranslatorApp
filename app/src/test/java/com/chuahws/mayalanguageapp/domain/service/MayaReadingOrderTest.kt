package com.chuahws.mayalanguageapp.domain.service

import org.junit.Assert.*
import org.junit.Test

class MayaReadingOrderTest {
    @Test fun digraphsAndGlottalizedLettersFollowMayaReadingOrder() {
        val input=listOf("TZ'AK","K'IN","CH'AM","TZAM","KAL","CHAK","BAJ","AJ","WE'","XUK","YAX")
        assertEquals(listOf("AJ","BAJ","CHAK","CH'AM","KAL","K'IN","TZAM","TZ'AK","WE'","XUK","YAX"),
            input.sortedWith(MayaReadingOrder))
    }
    @Test fun typographyAndAlternateTsSpellingDoNotChangeOrder() {
        assertEquals(0,MayaReadingOrder.compare("K’IN","k'in"))
        assertEquals(0,MayaReadingOrder.compare("TS'AK","TZ'AK"))
    }
    @Test fun dictionaryPagesNeverContainMoreThanTenEntries() {
        val entries=(1..135).toList()
        assertEquals(14,ListPaging.pageCount(entries.size))
        for(page in 1..14)assertTrue(ListPaging.pageItems(entries,page).size<=10)
        assertEquals(listOf(131,132,133,134,135),ListPaging.pageItems(entries,14))
    }
}
