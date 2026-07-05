package com.sheaf.feature.reader.pages

/**
 * Pure, Android-free transformations for the page-management grid, extracted so they can be
 * unit-tested without instrumentation (per the vault's testing strategy).
 */
object PageReducer {
    fun rotate(items: List<PageItem>, position: Int): List<PageItem> =
        items.mapIndexed { i, item ->
            if (i == position) item.copy(rotation = (item.rotation + 90) % 360) else item
        }

    fun delete(items: List<PageItem>, position: Int): List<PageItem> =
        if (items.size <= 1) items else items.filterIndexed { i, _ -> i != position }

    fun move(items: List<PageItem>, position: Int, delta: Int): List<PageItem> {
        val target = position + delta
        if (position !in items.indices || target !in items.indices) return items
        val list = items.toMutableList()
        val tmp = list[position]
        list[position] = list[target]
        list[target] = tmp
        return list
    }
}
