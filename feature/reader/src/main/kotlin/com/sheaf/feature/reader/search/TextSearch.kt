package com.sheaf.feature.reader.search

import com.sheaf.core.domain.model.SearchHit
import kotlin.math.max
import kotlin.math.min

/** Pure search + reflow helpers over extracted page text (unit-tested, no Android types). */
object TextSearch {
    private const val SNIPPET_CONTEXT = 30

    /** One hit per page containing [query] (case-insensitive, whitespace-normalised). */
    fun find(pages: List<String>, query: String): List<SearchHit> {
        val needle = normalize(query)
        if (needle.isEmpty()) return emptyList()
        return pages.mapIndexedNotNull { index, raw ->
            val text = normalize(raw)
            val at = text.indexOf(needle, ignoreCase = true)
            if (at < 0) {
                null
            } else {
                SearchHit(
                    pageIndex = index,
                    snippet = snippet(text, at, needle.length),
                    boundsOnPage = emptyList(),
                )
            }
        }
    }

    /** Collapses runs of whitespace (incl. line breaks) so a phrase split across lines still matches. */
    fun normalize(text: String): String = text.replace(WHITESPACE, " ").trim()

    private fun snippet(text: String, index: Int, length: Int): String {
        val start = max(0, index - SNIPPET_CONTEXT)
        val end = min(text.length, index + length + SNIPPET_CONTEXT)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).trim() + suffix
    }

    /**
     * Turns a page's extracted text into reflowable paragraphs: joins hard-wrapped lines, repairs
     * words hyphenated at a line break, and keeps blank-line paragraph breaks.
     */
    fun paragraphs(pageText: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            val p = current.toString().trim()
            if (p.isNotEmpty()) out += p
            current.clear()
        }
        for (rawLine in pageText.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                flush()
                continue
            }
            when {
                current.isEmpty() -> current.append(line)
                // "hyphen-" + "ated" -> "hyphenated"; "Jean-" + "Paul" keeps its real hyphen.
                current.endsWith("-") && line.first().isLowerCase() -> {
                    current.setLength(current.length - 1)
                    current.append(line)
                }
                current.endsWith("-") -> current.append(line)
                else -> current.append(' ').append(line)
            }
            // A line ending a sentence that is much shorter than typical body lines is a paragraph end.
            if (line.length < SHORT_LINE && line.last() in ".!?:") flush()
        }
        flush()
        return out
    }

    private const val SHORT_LINE = 50
    private val WHITESPACE = Regex("\\s+")
}
