package com.dotline.launcher.ui.home.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dotline.launcher.home.WidgetFit
import com.dotline.launcher.home.WidgetShape
import com.dotline.launcher.home.WidgetSizes
import com.dotline.launcher.home.WidgetText
import com.dotline.launcher.ui.components.DotText
import com.dotline.launcher.ui.theme.DotlineTheme
import java.time.LocalDate
import java.util.Locale

/* ------------------------------------------------------------------------------------------
 * Date widget. 2x1 capsule: "FRI" over "9 OCT" in fine dots. 4x1 capsule: the long form
 * "FRIDAY 9 OCTOBER" on one line. 2x2 card: weekday above, a big dot-matrix day number, month below.
 * The date comes from rememberWidgetToday(), so the widget only recomposes when the day changes.
 * ------------------------------------------------------------------------------------------ */

@Composable
fun DateWidget(spanX: Int, spanY: Int, modifier: Modifier = Modifier) {
    val shape = WidgetSizes.shapeFor(spanX, spanY)
    val today by rememberWidgetToday()
    val locale = Locale.getDefault()
    val spoken = remember(today, locale) { "Date, " + WidgetText.longDate(today, locale) }
    WidgetSurface(shape = shape, modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().widgetDescription(spoken)) {
            when {
                shape == WidgetShape.CARD -> DateCardBody(today)
                spanX >= 4 -> DateWideCapsuleBody(today)
                else -> DateCapsuleBody(today)
            }
        }
    }
}

/** 2x1: two fine lines, weekday over day and month. */
@Composable
private fun DateCapsuleBody(today: LocalDate) {
    val locale = Locale.getDefault()
    val lines = remember(today, locale) {
        listOf(WidgetText.weekdayShort(today, locale), WidgetText.dayMonthShort(today, locale))
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val scale = WidgetFit.fineScale(
            maxChars = WidgetText.longest(lines),
            lineCount = lines.size,
            availW = maxWidth.value,
            availH = maxHeight.value,
            minScale = 0.6f,
            maxScale = 1.8f,
        )
        WidgetFineLines(lines = lines, scale = scale)
    }
}

/** 4x1: "FRIDAY 9 OCTOBER" in one line. */
@Composable
private fun DateWideCapsuleBody(today: LocalDate) {
    val locale = Locale.getDefault()
    val lines = remember(today, locale) { listOf(WidgetText.longDate(today, locale)) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val scale = WidgetFit.fineScale(
            maxChars = WidgetText.longest(lines),
            lineCount = 1,
            availW = maxWidth.value,
            availH = maxHeight.value,
            minScale = 0.6f,
            maxScale = 2f,
        )
        WidgetFineLines(lines = lines, scale = scale)
    }
}

/** 2x2: weekday, big stacked day number, month. */
@Composable
private fun DateCardBody(today: LocalDate) {
    val locale = Locale.getDefault()
    val weekday = remember(today, locale) { WidgetText.weekdayFull(today, locale) }
    val month = remember(today, locale) { WidgetText.monthFull(today, locale) }
    val day = remember(today) { today.dayOfMonth.toString() }
    val colors = DotlineTheme.colors
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val availW = maxWidth.value
        val availH = maxHeight.value
        val fineScale = WidgetFit.fineScale(
            maxChars = maxOf(weekday.length, month.length),
            lineCount = 1,
            availW = availW,
            availH = availH,
            minScale = 0.6f,
            maxScale = 1.4f,
        )
        val lineH = WidgetFit.fineLinesHeight(1, fineScale)
        val space = 16f
        val dot = WidgetFit.numberDot(
            availW = availW,
            availH = availH,
            reservedH = lineH * 2f + space * 2f,
            fill = 0.8f,
            maxDot = 8f,
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WidgetFineLines(lines = listOf(weekday), scale = fineScale, color = colors.secondary)
            Spacer(Modifier.height(space.dp))
            DotText(
                text = day,
                dot = dot.dp,
                gap = (dot * WidgetFit.CLOCK_GAP_RATIO).dp,
                color = colors.primary,
            )
            Spacer(Modifier.height(space.dp))
            WidgetFineLines(lines = listOf(month), scale = fineScale, color = colors.secondary)
        }
    }
}
