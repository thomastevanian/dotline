package com.dotline.launcher.data

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarFormatTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val minute = 60_000L
    private val hour = 60L * minute
    private val day = 24L * hour

    // Friday 9 October 2026, 10:00 UTC.
    private val now: Long = ZonedDateTime.of(2026, 10, 9, 10, 0, 0, 0, utc).toInstant().toEpochMilli()

    private fun rel(begin: Long, allDay: Boolean = false, zone: ZoneId = utc, at: Long = now): String =
        CalendarFormat.formatRelative(at, begin, allDay, zone, Locale.ENGLISH)

    private fun utcMillis(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun minutesCountDown() {
        assertEquals("IN 25 MIN", rel(now + 25 * minute))
        assertEquals("IN 1 MIN", rel(now + 30_000L))
        assertEquals("IN 1 MIN", rel(now + 1L))
        assertEquals("IN 59 MIN", rel(now + 59 * minute))
    }

    @Test
    fun partialMinutesRoundUp() {
        assertEquals("IN 25 MIN", rel(now + 25 * minute - 1L))
        assertEquals("IN 26 MIN", rel(now + 25 * minute + 1L))
    }

    @Test
    fun startedEventsSayNow() {
        assertEquals("NOW", rel(now))
        assertEquals("NOW", rel(now - 10 * minute))
    }

    @Test
    fun hoursCountDownForSixHours() {
        assertEquals("IN 1 H", rel(now + hour))
        assertEquals("IN 1 H 30 MIN", rel(now + 90 * minute))
        assertEquals("IN 5 H 59 MIN", rel(now + 5 * hour + 59 * minute))
    }

    @Test
    fun laterTodayIsToday() {
        // 10:00 + 6 h = 16:00, still the same day.
        assertEquals("TODAY", rel(now + 6 * hour))
        assertEquals("TODAY", rel(utcMillis(2026, 10, 9, 23, 59)))
    }

    @Test
    fun soonAfterMidnightStillCountsDown() {
        val late = utcMillis(2026, 10, 9, 23, 0)
        assertEquals("IN 2 H", rel(utcMillis(2026, 10, 10, 1, 0), at = late))
    }

    @Test
    fun tomorrowAndWeekdays() {
        assertEquals("TOMORROW", rel(utcMillis(2026, 10, 10, 9, 0)))
        assertEquals("MONDAY", rel(utcMillis(2026, 10, 12, 9, 0)))
        assertEquals("THURSDAY", rel(utcMillis(2026, 10, 15, 18, 30)))
    }

    @Test
    fun farAwayEventsCountDays() {
        assertEquals("IN 7 DAYS", rel(utcMillis(2026, 10, 16, 9, 0)))
        assertEquals("IN 9 DAYS", rel(now + 9 * day))
    }

    @Test
    fun allDayEventsUseTheUtcDay() {
        assertEquals("ALL DAY", rel(utcMillis(2026, 10, 9), allDay = true))
        // A multi day event that began yesterday is still all day today.
        assertEquals("ALL DAY", rel(utcMillis(2026, 10, 8), allDay = true))
        assertEquals("TOMORROW", rel(utcMillis(2026, 10, 10), allDay = true))
        assertEquals("WEDNESDAY", rel(utcMillis(2026, 10, 14), allDay = true))
        assertEquals("IN 8 DAYS", rel(utcMillis(2026, 10, 17), allDay = true))
    }

    @Test
    fun allDayDayIsReadInUtcNotInThePhoneZone() {
        // 23:00 on 9 October in Auckland (UTC+13 in October 2026) is 10:00 UTC the same day.
        val auckland = ZoneId.of("Pacific/Auckland")
        val aucklandNow = ZonedDateTime.of(2026, 10, 9, 23, 0, 0, 0, auckland).toInstant().toEpochMilli()
        assertEquals("ALL DAY", rel(utcMillis(2026, 10, 9), allDay = true, zone = auckland, at = aucklandNow))
        assertEquals("TOMORROW", rel(utcMillis(2026, 10, 10), allDay = true, zone = auckland, at = aucklandNow))
    }

    @Test
    fun timedEventDayIsReadInThePhoneZone() {
        val auckland = ZoneId.of("Pacific/Auckland")
        val aucklandNow = ZonedDateTime.of(2026, 10, 9, 23, 0, 0, 0, auckland).toInstant().toEpochMilli()
        val nextMorning = ZonedDateTime.of(2026, 10, 10, 9, 0, 0, 0, auckland).toInstant().toEpochMilli()
        assertEquals("TOMORROW", rel(nextMorning, zone = auckland, at = aucklandNow))
    }

    @Test
    fun allDayDateIsTheUtcDate() {
        assertEquals(java.time.LocalDate.of(2026, 10, 9), CalendarFormat.allDayDate(utcMillis(2026, 10, 9)))
    }

    @Test
    fun timeFormats() {
        val afternoon = utcMillis(2026, 10, 9, 14, 30)
        assertEquals("14:30", CalendarFormat.formatTime(afternoon, utc, true))
        assertEquals("2:30 PM", CalendarFormat.formatTime(afternoon, utc, false))
        assertEquals("12:05 AM", CalendarFormat.formatTime(utcMillis(2026, 10, 9, 0, 5), utc, false))
        assertEquals("00:05", CalendarFormat.formatTime(utcMillis(2026, 10, 9, 0, 5), utc, true))
        assertEquals("12:00 PM", CalendarFormat.formatTime(utcMillis(2026, 10, 9, 12, 0), utc, false))
    }

    @Test
    fun timeFormatFollowsTheZone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals("19:30", CalendarFormat.formatTime(utcMillis(2026, 10, 9, 10, 30), tokyo, true))
    }
}
