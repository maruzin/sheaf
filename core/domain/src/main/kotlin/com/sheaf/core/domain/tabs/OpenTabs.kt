package com.sheaf.core.domain.tabs

import kotlinx.coroutines.flow.Flow

/** Documents the user has open as reader tabs, most recently opened last. Survives restarts. */
interface OpenTabsRepository {
    val tabs: Flow<List<Long>>
    suspend fun open(documentId: Long)
    suspend fun close(documentId: Long)
}

/** Pure tab-list rules, shared by the repository and unit tests. */
object OpenTabs {
    const val MAX_TABS = 8

    /** Adds [id] (or keeps it in place if already open). Drops the oldest tab beyond [max]. */
    fun open(tabs: List<Long>, id: Long, max: Int = MAX_TABS): List<Long> {
        if (id in tabs) return tabs
        val next = tabs + id
        return if (next.size > max) next.drop(next.size - max) else next
    }

    fun close(tabs: List<Long>, id: Long): List<Long> = tabs - id

    fun encode(tabs: List<Long>): String = tabs.joinToString(",")

    fun decode(raw: String?): List<Long> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()
}
