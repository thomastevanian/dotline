package com.dotline.launcher.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.ClockStyle
import com.dotline.launcher.data.TimeFormat
import com.dotline.launcher.home.WidgetText
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.theme.DotlineFonts
import com.dotline.launcher.ui.theme.DotlineTheme
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

/** The parts of a clock face. [amPm] is "AM" or "PM" in 12 hour mode and null in 24 hour mode. */
@Immutable
data class ClockDigits(val hours: String, val minutes: String, val amPm: String?)

/**
 * Splits [now] into display strings. 24 hour: two digit hours ("07"). 12 hour: no leading zero
 * ("7") plus "AM"/"PM". [format] decides 12 vs 24; [TimeFormat.SYSTEM] follows [system24].
 */
fun clockDigits(now: ZonedDateTime, format: TimeFormat, system24: Boolean): ClockDigits {
    val use24 = when (format) {
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
        TimeFormat.SYSTEM -> system24
    }
    val minutes = now.minute.toString().padStart(2, '0')
    if (use24) {
        return ClockDigits(now.hour.toString().padStart(2, '0'), minutes, null)
    }
    val hour12 = now.hour % 12
    val shown = if (hour12 == 0) 12 else hour12
    val amPm = if (now.hour < 12) "AM" else "PM"
    return ClockDigits(shown.toString(), minutes, amPm)
}

/**
 * Current time as state, minute-aligned and battery friendly. A receiver for TIME_TICK,
 * TIME_CHANGED and TIMEZONE_CHANGED is registered only while the lifecycle is STARTED and is
 * removed when it stops; the value is refreshed immediately on every start. No timers, no alarms.
 */
@Composable
fun rememberNow(): State<ZonedDateTime> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(ZonedDateTime.now()) }
    DisposableEffect(lifecycleOwner, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent != null && intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    TimeZone.setDefault(null)
                }
                state.value = ZonedDateTime.now()
            }
        }
        var registered = false

        fun registerTimeReceiver() {
            if (registered) return
            try {
                val filter = IntentFilter()
                filter.addAction(Intent.ACTION_TIME_TICK)
                filter.addAction(Intent.ACTION_TIME_CHANGED)
                filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                registered = true
            } catch (e: RuntimeException) {
                CrashLog.record("rememberNow.register", e)
            }
        }

        fun unregisterTimeReceiver() {
            if (!registered) return
            registered = false
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Already unregistered; nothing to do.
            }
        }

        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        state.value = ZonedDateTime.now()
                        registerTimeReceiver()
                    }
                    Lifecycle.Event.ON_STOP -> unregisterTimeReceiver()
                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregisterTimeReceiver()
        }
    }
    return state
}

/**
 * The home screen clock. DOT style draws dot-matrix digits ([DotText]); NORMAL style draws large
 * Space Grotesk text. [stacked] puts the hours above the minutes. Draws nothing when the
 * "hide clock" setting is on. [dot] and [gap] size the dot-matrix digits.
 */
@Composable
fun DotClock(
    now: ZonedDateTime,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    dot: Dp = 6.dp,
    gap: Dp = 2.dp,
    color: Color = DotlineTheme.colors.primary,
) {
    val settings = LocalSettings.current
    if (!settings.hideClock) {
        val context = LocalContext.current
        val system24 = remember(context, now) { DateFormat.is24HourFormat(context) }
        val digits = remember(now, settings.timeFormat, system24) {
            clockDigits(now, settings.timeFormat, system24)
        }
        if (settings.clockStyle == ClockStyle.DOT) {
            DotClockDots(digits, modifier, stacked, dot, gap, color)
        } else {
            DotClockNormal(digits, modifier, stacked, color)
        }
    }
}

@Composable
private fun DotClockDots(
    digits: ClockDigits,
    modifier: Modifier,
    stacked: Boolean,
    dot: Dp,
    gap: Dp,
    color: Color,
) {
    val amPm = digits.amPm
    val smallDot = dot * 0.3f
    val smallGap = gap * 0.3f
    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            DotText(text = digits.hours, dot = dot, gap = gap, color = color)
            Spacer(Modifier.height(dot * 2f))
            DotText(text = digits.minutes, dot = dot, gap = gap, color = color)
            if (amPm != null) {
                Spacer(Modifier.height(dot * 2f))
                DotText(text = amPm, dot = smallDot, gap = smallGap, color = color)
            }
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.Bottom) {
            DotText(text = digits.hours + ":" + digits.minutes, dot = dot, gap = gap, color = color)
            if (amPm != null) {
                Spacer(Modifier.width(dot * 2f))
                DotText(text = amPm, dot = smallDot, gap = smallGap, color = color)
            }
        }
    }
}

@Composable
private fun DotClockNormal(
    digits: ClockDigits,
    modifier: Modifier,
    stacked: Boolean,
    color: Color,
) {
    val amPm = digits.amPm
    val fontSize = if (stacked) 88.sp else 72.sp
    val timeStyle = TextStyle(
        fontFamily = DotlineFonts.Body,
        fontWeight = FontWeight.Normal,
        fontSize = fontSize,
        lineHeight = fontSize,
        color = color,
    )
    val amPmStyle = DotlineTheme.type.label.copy(color = color)
    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(text = digits.hours, style = timeStyle, maxLines = 1, softWrap = false)
            BasicText(text = digits.minutes, style = timeStyle, maxLines = 1, softWrap = false)
            if (amPm != null) {
                Spacer(Modifier.height(4.dp))
                BasicText(text = amPm, style = amPmStyle)
            }
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.Bottom) {
            BasicText(
                text = digits.hours + ":" + digits.minutes,
                style = timeStyle,
                maxLines = 1,
                softWrap = false,
            )
            if (amPm != null) {
                Spacer(Modifier.width(6.dp))
                BasicText(
                    text = amPm,
                    modifier = Modifier.padding(bottom = 12.dp),
                    style = amPmStyle,
                )
            }
        }
    }
}

/**
 * The two date lines. [WidgetText] strips accents and falls back to the English names, because the
 * 5 x 7 dot font only knows A-Z and digits (a French, German or Japanese phone would otherwise
 * show "?" glyphs).
 */
private fun dotDateLines(date: LocalDate, locale: Locale): Pair<String, String> =
    Pair(WidgetText.weekdayFull(date, locale), WidgetText.dayMonthShort(date, locale))

/**
 * Dot-matrix date in two left-aligned lines: the weekday (FRIDAY) and then day and month (9 OCT).
 * Very fine text as in the Nothing OS reference: 1.0 dp dots, 1.4 dp pitch (0.4 dp gaps) and the
 * two lines 18 dp apart (the line gap is 8.6 dots). Draws nothing when "show date" is off.
 */
@Composable
fun DotDate(
    now: ZonedDateTime,
    modifier: Modifier = Modifier,
    color: Color = DotlineTheme.colors.secondary,
    dot: Dp = 1.dp,
    gap: Dp = 0.4.dp,
) {
    val settings = LocalSettings.current
    if (settings.showDate) {
        val locale = Locale.getDefault()
        val today = now.toLocalDate()
        val lines = remember(today, locale) { dotDateLines(today, locale) }
        Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(dot * 8.6f),
            horizontalAlignment = Alignment.Start,
        ) {
            DotText(text = lines.first, dot = dot, gap = gap, color = color)
            DotText(text = lines.second, dot = dot, gap = gap, color = color)
        }
    }
}
