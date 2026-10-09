package com.dotline.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.runtime.Immutable
import com.dotline.launcher.core.CrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class CalendarEvent(val title: String, val beginMillis: Long, val endMillis: Long, val allDay: Boolean)

/**
 * Reads the next calendar event on demand (when the launcher resumes and a calendar widget is
 * present). Needs READ_CALENDAR, which is requested only when the calendar widget is added;
 * without it this simply returns null.
 */
object CalendarRepository {
    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun nextEvent(context: Context, now: Long = System.currentTimeMillis()): CalendarEvent? {
        if (!hasPermission(context)) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(builder, now)
                ContentUris.appendId(builder, now + 7L * 24 * 60 * 60 * 1000)
                val projection = arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                )
                context.contentResolver.query(
                    builder.build(), projection, null, null,
                    CalendarContract.Instances.BEGIN + " ASC",
                )?.use { c ->
                    var best: CalendarEvent? = null
                    while (c.moveToNext()) {
                        val allDay = c.getInt(3) == 1
                        val end = c.getLong(2)
                        if (end <= now) continue
                        val event = CalendarEvent(c.getString(0).orEmpty().ifBlank { "Event" }, c.getLong(1), end, allDay)
                        // Prefer timed events that have not ended; an all-day event only if nothing timed comes first today.
                        if (best == null || (best.allDay && !event.allDay)) best = event
                        if (best != null && !best.allDay) break
                    }
                    best
                }
            }.onFailure { CrashLog.record("calendar", it) }.getOrNull()
        }
    }
}
