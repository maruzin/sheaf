package com.sheaf.feature.reader.signature

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The date stamp: the phone's own day / month order, always with a 4-digit year. */
object FormDates {
    fun format(date: LocalDate, locale: Locale): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "ddMMyyyy")
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}
