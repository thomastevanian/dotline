package com.dotline.launcher.ui.home.widgets

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dotline.launcher.data.ClockStyle
import com.dotline.launcher.home.WidgetFit
import com.dotline.launcher.home.WidgetShape
import com.dotline.launcher.home.WidgetSizes
import com.dotline.launcher.home.WidgetText
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.ClockDigits
import com.dotline.launcher.ui.components.DotClock
import com.dotline.launcher.ui.components.clockDigits
import com.dotline.launcher.ui.components.rememberNow
import com.dotline.launcher.ui.theme.DotlineFonts
import com.dotline.launcher.ui.theme.DotlineTheme
import java.time.ZonedDateTime
import java.util.Locale

/* ------------------------------------------------------------------------------------------
 * Clock widget. 4x2 card: big dot-matrix HH:MM that fills the card plus the fine dot date.
 * 2x2 card: stacked HH over MM filling the card. 4x1 capsule: HH:MM at the left, the date at the
 * right. The whole widget opens the clock app. The dot size is computed from the available box
 * (WidgetFit) so the digits always fill the width without overflowing.
 * ------------------------------------------------------------------------------------------ */

private enum class ClockLayout { INLINE_CARD, STACKED_CARD, CAPSULE }

private const val CLOCK_DATE_GAP = 14f
private const val CLOCK_CAPSULE_DATE_MIN_WIDTH = 200f

@Composable
fun ClockWidget(spanX: Int, spanY: Int, modifier: Modifier = Modifier) {
    val shape = WidgetSizes.shapeFor(spanX, spanY)
    val actions = LocalWidgetActions.current
    val settings = LocalSettings.current
    val layout = when {
        shape == WidgetShape.CAPSULE -> ClockLayout.CAPSULE
        shape == WidgetShape.CARD && spanX >= 3 -> ClockLayout.INLINE_CARD
        else -> ClockLayout.STACKED_CARD
    }
    ClickableWidgetSurface(shape = shape, onClick = actions.openClockApp, modifier = modifier) {
        // "Hide clock" keeps the surface and draws nothing on it.
        if (!settings.hideClock) {
            ClockWidgetBody(layout)
        }
    }
}

/** Reads the minute clock; the once a minute tick recomposes only this body. */
@Composable
private fun ClockWidgetBody(layout: ClockLayout) {
    val settings = LocalSettings.current
    val context = LocalContext.current
    val now by rememberNow()
    val system24 = remember(context, now) { DateFormat.is24HourFormat(context) }
    val digits = remember(now, settings.timeFormat, system24) {
        clockDigits(now, settings.timeFormat, system24)
    }
    val locale = Locale.getDefault()
    val today = now.toLocalDate()
    val dateLines = remember(today, locale, layout) {
        if (layout == ClockLayout.CAPSULE) {
            listOf(WidgetText.weekdayShort(today, locale), WidgetText.dayMonthShort(today, locale))
        } else {
            listOf(WidgetText.weekdayFull(today, locale), WidgetText.dayMonthShort(today, locale))
        }
    }
    val spoken = remember(digits) {
        val time = digits.hours + ":" + digits.minutes
        if (digits.amPm != null) "Clock, " + time + " " + digits.amPm else "Clock, " + time
    }
    Box(modifier = Modifier.fillMaxSize().widgetDescription(spoken)) {
        when (layout) {
            ClockLayout.INLINE_CARD -> ClockInlineCard(now, digits, dateLines, settings.showDate)
            ClockLayout.STACKED_CARD -> ClockStackedCard(now, digits)
            ClockLayout.CAPSULE -> ClockCapsule(now, digits, dateLines, settings.showDate)
        }
    }
}

/** 4x2: inline HH:MM sized to the card width, the date in fine dots underneath. */
@Composable
private fun ClockInlineCard(
    now: ZonedDateTime,
    digits: ClockDigits,
    dateLines: List<String>,
    showDate: Boolean,
) {
    val secondary = DotlineTheme.colors.secondary
    val hasAmPm = digits.amPm != null
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val availW = maxWidth.value
        val availH = maxHeight.value
        val dateBlock = if (showDate) WidgetFit.fineLinesHeight(2, 1f) + CLOCK_DATE_GAP else 0f
        val clockH = availH - dateBlock
        val dot = WidgetFit.clockInlineDot(availW, clockH, hasAmPm, 9f)
        val normalSize = WidgetFit.normalClockFontSize(availW, clockH, false, hasAmPm, 96f)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            ClockFace(now = now, digits = digits, stacked = false, dot = dot, normalSize = normalSize)
            if (showDate) {
                Spacer(Modifier.height(CLOCK_DATE_GAP.dp))
                WidgetFineLines(lines = dateLines, scale = 1f, color = secondary)
            }
        }
    }
}

/** 2x2: hours over minutes, as big as the card allows. */
@Composable
private fun ClockStackedCard(now: ZonedDateTime, digits: ClockDigits) {
    val hasAmPm = digits.amPm != null
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val availW = maxWidth.value
        val availH = maxHeight.value
        val dot = WidgetFit.clockStackedDot(availW, availH, hasAmPm, 10f)
        val normalSize = WidgetFit.normalClockFontSize(availW, availH, true, hasAmPm, 96f)
        ClockFace(now = now, digits = digits, stacked = true, dot = dot, normalSize = normalSize)
    }
}

/** 4x1: the time at the left, the date lines at the right. */
@Composable
private fun ClockCapsule(
    now: ZonedDateTime,
    digits: ClockDigits,
    dateLines: List<String>,
    showDate: Boolean,
) {
    val secondary = DotlineTheme.colors.secondary
    val hasAmPm = digits.amPm != null
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 10.dp)) {
        val availW = maxWidth.value
        val availH = maxHeight.value
        val withDate = showDate && availW + 44f >= CLOCK_CAPSULE_DATE_MIN_WIDTH
        val dateW = if (withDate) WidgetFit.fineLinesWidth(WidgetText.longest(dateLines), 1f) else 0f
        val clockW = availW - dateW - (if (withDate) 16f else 0f)
        val dot = WidgetFit.clockInlineDot(clockW, availH, hasAmPm, 6f)
        val normalSize = WidgetFit.normalClockFontSize(clockW, availH, false, hasAmPm, 64f)
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClockFace(now = now, digits = digits, stacked = false, dot = dot, normalSize = normalSize)
            if (withDate) {
                Spacer(Modifier.weight(1f))
                WidgetFineLines(lines = dateLines, scale = 1f, color = secondary)
            }
        }
    }
}

/** The digits in the user's clock style: dot-matrix (DotClock) or Space Grotesk text. */
@Composable
private fun ClockFace(
    now: ZonedDateTime,
    digits: ClockDigits,
    stacked: Boolean,
    dot: Float,
    normalSize: Float,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val color = DotlineTheme.colors.primary
    if (settings.clockStyle == ClockStyle.DOT) {
        DotClock(
            now = now,
            modifier = modifier,
            stacked = stacked,
            dot = dot.dp,
            gap = (dot * WidgetFit.CLOCK_GAP_RATIO).dp,
            color = color,
        )
    } else {
        ClockNormalText(
            digits = digits,
            stacked = stacked,
            fontSizeDp = normalSize,
            color = color,
            modifier = modifier,
        )
    }
}

@Composable
private fun ClockNormalText(
    digits: ClockDigits,
    stacked: Boolean,
    fontSizeDp: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fontSize = widgetFixedSp(fontSizeDp)
    val style = remember(fontSize, color) {
        TextStyle(
            fontFamily = DotlineFonts.Body,
            fontWeight = FontWeight.Normal,
            fontSize = fontSize,
            lineHeight = fontSize,
            color = color,
        )
    }
    val amPmStyle = DotlineTheme.type.label.copy(color = color)
    val amPm = digits.amPm
    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(text = digits.hours, style = style, maxLines = 1, softWrap = false)
            BasicText(text = digits.minutes, style = style, maxLines = 1, softWrap = false)
            if (amPm != null) {
                Spacer(Modifier.height(4.dp))
                BasicText(text = amPm, style = amPmStyle)
            }
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.Bottom) {
            BasicText(
                text = digits.hours + ":" + digits.minutes,
                style = style,
                maxLines = 1,
                softWrap = false,
            )
            if (amPm != null) {
                Spacer(Modifier.width(6.dp))
                BasicText(text = amPm, style = amPmStyle)
            }
        }
    }
}
