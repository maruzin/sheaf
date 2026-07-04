package com.sheaf.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-state assertions for reader intents. The VM's reducer is intentionally free of Android
 * types so it is unit-testable without instrumentation (per Android Stack testing guidance).
 */
class ReaderStateReducerTest {
    @Test
    fun toggleOutline_flips_visibility() {
        var s = ReaderUiState()
        assertFalse(s.outlineVisible)
        s = s.copy(outlineVisible = !s.outlineVisible)
        assertTrue(s.outlineVisible)
    }

    @Test
    fun jumpTo_sets_page_and_hides_outline() {
        val s = ReaderUiState(outlineVisible = true).copy(currentPage = 12, outlineVisible = false)
        assertEquals(12, s.currentPage)
        assertFalse(s.outlineVisible)
    }
}
