package com.chuahws.mayalanguageapp.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListPagingTest {

    @Test
    fun onePageForZeroToTenItems() {
        assertEquals(1, ListPaging.pageCount(0))
        assertEquals(1, ListPaging.pageCount(10))
        assertTrue(ListPaging.pageItems(emptyList<Int>(), 1).isEmpty())
    }

    @Test
    fun elevenResultsCreateSecondPageWithOneItem() {
        val values = (1..11).toList()
        assertEquals(2, ListPaging.pageCount(values.size))
        assertEquals((1..10).toList(), ListPaging.pageItems(values, 1))
        assertEquals(listOf(11), ListPaging.pageItems(values, 2))
    }

    @Test
    fun navigationToSpecificPageKeepsOnlyTenEntries() {
        val values = (1..34).toList()
        assertEquals(4, ListPaging.pageCount(values.size))
        assertEquals((21..30).toList(), ListPaging.pageItems(values, 3))
        assertEquals(21, ListPaging.firstVisibleIndex(3, values.size))
        assertEquals(30, ListPaging.lastVisibleIndex(3, values.size))
    }

    @Test
    fun pageNumberClampsAfterDeletingFinalPageEntries() {
        assertEquals(2, ListPaging.safePage(3, 20))
        assertEquals((11..20).toList(), ListPaging.pageItems((1..20).toList(), 3))
        assertEquals(1, ListPaging.safePage(-10, 20))
    }

    @Test
    fun filteredResultsRecalculatePageBounds() {
        val matches = (1..35).filter { it % 2 == 0 }
        assertEquals(2, ListPaging.pageCount(matches.size))
        assertEquals(10, ListPaging.pageItems(matches, 1).size)
        assertEquals(7, ListPaging.pageItems(matches, 2).size)
    }
}
