package com.dotline.launcher.home

import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetTextTest {
    // 2026-10-09 is a Friday.
    private val friday = LocalDate.of(2026, 10, 9)

    @Test
    fun englishDateStrings() {
        assertEquals("FRIDAY", WidgetText.weekdayFull(friday, Locale.ENGLISH))
        assertEquals("FRI", WidgetText.weekdayShort(friday, Locale.ENGLISH))
        assertEquals("OCTOBER", WidgetText.monthFull(friday, Locale.ENGLISH))
        assertEquals("OCT", WidgetText.monthShort(friday, Locale.ENGLISH))
        assertEquals("9 OCT", WidgetText.dayMonthShort(friday, Locale.ENGLISH))
        assertEquals("FRIDAY 9 OCTOBER", WidgetText.longDate(friday, Locale.ENGLISH))
    }

    @Test
    fun accentsAreStrippedAndTrailingDotsDropped() {
        assertEquals("SABADO", WidgetText.toDot("sábado"))
        assertEquals("FEVR", WidgetText.toDot("févr."))
        assertEquals("STRASSE", WidgetText.toDot("Straße"))
        assertEquals("TURKCE", WidgetText.toDot("Türkçe"))
    }

    @Test
    fun scriptsTheDotFontCannotDrawAreRejected() {
        assertNull(WidgetText.toDot("金曜日"))
        assertNull(WidgetText.toDot("10月"))
        assertEquals("", WidgetText.dotLine("金曜日"))
    }

    @Test
    fun unsupportedLocalesFallBackToEnglish() {
        // Japanese names are not drawable, so the English ones are used.
        val ja = Locale.JAPANESE
        val name = WidgetText.weekdayFull(friday, ja)
        assertTrue(name.all { it in 'A'..'Z' || it == ' ' }, "got $name")
    }

    @Test
    fun sentenceCaseAndLongest() {
        assertEquals("Partly cloudy", WidgetText.sentenceCase("PARTLY CLOUDY"))
        assertEquals("--", WidgetText.sentenceCase("--"))
        assertEquals("", WidgetText.sentenceCase(""))
        assertEquals(5, WidgetText.longest(listOf("FRI", "9 OCT", "A")))
        assertEquals(0, WidgetText.longest(emptyList()))
    }
}
