package com.chuahws.mayalanguageapp.domain.service

/** Pure pagination rules shared by History and Saved Words. */
object ListPaging {
    const val PAGE_SIZE = 10

    fun pageCount(totalItems: Int): Int =
        if (totalItems <= 0) 1 else (totalItems - 1) / PAGE_SIZE + 1

    fun safePage(requestedPage: Int, totalItems: Int): Int =
        requestedPage.coerceIn(1, pageCount(totalItems))

    fun <T> pageItems(items: List<T>, requestedPage: Int): List<T> {
        val offset = (safePage(requestedPage, items.size) - 1) * PAGE_SIZE
        return items.drop(offset).take(PAGE_SIZE)
    }

    fun firstVisibleIndex(requestedPage: Int, totalItems: Int): Int =
        if (totalItems == 0) 0
        else (safePage(requestedPage, totalItems) - 1) * PAGE_SIZE + 1

    fun lastVisibleIndex(requestedPage: Int, totalItems: Int): Int =
        minOf(safePage(requestedPage, totalItems) * PAGE_SIZE, totalItems)
}
