package com.dotline.launcher.ui.home.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.dotline.launcher.data.weather.WeatherDots
import com.dotline.launcher.data.weather.WeatherKind
import com.dotline.launcher.home.WidgetFit
import com.dotline.launcher.home.WidgetShape
import com.dotline.launcher.ui.components.DotText
import com.dotline.launcher.ui.components.rememberNow
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.flatClickable
import java.time.LocalDate

/* ------------------------------------------------------------------------------------------
 * Shared pieces of the built-in Nothing OS widgets: the flat surface (card, capsule, circle),
 * dot-matrix icons and the actions a widget can trigger. Strictly flat: no gradient, no shadow,
 * no outline, no ripple.
 * ------------------------------------------------------------------------------------------ */

private val WidgetCardShape: Shape = RoundedCornerShape(20.dp)
private val WidgetCapsuleShape: Shape = RoundedCornerShape(percent = 50)

/** The clip shape of a widget family: card 20 dp corners, capsule fully rounded, circle round. */
fun widgetShapeFor(shape: WidgetShape): Shape = when (shape) {
    WidgetShape.CARD -> WidgetCardShape
    WidgetShape.CAPSULE -> WidgetCapsuleShape
    WidgetShape.CIRCLE -> CircleShape
}

/**
 * Makes everything inside a widget one accessibility node with [text] as its description (the dot
 * matrix is drawn on canvases that a screen reader cannot read).
 */
fun Modifier.widgetDescription(text: String): Modifier =
    this.semantics(mergeDescendants = true) { contentDescription = text }

/**
 * Actions a widget can ask the home screen to perform. The home screen provides the real ones
 * through [LocalWidgetActions]; the defaults do nothing so a widget also works in a preview.
 */
@Immutable
class WidgetActions(
    val requestCalendarPermission: () -> Unit = {},
    val openClockApp: () -> Unit = {},
    val openCalendarApp: () -> Unit = {},
    val editNote: () -> Unit = {},
)

val LocalWidgetActions = staticCompositionLocalOf { WidgetActions() }

/**
 * The flat widget surface filled with colors.widget. Cards fill the box the home grid gives them
 * (20 dp corners), a capsule is fully rounded and at most 66 dp high, a circle is round and at most
 * 66 dp wide; capsules and circles are centred in their cells. No outline, no shadow.
 */
@Composable
fun WidgetSurface(
    shape: WidgetShape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    WidgetSurfaceImpl(shape = shape, modifier = modifier, onClick = null, content = content)
}

/**
 * Same surface, but the whole visible shape is clickable with the faint flat press overlay. A long
 * press is swallowed (it never counts as a click), so a user who long-presses a widget to move it
 * does not open an app when the finger lifts.
 */
@Composable
fun ClickableWidgetSurface(
    shape: WidgetShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    WidgetSurfaceImpl(shape = shape, modifier = modifier, onClick = onClick, content = content)
}

@Composable
private fun WidgetSurfaceImpl(
    shape: WidgetShape,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val clipShape = widgetShapeFor(shape)
    val fill = DotlineTheme.colors.widget
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val box = WidgetFit.surface(shape, maxWidth.value, maxHeight.value)
        var surface: Modifier = Modifier.size(box.width.dp, box.height.dp)
        if (onClick != null) {
            surface = surface.flatClickable(clipShape, onLongClick = {}, onClick = onClick)
        }
        Box(modifier = surface.clip(clipShape).background(fill), content = content)
    }
}

/**
 * A dot-matrix picture: every '#' in [rows] is a round dot, every other character is an empty cell.
 * [pitch] is the distance between dot centres, the dot diameter is 0.78 x pitch.
 */
@Composable
fun DotMatrixIcon(
    rows: List<String>,
    pitch: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var cols = 0
    for (r in rows) {
        if (r.length > cols) cols = r.length
    }
    val rowCount = rows.size
    val dotFraction = 0.78f
    val widthDp = if (cols == 0) 0.dp else pitch * (cols - 1) + pitch * dotFraction
    val heightDp = if (rowCount == 0) 0.dp else pitch * (rowCount - 1) + pitch * dotFraction
    Canvas(modifier.size(widthDp, heightDp)) {
        val pitchPx = pitch.toPx()
        val radius = pitchPx * dotFraction / 2f
        for (row in 0 until rowCount) {
            val line = rows[row]
            for (col in 0 until line.length) {
                if (line[col] == '#') {
                    drawCircle(color, radius, Offset(radius + col * pitchPx, radius + row * pitchPx))
                }
            }
        }
    }
}

/** The 14 x 10 dot-matrix weather pictogram of [kind]. */
@Composable
fun WeatherDotIcon(
    kind: WeatherKind,
    pitch: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    DotMatrixIcon(rows = WeatherDots.rows(kind), pitch = pitch, color = color, modifier = modifier)
}

/**
 * Lines of VERY FINE dot-matrix text as on the Nothing date and weather widgets: at [scale] 1 the
 * dots are 1.0 dp, the gaps 0.4 dp and the lines 18 dp apart. Lines are left aligned.
 */
@Composable
fun WidgetFineLines(
    lines: List<String>,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    color: Color = DotlineTheme.colors.primary,
) {
    val dot = (WidgetFit.FINE_DOT * scale).dp
    val gap = (WidgetFit.FINE_GAP * scale).dp
    val lineHeight = WidgetFit.dotTextHeight(WidgetFit.FINE_DOT * scale, WidgetFit.FINE_GAP * scale)
    val spacing = (WidgetFit.FINE_LINE_PITCH * scale - lineHeight).coerceAtLeast(0f).dp
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.Start,
    ) {
        for (line in lines) {
            DotText(text = line, dot = dot, gap = gap, color = color)
        }
    }
}

/**
 * Today's date as state. It is derived from the minute clock, so it only changes (and only
 * recomposes its readers) when the day changes, not every minute.
 */
@Composable
fun rememberWidgetToday(): State<LocalDate> {
    val now = rememberNow()
    return remember(now) { derivedStateOf { now.value.toLocalDate() } }
}

/**
 * Font size in sp that is exactly [size] dp tall regardless of the user's font scale. Capsules and
 * circles have a fixed size, so their text must not grow with the accessibility font size.
 */
@Composable
fun widgetFixedSp(size: Float): TextUnit {
    val density = LocalDensity.current
    return with(density) { size.dp.toSp() }
}
