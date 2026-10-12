package com.dotline.launcher.home

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Text of the dot-matrix widgets. The 5 x 7 dot font only knows A-Z, digits and a few signs, so
 * names from other locales are upper-cased, stripped of accents ("SÁBADO" becomes "SABADO") and,
 * when anything else is left over (for example Japanese), replaced by the English name.
 */
object WidgetText {
    private val combiningMarks = Regex("\\p{M}+")

    private fun isDotChar(c: Char): Boolean =
        (c in 'A'..'Z') || (c in '0'..'9') || c == ' ' || c == '-' || c == '/' || c == ',' || c == '.'

    /** Upper-cased, accent-free [raw] or null when the dot font cannot draw all of it. */
    fun toDot(raw: String): String? {
        val upper = raw.uppercase(Locale.ROOT)
        val stripped = Normalizer.normalize(upper, Normalizer.Form.NFD).replace(combiningMarks, "")
        val trimmed = stripped.trim().trimEnd('.')
        for (c in trimmed) {
            if (!isDotChar(c)) return null
        }
        return trimmed
    }

    /** [raw] as dot-matrix text, or an empty string when it cannot be drawn. */
    fun dotLine(raw: String): String = toDot(raw) ?: ""

    private fun pick(local: String, english: String): String =
        toDot(local) ?: toDot(english) ?: english.uppercase(Locale.ROOT)

    fun weekdayFull(date: LocalDate, locale: Locale): String = pick(
        date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
    )

    fun weekdayShort(date: LocalDate, locale: Locale): String = pick(
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
    )

    fun monthFull(date: LocalDate, locale: Locale): String = pick(
        date.month.getDisplayName(TextStyle.FULL, locale),
        date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
    )

    fun monthShort(date: LocalDate, locale: Locale): String = pick(
        date.month.getDisplayName(TextStyle.SHORT, locale),
        date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
    )

    /** "9 OCT" */
    fun dayMonthShort(date: LocalDate, locale: Locale): String =
        date.dayOfMonth.toString() + " " + monthShort(date, locale)

    /** "FRIDAY 9 OCTOBER" */
    fun longDate(date: LocalDate, locale: Locale): String =
        weekdayFull(date, locale) + " " + date.dayOfMonth.toString() + " " + monthFull(date, locale)

    /** "PARTLY CLOUDY" becomes "Partly cloudy". */
    fun sentenceCase(s: String): String {
        if (s.isEmpty()) return s
        return s.substring(0, 1).uppercase(Locale.ROOT) + s.substring(1).lowercase(Locale.ROOT)
    }

    /** Length of the longest string. */
    fun longest(lines: List<String>): Int {
        var n = 0
        for (line in lines) {
            if (line.length > n) n = line.length
        }
        return n
    }
}
