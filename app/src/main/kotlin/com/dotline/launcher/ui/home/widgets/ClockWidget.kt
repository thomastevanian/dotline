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
import com.dotline.launcher.ui.components.DotText
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

private const val CLOCK_DATE_GAP = 16f

/** Space between the stacked digits and the date column of the wide card (dp). */
private const val CLOCK_COLUMN_GAP = 20f

/** The date under the big inline clock is drawn a little larger than the fine default so it reads at arm's length. */
private const val CLOCK_DATE_SCALE = 1.25f

/** In the wide card the date may grow to this much, so it fills the column beside the stacked digits. */
private const val CLOCK_DATE_MAX_SCALE = 1.7f
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

/**
 * 4x2 / 5x2: hours over minutes as big as the card is high on the left (the Nothing look), the date
 * lines and AM / PM in the column on the right.
 */
@Composable
private fun ClockInlineCard(
    now: ZonedDateTime,
    digits: ClockDigits,
    dateLines: List<String>,
    showDate: Boolean,
) {
    val settings = LocalSettings.current
    val colors = DotlineTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        val availW = maxWidth.value
        val availH = maxHeight.value
        val dot = WidgetFit.clockStackedDot(availW * 0.55f, availH, false, 16f)
        val gap = dot * WidgetFit.CLOCK_GAP_RATIO
        val stackW = WidgetFit.dotTextWidth(2, dot, gap)
        val restW = (availW - stackW - CLOCK_COLUMN_GAP).coerceAtLeast(0f)
        val dateScale = if (showDate && restW > 0f) {
            (restW / WidgetFit.fineLinesWidth(WidgetText.longest(dateLines), 1f)).coerceIn(0.8f, CLOCK_DATE_MAX_SCALE)
        } else {
            1f
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (settings.clockStyle == ClockStyle.DOT) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DotText(text = digits.hours.padStart(2, '0'), dot = dot.dp, gap = gap.dp, color = colors.primary)
                    Spacer(Modifier.height((dot * 2f).dp))
                    DotText(text = digits.minutes, dot = dot.dp, gap = gap.dp, color = colors.primary)
                }
            } else {
                ClockNormalText(
                    digits = digits.copy(hours = digits.hours.padStart(2, '0'), amPm = null),
                    stacked = true,
                    fontSizeDp = WidgetFit.normalClockFontSize(availW * 0.5f, availH, true, false, 120f),
                    color = colors.primary,
                )
            }
            Spacer(Modifier.width(CLOCK_COLUMN_GAP.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                if (showDate) {
                    WidgetFineLines(lines = dateLines, scale = dateScale, color = colors.primary.copy(alpha = 0.78f))
                }
                val amPm = digits.amPm
                if (amPm != null) {
                    Spacer(Modifier.height(16.dp))
                    DotText(text = amPm, dot = 4.dp, gap = 1.dp, color = colors.secondary)
                }
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
