package com.dotline.launcher.ui.home.widgets

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotline.launcher.data.CalendarEvent
import com.dotline.launcher.data.CalendarFormat
import com.dotline.launcher.data.CalendarRepository
import com.dotline.launcher.home.InfoWidgetMath
import com.dotline.launcher.home.WidgetShape
import com.dotline.launcher.home.WidgetSizes
import com.dotline.launcher.home.WidgetText
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.DotText
import com.dotline.launcher.ui.components.rememberNow
import com.dotline.launcher.ui.theme.DotlineTheme
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.min

/* ------------------------------------------------------------------------------------------
 * Calendar widget: the next calendar event.
 *
 *  - 4x2 card: caption NEXT EVENT and the relative line (IN 25 MIN), the start time in fine
 *    dot-matrix with the end time next to it, the title in Space Grotesk 16 sp (2 lines).
 *  - 2x2 card: the same, narrower, the relative line under the title.
 *  - 4x1 capsule: start time and title on one line, the relative line under the title.
 *
 * The event is read from the calendar provider on a background thread ONLY when the home screen is
 * resumed (see rememberResumeCount), never on a timer, and once more when the shown event has just
 * ended (driven by the minute tick that already exists for the clocks). Without the calendar
 * permission the widget asks to allow it; tapping it then requests the permission, otherwise a tap
 * opens the calendar app.
 * ------------------------------------------------------------------------------------------ */

/** What the last query found. [loaded] is false until the first query finished. */
@Immutable
private class CalendarSnapshot(val loaded: Boolean, val granted: Boolean, val event: CalendarEvent?)

/** Last result seen in this process, so a widget that re-enters composition does not flash empty. */
private var lastCalendarSnapshot: CalendarSnapshot = CalendarSnapshot(loaded = false, granted = false, event = null)

/** Strings that change with the minute clock, computed once per minute. */
@Immutable
private class CalendarTexts(val start: String, val end: String, val relative: String, val spoken: String)

private fun calendarTexts(event: CalendarEvent, now: ZonedDateTime, use24h: Boolean, locale: Locale): CalendarTexts {
    val zone = now.zone
    val nowMillis = now.toInstant().toEpochMilli()
    val relative = CalendarFormat.formatRelative(nowMillis, event.beginMillis, event.allDay, zone, locale)
    val start: String
    val end: String
    if (event.allDay) {
        start = WidgetText.dayMonthShort(CalendarFormat.allDayDate(event.beginMillis), locale)
        end = ""
    } else {
        start = CalendarFormat.formatTime(event.beginMillis, zone, use24h)
        end = CalendarFormat.formatTime(event.endMillis, zone, use24h)
    }
    val timing = if (event.allDay) {
        relative.lowercase(Locale.ROOT)
    } else {
        relative.lowercase(Locale.ROOT) + ", " + start.lowercase(Locale.ROOT) + " to " + end.lowercase(Locale.ROOT)
    }
    return CalendarTexts(start, end, relative, "Next event, " + event.title + ", " + timing)
}

@Composable
fun CalendarWidget(spanX: Int, spanY: Int, modifier: Modifier = Modifier) {
    val shape = WidgetSizes.shapeFor(spanX, spanY)
    val actions = LocalWidgetActions.current
    val now = rememberNow()
    val snapshot by rememberCalendarSnapshot(now)
    val needsPermission = snapshot.loaded && !snapshot.granted
    val onTap: () -> Unit = if (needsPermission) actions.requestCalendarPermission else actions.openCalendarApp
    WidgetSurface(shape = shape, modifier = modifier) {
        InfoWidgetTapArea(onClick = onTap) {
            CalendarBody(shape, snapshot, now)
        }
    }
}

/**
 * Loads the next event when the screen is resumed (and, once, when the shown event has ended).
 * The query itself runs on the IO dispatcher inside CalendarRepository.
 */
@Composable
private fun rememberCalendarSnapshot(now: State<ZonedDateTime>): State<CalendarSnapshot> {
    val context = LocalContext.current
    val resume = rememberResumeCount()
    val state = remember { mutableStateOf(lastCalendarSnapshot) }
    val ended by remember {
        derivedStateOf {
            val event = state.value.event
            event != null && now.value.toInstant().toEpochMilli() >= event.endMillis
        }
    }
    LaunchedEffect(resume.value, ended) {
        val granted = CalendarRepository.hasPermission(context)
        val next = if (granted) CalendarRepository.nextEvent(context) else null
        val loaded = CalendarSnapshot(loaded = true, granted = granted, event = next)
        lastCalendarSnapshot = loaded
        state.value = loaded
    }
    return state
}

@Composable
private fun CalendarBody(shape: WidgetShape, snapshot: CalendarSnapshot, now: State<ZonedDateTime>) {
    val event = snapshot.event
    if (!snapshot.loaded) {
        Box(Modifier.fillMaxSize())
    } else if (!snapshot.granted) {
        InfoNotice(
            shape = shape,
            caption = "CALENDAR",
            message = "Tap to allow calendar",
            modifier = Modifier.infoDescription("Calendar. Tap to allow calendar access."),
        )
    } else if (event == null) {
        InfoNotice(
            shape = shape,
            caption = "NEXT EVENT",
            message = "NO UPCOMING EVENTS",
            modifier = Modifier.infoDescription("Next event. No upcoming events."),
            placeholder = "--:--",
            messageIsCaption = true,
        )
    } else {
        CalendarEventBody(shape, event, now)
    }
}

@Composable
private fun CalendarEventBody(shape: WidgetShape, event: CalendarEvent, now: State<ZonedDateTime>) {
    val settings = LocalSettings.current
    val context = LocalContext.current
    // Reading the minute clock here recomposes only this body once a minute.
    val current = now.value
    val system24 = remember(context, current) { DateFormat.is24HourFormat(context) }
    val use24h = InfoWidgetMath.use24h(settings.timeFormat, system24)
    val locale = Locale.getDefault()
    val texts = remember(event, current, use24h, locale) { calendarTexts(event, current, use24h, locale) }
    Box(Modifier.fillMaxSize().infoDescription(texts.spoken)) {
        when (shape) {
            WidgetShape.CARD -> CalendarCard(event, texts)
            WidgetShape.CAPSULE -> CalendarCapsule(event, texts)
            WidgetShape.CIRCLE -> InfoNotice(shape = shape, caption = "EVENT", message = "")
        }
    }
}

/** The start time (or the date of an all-day event) in fine dot-matrix. */
@Composable
private fun CalendarStart(event: CalendarEvent, texts: CalendarTexts, dot: Float) {
    val color = DotlineTheme.colors.primary
    if (event.allDay) {
        DotText(
            text = texts.start,
            dot = dot.dp,
            gap = (dot * InfoWidgetMath.GAP_RATIO).dp,
            color = color,
        )
    } else {
        InfoDotTime(time = texts.start, dot = dot, color = color)
    }
}

private fun calendarDot(event: CalendarEvent, start: String, availW: Float, maxDot: Float): Float =
    if (event.allDay) {
        InfoWidgetMath.fitTextDot(start, availW, maxDot)
    } else {
        InfoWidgetMath.fitTimeDot(start, availW, maxDot)
    }

/** 4x2 and 2x2. */
@Composable
private fun CalendarCard(event: CalendarEvent, texts: CalendarTexts) {
    val colors = DotlineTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().padding(InfoCardPadding)) {
        val innerW = maxWidth.value
        val innerH = maxHeight.value
        val wide = innerW >= 200f
        val showEnd = wide && texts.end.isNotEmpty()
        val timeW = if (showEnd) innerW * 0.58f else innerW
        val dot = calendarDot(event, texts.start, timeW, 3f)
        val titleLine = 21f
        val bottomH = if (wide) 0f else INFO_CAPTION_HEIGHT + 6f
        val fixedH = INFO_CAPTION_HEIGHT + 10f + InfoWidgetMath.dotHeight(dot) + bottomH + 8f
        val titleLines = InfoWidgetMath.fitLines(innerH - fixedH, titleLine, if (wide) 2 else 3)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                InfoCaption("NEXT EVENT", colors.secondary, Modifier.weight(1f))
                if (wide) {
                    Spacer(Modifier.width(8.dp))
                    InfoCaption(texts.relative, colors.primary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                CalendarStart(event, texts, dot)
                if (showEnd) {
                    Spacer(Modifier.width(10.dp))
                    InfoCaption("TO " + texts.end, colors.secondary, Modifier.offset(y = 2.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            InfoBodyText(event.title, 16f, titleLine, colors.primary, titleLines)
            if (!wide) {
                Spacer(Modifier.height(6.dp))
                InfoCaption(texts.relative, colors.secondary)
            }
        }
    }
}

/** 4x1: time and title on one line, the relative line under the title. */
@Composable
private fun CalendarCapsule(event: CalendarEvent, texts: CalendarTexts) {
    val colors = DotlineTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().padding(InfoCapsulePadding)) {
        val innerW = maxWidth.value
        val innerH = maxHeight.value
        val byHeight = innerH / InfoWidgetMath.dotHeight(1f)
        val maxDot = min(byHeight, 2.4f)
        val dot = calendarDot(event, texts.start, innerW * 0.42f, maxDot)
        val showRelative = innerH >= 40f
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            CalendarStart(event, texts, dot)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                InfoBodyText(event.title, 15f, 20f, colors.primary, 1)
                if (showRelative) {
                    InfoCaption(texts.relative, colors.secondary)
                }
            }
        }
    }
}
