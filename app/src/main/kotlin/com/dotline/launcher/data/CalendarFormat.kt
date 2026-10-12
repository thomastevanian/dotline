package com.dotline.launcher.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure text helpers of the calendar widget ("IN 25 MIN", "TOMORROW", "ALL DAY", "14:30").
 *
 * Everything takes plain epoch milliseconds plus the zone, so it can be unit tested without Android.
 * Note that the calendar provider stores ALL-DAY events in UTC (an all-day event on the 9th begins
 * at 00:00 UTC of the 9th whatever the phone's zone is), so their day is read in UTC.
 */
object CalendarFormat {
    private const val MINUTE_MS = 60_000L

    /** Below this many minutes the line counts down ("IN 2 H 15 MIN"); further away it names the day. */
    private const val COUNTDOWN_LIMIT_MINUTES = 6L * 60L

    /** Events within this many days are named by their weekday; later ones say "IN 9 DAYS". */
    private const val WEEKDAY_LIMIT_DAYS = 7L

    /**
     * The caption under the next event: "NOW", "IN 25 MIN", "IN 2 H", "IN 2 H 15 MIN", "TODAY",
     * "TOMORROW", a weekday ("FRIDAY") or "IN 9 DAYS". An all-day event reads "ALL DAY" while it is
     * today (or still running) and otherwise "TOMORROW", a weekday or "IN 9 DAYS".
     *
     * @param now current time, epoch milliseconds
     * @param begin start of the event, epoch milliseconds
     */
    fun formatRelative(
        now: Long,
        begin: Long,
        allDay: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (allDay) {
            val day = allDayDate(begin)
            val days = day.toEpochDay() - today.toEpochDay()
            return if (days <= 0L) "ALL DAY" else dayLabel(days, day, locale)
        }
        val diff = begin - now
        if (diff <= 0L) return "NOW"
        val minutes = (diff + MINUTE_MS - 1L) / MINUTE_MS
        if (minutes < 60L) return "IN " + minutes + " MIN"
        if (minutes < COUNTDOWN_LIMIT_MINUTES) {
            val hours = minutes / 60L
            val rest = minutes % 60L
            return if (rest == 0L) "IN " + hours + " H" else "IN " + hours + " H " + rest + " MIN"
        }
        val day = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
        val days = day.toEpochDay() - today.toEpochDay()
        return if (days <= 0L) "TODAY" else dayLabel(days, day, locale)
    }

    private fun dayLabel(days: Long, day: LocalDate, locale: Locale): String = when {
        days == 1L -> "TOMORROW"
        days < WEEKDAY_LIMIT_DAYS -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale).uppercase(locale)
        else -> "IN " + days + " DAYS"
    }

    /** "14:30" or, in 12 hour mode, "2:30 PM" (always upper case, the dot font only knows capitals). */
    fun formatTime(millis: Long, zone: ZoneId, use24h: Boolean): String {
        val fmt = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", Locale.ENGLISH)
        return Instant.ofEpochMilli(millis).atZone(zone).format(fmt).uppercase(Locale.ROOT)
    }

    /** The calendar day of an all-day event (they are stored in UTC). */
    fun allDayDate(begin: Long): LocalDate =
        Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
}
