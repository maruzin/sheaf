package com.sheaf.feature.reader.pages

import org.junit.Assert.assertEquals
import org.junit.Test

class PageReducerTest {

    private fun pages(n: Int) = (0 until n).map { PageItem(sourceIndex = it, rotation = 0) }

    @Test
    fun rotate_advances_only_target_by_90_and_wraps() {
        val out = PageReducer.rotate(pages(3), 1)
        assertEquals(listOf(0, 90, 0), out.map { it.rotation })
        val wrapped = PageReducer.rotate(out.map { it.copy(rotation = 270) }, 1)
        assertEquals(0, wrapped[1].rotation)
    }

    @Test
    fun delete_removes_target_but_never_the_last_page() {
        assertEquals(listOf(0, 2), PageReducer.delete(pages(3), 1).map { it.sourceIndex })
        assertEquals(1, PageReducer.delete(pages(1), 0).size)
    }

    @Test
    fun move_swaps_neighbours_and_ignores_out_of_bounds() {
        assertEquals(listOf(1, 0, 2), PageReducer.move(pages(3), 0, 1).map { it.sourceIndex })
        assertEquals(listOf(0, 1, 2), PageReducer.move(pages(3), 0, -1).map { it.sourceIndex })
        assertEquals(listOf(0, 1, 2), PageReducer.move(pages(3), 2, 1).map { it.sourceIndex })
    }
}
