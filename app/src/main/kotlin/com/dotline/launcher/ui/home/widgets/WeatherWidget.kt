package com.dotline.launcher.ui.home.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotline.launcher.data.WeatherUnit
import com.dotline.launcher.data.weather.WeatherFormat
import com.dotline.launcher.data.weather.WeatherKind
import com.dotline.launcher.data.weather.WeatherSnapshot
import com.dotline.launcher.home.WidgetFit
import com.dotline.launcher.home.WidgetShape
import com.dotline.launcher.home.WidgetSizes
import com.dotline.launcher.home.WidgetText
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.DotText
import com.dotline.launcher.ui.theme.DotlineTheme
import java.util.Locale

/* ------------------------------------------------------------------------------------------
 * Weather widget (Open-Meteo data from the repository, never fetched here).
 * 4x2 card exactly like the Nothing reference: weekday, date and condition in very fine dot-matrix
 * text, then the dot pictogram with the temperature and a tiny degree sign. 2x1 capsule: the
 * pictogram and the condition word. 1x1 circle: the temperature. Without data it shows "--" and a
 * hint. It is not clickable.
 * ------------------------------------------------------------------------------------------ */

private val WeatherDegreeRows: List<String> = listOf(".#.", "#.#", ".#.")

@Composable
fun WeatherWidget(spanX: Int, spanY: Int, modifier: Modifier = Modifier) {
    val shape = WidgetSizes.shapeFor(spanX, spanY)
    val graph = LocalAppGraph.current
    val settings = LocalSettings.current
    val snapshot by graph.weather.weather.collectAsStateWithLifecycle()
    val error by graph.weather.error.collectAsStateWithLifecycle()
    val tempUnit = settings.weatherUnit
    val needsCity = settings.weatherCity.isBlank() && !settings.weatherUseLocation
    val current = snapshot
    val spoken = if (current != null) {
        "Weather, " + WeatherFormat.convert(current.tempC, tempUnit).toString() + " degrees, " +
            WidgetText.sentenceCase(current.kind.label)
    } else {
        "Weather, no data"
    }
    WidgetSurface(shape = shape, modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().widgetDescription(spoken)) {
            when (shape) {
                WidgetShape.CARD -> WeatherCardBody(snapshot, error, needsCity, tempUnit)
                WidgetShape.CAPSULE -> WeatherCapsuleBody(snapshot)
                WidgetShape.CIRCLE -> WeatherCircleBody(snapshot, tempUnit)
            }
        }
    }
}

/** 4x2 card, proportions of the reference screenshot (20 dp top, 14 dp left, 18 dp line pitch). */
@Composable
private fun WeatherCardBody(
    snapshot: WeatherSnapshot?,
    error: String?,
    needsCity: Boolean,
    tempUnit: WeatherUnit,
) {
    val today by rememberWidgetToday()
    val locale = Locale.getDefault()
    val colors = DotlineTheme.colors
    val kind = if (snapshot != null) snapshot.kind else WeatherKind.UNKNOWN
    val third = when {
        snapshot != null -> WidgetText.dotLine(snapshot.kind.label)
        error != null -> WidgetText.dotLine(error)
        needsCity -> "SET CITY IN SETTINGS"
        else -> "LOADING"
    }.ifEmpty { "--" }
    val lines = remember(today, locale, third) {
        listOf(WidgetText.weekdayFull(today, locale), WidgetText.dayMonthShort(today, locale), third)
    }
    val temp = if (snapshot != null) WeatherFormat.convert(snapshot.tempC, tempUnit).toString() else "--"
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val s = WidgetFit.weatherCardScale(WidgetText.longest(lines), maxWidth.value, maxHeight.value)
        val lineHeight = WidgetFit.dotTextHeight(WidgetFit.FINE_DOT * s, WidgetFit.FINE_GAP * s)
        val spacing = (WidgetFit.FINE_LINE_PITCH * s - lineHeight).coerceAtLeast(0f)
        Column(
            modifier = Modifier.padding(
                start = (WidgetFit.WEATHER_CARD_PAD_X * s).dp,
                top = (20f * s).dp,
            ),
        ) {
            WidgetFineLines(lines = lines, scale = s, color = colors.primary)
            Spacer(Modifier.height(spacing.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherDotIcon(kind = kind, pitch = (1.5f * s).dp, color = colors.primary)
                Spacer(Modifier.width((8f * s).dp))
                DotText(
                    text = temp,
                    dot = (WidgetFit.FINE_DOT * s).dp,
                    gap = (WidgetFit.FINE_GAP * s).dp,
                    color = colors.primary,
                )
                if (snapshot != null) {
                    Spacer(Modifier.width((1.5f * s).dp))
                    DotMatrixIcon(
                        rows = WeatherDegreeRows,
                        pitch = (1.4f * s).dp,
                        color = colors.primary,
                        modifier = Modifier.align(Alignment.Top),
                    )
                }
            }
        }
    }
}

/** 2x1 capsule: pictogram (2 dp pitch, about 28 dp wide) at the left, condition word in 15 sp. */
@Composable
private fun WeatherCapsuleBody(snapshot: WeatherSnapshot?) {
    val colors = DotlineTheme.colors
    val kind = if (snapshot != null) snapshot.kind else WeatherKind.UNKNOWN
    val word = if (snapshot != null) WidgetText.sentenceCase(snapshot.kind.label) else "--"
    val style = DotlineTheme.type.body.copy(
        fontSize = widgetFixedSp(15f),
        lineHeight = widgetFixedSp(18f),
        color = colors.primary,
        textAlign = TextAlign.Center,
    )
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherDotIcon(kind = kind, pitch = 2.dp, color = colors.primary)
        Spacer(Modifier.width(10.dp))
        BasicText(
            text = word,
            modifier = Modifier.weight(1f),
            style = style,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )
    }
}

/** 1x1 circle: "33" and a degree sign in 22 sp. */
@Composable
private fun WeatherCircleBody(snapshot: WeatherSnapshot?, tempUnit: WeatherUnit) {
    val text = if (snapshot != null) WeatherFormat.degrees(snapshot.tempC, tempUnit) else "--"
    val style = DotlineTheme.type.body.copy(
        fontSize = widgetFixedSp(22f),
        lineHeight = widgetFixedSp(26f),
        color = DotlineTheme.colors.primary,
        textAlign = TextAlign.Center,
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(text = text, style = style, maxLines = 1, softWrap = false)
    }
}
