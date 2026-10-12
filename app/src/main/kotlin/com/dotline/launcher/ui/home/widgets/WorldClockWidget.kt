package com.dotline.launcher.ui.home.widgets

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotline.launcher.data.WorldClock
import com.dotline.launcher.data.WorldClockEntry
import com.dotline.launcher.home.InfoWidgetMath
import com.dotline.launcher.home.WidgetShape
import com.dotline.launcher.home.WidgetSizes
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.DottedDivider
import com.dotline.launcher.ui.components.rememberNow
import com.dotline.launcher.ui.theme.DotlineTheme
import java.util.Locale
import kotlin.math.min

/* ------------------------------------------------------------------------------------------
 * World clock widget: two cities from the settings with their local time.
 *
 *  - 4x2 card: two rows, the city in 9 sp mono capitals at the left (with +1D / -1D when it is
 *    another calendar day there), the time in fine dot-matrix at the right, a dotted divider
 *    between the rows.
 *  - 4x1 capsule: the two cities side by side, city over time, a dotted separator between them.
 *
 * The times come from WorldClock.entries and are recomputed once a minute from the minute clock
 * (rememberNow), nothing else wakes this widget. A tap opens the clock app.
 * ------------------------------------------------------------------------------------------ */

/** Width taken by the dotted separator and its margins in the capsule, in dp. */
private const val CAPSULE_SEPARATOR_WIDTH = 30f

/** The most cities the widget shows. */
private const val WORLD_CLOCK_MAX_CITIES = 2

@Composable
fun WorldClockWidget(spanX: Int, spanY: Int, modifier: Modifier = Modifier) {
    val shape = WidgetSizes.shapeFor(spanX, spanY)
    val actions = LocalWidgetActions.current
    WidgetSurface(shape = shape, modifier = modifier) {
        InfoWidgetTapArea(onClick = actions.openClockApp) {
            WorldClockBody(shape)
        }
    }
}

/** Reads the minute clock; the once a minute tick recomposes only this body. */
@Composable
private fun WorldClockBody(shape: WidgetShape) {
    val settings = LocalSettings.current
    val context = LocalContext.current
    val now by rememberNow()
    val system24 = remember(context, now) { DateFormat.is24HourFormat(context) }
    val use24h = InfoWidgetMath.use24h(settings.timeFormat, system24)
    val zones = settings.worldClockZones
    val entries = remember(now, zones, use24h) {
        WorldClock.entries(now.toInstant(), now.zone, zones, use24h).take(WORLD_CLOCK_MAX_CITIES)
    }
    val spoken = remember(entries) {
        val parts = entries.joinToString(", ") { entry ->
            entry.label.lowercase(Locale.ROOT) + " " + entry.time.lowercase(Locale.ROOT)
        }
        if (parts.isEmpty()) "World clock, no cities" else "World clock, " + parts
    }
    Box(Modifier.fillMaxSize().infoDescription(spoken)) {
        if (entries.isEmpty()) {
            InfoNotice(
                shape = shape,
                caption = "WORLD CLOCK",
                message = "ADD CITIES IN SETTINGS",
                messageIsCaption = true,
            )
        } else {
            when (shape) {
                WidgetShape.CARD -> WorldClockCard(entries)
                WidgetShape.CAPSULE -> WorldClockCapsule(entries)
                WidgetShape.CIRCLE -> InfoNotice(shape = shape, caption = "TIME", message = "")
            }
        }
    }
}

/** The label of a city and, when it is another day there, the +1D / -1D mark. */
@Composable
private fun CityLabel(entry: WorldClockEntry, modifier: Modifier = Modifier) {
    val colors = DotlineTheme.colors
    val dayCaption = InfoWidgetMath.dayCaption(entry.dayOffset)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        InfoCaption(entry.label, colors.secondary, Modifier.weight(1f, fill = false))
        if (dayCaption.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            InfoCaption(dayCaption, colors.primary)
        }
    }
}

/** 4x2: one row per city, a dotted divider between them. */
@Composable
private fun WorldClockCard(entries: List<WorldClockEntry>) {
    val colors = DotlineTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 6.dp)) {
        val innerW = maxWidth.value
        Column(Modifier.fillMaxSize()) {
            for (index in entries.indices) {
                val entry = entries[index]
                if (index > 0) {
                    DottedDivider()
                }
                val dot = InfoWidgetMath.fitTimeDot(entry.time, innerW * 0.62f, 3f)
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CityLabel(entry, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    InfoDotTime(time = entry.time, dot = dot, color = colors.primary)
                }
            }
        }
    }
}

/** 4x1: the cities side by side. */
@Composable
private fun WorldClockCapsule(entries: List<WorldClockEntry>) {
    val colors = DotlineTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().padding(InfoCapsulePadding)) {
        val innerW = maxWidth.value
        val innerH = maxHeight.value
        val count = entries.size
        val columnW = (innerW - (count - 1) * CAPSULE_SEPARATOR_WIDTH) / count
        val byHeight = (innerH - INFO_CAPTION_HEIGHT - 4f) / InfoWidgetMath.dotHeight(1f)
        val maxDot = min(byHeight, 2.4f)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            for (index in entries.indices) {
                val entry = entries[index]
                if (index > 0) {
                    Spacer(Modifier.width(14.dp))
                    InfoVerticalDots(Modifier.height((innerH * 0.6f).dp))
                    Spacer(Modifier.width(14.dp))
                }
                val dot = InfoWidgetMath.fitTimeDot(entry.time, columnW, maxDot)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    CityLabel(entry)
                    Spacer(Modifier.height(4.dp))
                    InfoDotTime(time = entry.time, dot = dot, color = colors.primary)
                }
            }
        }
    }
}
