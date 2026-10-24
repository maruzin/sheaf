package com.sheaf.core.domain.tabs

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenTabsTest {

    @Test
    fun `opening adds once and keeps order`() {
        var tabs = emptyList<Long>()
        tabs = OpenTabs.open(tabs, 1)
        tabs = OpenTabs.open(tabs, 2)
        tabs = OpenTabs.open(tabs, 1)
        assertEquals(listOf(1L, 2L), tabs)
    }

    @Test
    fun `oldest tab is dropped beyond the limit`() {
        val tabs = (1L..3L).fold(emptyList<Long>()) { acc, id -> OpenTabs.open(acc, id, max = 2) }
        assertEquals(listOf(2L, 3L), tabs)
    }

    @Test
    fun `close removes only that tab`() {
        assertEquals(listOf(1L, 3L), OpenTabs.close(listOf(1L, 2L, 3L), 2))
        assertEquals(listOf(1L), OpenTabs.close(listOf(1L), 9))
    }

    @Test
    fun `encode and decode round trip and tolerate junk`() {
        assertEquals(listOf(4L, 5L), OpenTabs.decode(OpenTabs.encode(listOf(4L, 5L))))
        assertEquals(listOf(1L, 2L), OpenTabs.decode("1,,x,2,1"))
        assertEquals(emptyList<Long>(), OpenTabs.decode(null))
    }
}
