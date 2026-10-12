package com.dotline.launcher.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.LocalReduceMotion
import com.dotline.launcher.ui.theme.flatClickable

/* ------------------------------------------------------------------------------------------
 * Flat controls in the Nothing OS settings style. No ripple, no shadow, no gradient:
 * pressed state is the faint overlay drawn by flatClickable, state changes are one-shot
 * 150 ms ease-out tweens (or an instant snap when the user disabled animations).
 * ------------------------------------------------------------------------------------------ */

private fun controlsClamp01(value: Float): Float {
    if (value.isNaN()) return 0f
    if (value < 0f) return 0f
    if (value > 1f) return 1f
    return value
}

/** Position of [value] inside [range] as 0..1; an empty range or NaN gives 0. */
private fun controlsFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    if (value.isNaN() || span <= 0f) return 0f
    return controlsClamp01((value - range.start) / span)
}

/**
 * Value for a touch at [x] on a slider that is [widthPx] x [heightPx]. The thumb (the rounded end
 * of the filled part) travels from heightPx / 2 to widthPx - heightPx / 2.
 */
private fun controlsValueAt(
    x: Float,
    widthPx: Float,
    heightPx: Float,
    range: ClosedFloatingPointRange<Float>,
): Float {
    val span = range.endInclusive - range.start
    if (span <= 0f) return range.start
    val travel = widthPx - heightPx
    val t = if (travel <= 0f) 0f else controlsClamp01((x - heightPx / 2f) / travel)
    val raw = range.start + span * t
    return if (raw < range.start) range.start else if (raw > range.endInclusive) range.endInclusive else raw
}

/**
 * Pill switch: red (accent) track when on, raised-card track with a tertiary knob when off.
 * The knob slides and the colours cross-fade in 150 ms (instantly when animations are reduced).
 */
@Composable
fun FlatSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = DotlineTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val spec: AnimationSpec<Float> = if (reduceMotion) {
        snap<Float>()
    } else {
        tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)
    }
    val progress = animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spec,
        label = "flatSwitch",
    )
    val offTrack = colors.cardRaised
    val onTrack = colors.accent
    val offKnob = colors.tertiary
    // Light knob on the red track in both themes, derived from theme tokens only.
    val onKnob: Color = if (colors.isDark) colors.primary else colors.onHighlight
    Canvas(
        modifier
            .size(52.dp, 32.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .flatClickable(
                DotlineTheme.shapes.pill,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        val t = progress.value
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = lerp(offTrack, onTrack, t),
            size = size,
            cornerRadius = CornerRadius(h / 2f),
        )
        val pad = 4.dp.toPx()
        val knobRadius = h / 2f - pad
        val left = pad + knobRadius
        val right = w - pad - knobRadius
        drawCircle(
            color = lerp(offKnob, onKnob, t),
            radius = knobRadius,
            center = Offset(left + (right - left) * t, h / 2f),
        )
    }
}

/**
 * Tall pill slider like the Nothing brightness slider: a raised-card track whose left part is
 * filled with the primary colour and ends in a rounded thumb carrying one small dot.
 * Tap or drag horizontally; the reported value is always clamped to [valueRange].
 */
@Composable
fun FlatSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = DotlineTheme.colors
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val latestRange by rememberUpdatedState(valueRange)
    val trackColor = colors.cardRaised
    val fillColor = colors.primary
    val markColor = colors.cardRaised
    val fraction = controlsFraction(value, valueRange)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        latestOnChange(
                            controlsValueAt(offset.x, size.width.toFloat(), size.height.toFloat(), latestRange),
                        )
                        latestOnFinished?.invoke()
                    },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        latestOnChange(
                            controlsValueAt(offset.x, size.width.toFloat(), size.height.toFloat(), latestRange),
                        )
                    },
                    onDragEnd = { latestOnFinished?.invoke() },
                    onDragCancel = { latestOnFinished?.invoke() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        latestOnChange(
                            controlsValueAt(
                                change.position.x,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                latestRange,
                            ),
                        )
                    },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(h / 2f)
        drawRoundRect(color = trackColor, size = size, cornerRadius = corner)
        val travel = if (w > h) w - h else 0f
        val fillWidth = h + travel * fraction
        drawRoundRect(color = fillColor, size = Size(fillWidth, h), cornerRadius = corner)
        drawCircle(
            color = markColor,
            radius = 2.dp.toPx(),
            center = Offset(fillWidth - h / 2f, h / 2f),
        )
    }
}

/**
 * Pill button. Filled: inverted highlight fill with dark text. Not filled: raised-card fill with
 * primary text. Disabled buttons are dimmed and ignore taps.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = DotlineTheme.colors
    val shape = DotlineTheme.shapes.pill
    val fill = if (filled) colors.highlight else colors.cardRaised
    val textColor = if (filled) colors.onHighlight else colors.primary
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 52.dp)
            .background(fill, shape)
            .flatClickable(shape, enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = DotlineTheme.type.bodyMedium.copy(color = textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A group of [SettingsRow]s: one block with 24dp outer corners, rows separated by 2dp gaps that
 * show the canvas through. Rows paint their own fill.
 */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DotlineTheme.shapes.card),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/**
 * One settings row. Normal rows use the card colour; a [highlighted] row is inverted (light fill,
 * dark text on the dark theme). [trailing] is placed at the end of the row, e.g. a [FlatSwitch].
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val fill = if (highlighted) colors.highlight else colors.card
    val titleColor = if (highlighted) colors.onHighlight else colors.primary
    val subtitleColor = if (highlighted) colors.onHighlight.copy(alpha = 0.6f) else colors.secondary
    val clickModifier = if (onClick != null) {
        Modifier.flatClickable(RectangleShape, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(fill)
            .then(clickModifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = type.body.copy(color = titleColor),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                BasicText(
                    text = subtitle,
                    style = type.small.copy(color = subtitleColor),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

/** Small secondary-colour mono label above a [SettingsCard]. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        style = DotlineTheme.type.label.copy(color = DotlineTheme.colors.secondary),
    )
}

/**
 * Screen header: optional back arrow in a 44dp circular press target, then the screen title in
 * the Doto title style. Pads itself below the status bar.
 */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .flatClickable(CircleShape, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                BackArrowIcon()
            }
        }
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = title,
            modifier = Modifier.weight(1f),
            style = DotlineTheme.type.title.copy(color = DotlineTheme.colors.primary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
