package com.dotline.launcher.data

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Immutable
data class WorldClockEntry(
    /** Upper-case city label derived from the zone id, e.g. "NEW YORK". */
    val label: String,
    /** "14:05" or "2:05 PM". */
    val time: String,
    /** -1, 0, +1 relative to the local calendar day. */
    val dayOffset: Int,
    /** Offset from local time in hours, e.g. +5.5. */
    val hoursFromLocal: Double,
)

object WorldClock {
    fun cityLabel(zoneId: String): String =
        zoneId.substringAfterLast('/').replace('_', ' ').uppercase(Locale.ROOT)

    fun entries(now: Instant, local: ZoneId, zones: List<String>, use24h: Boolean): List<WorldClockEntry> {
        val fmt = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", Locale.ENGLISH)
        val localTime = now.atZone(local)
        return zones.mapNotNull { id ->
            val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return@mapNotNull null
            val t = now.atZone(zone)
            val dayDiff = (t.toLocalDate().toEpochDay() - localTime.toLocalDate().toEpochDay()).toInt().coerceIn(-1, 1)
            val hours = (t.offset.totalSeconds - localTime.offset.totalSeconds) / 3600.0
            WorldClockEntry(cityLabel(id), t.format(fmt).uppercase(Locale.ROOT), dayDiff, hours)
        }
    }
}
