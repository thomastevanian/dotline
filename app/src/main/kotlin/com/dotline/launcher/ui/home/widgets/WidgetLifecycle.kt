package com.dotline.launcher.ui.home.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dotline.launcher.home.InfoWidgetMath
import com.dotline.launcher.home.WidgetShape
import com.dotline.launcher.ui.components.DotText
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.flatClickable

/* ------------------------------------------------------------------------------------------
 * Lifecycle helper and shared pieces of the calendar, world clock and notes widgets: the tap
 * area, the 9 sp mono captions, the fine dot-matrix time and the "nothing to show" notice.
 * Everything is flat: no gradient, no shadow, no outline, no ripple.
 * ------------------------------------------------------------------------------------------ */

/**
 * A counter that goes up every time the host screen is RESUMED (the launcher comes back to the
 * front, the screen is unlocked, a permission dialog closes). Widgets key a one-off load on it, so
 * they refresh when the user looks at them and never on a timer.
 *
 * It starts at 0 and is bumped once as soon as the observer is registered, so the first read
 * happens; the ON_RESUME that a lifecycle replays to a new observer is ignored so that first load
 * is not done twice.
 */
@Composable
fun rememberResumeCount(): State<Int> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val count = remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        var primed = false
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (primed && event == Lifecycle.Event.ON_RESUME) {
                    count.value = count.value + 1
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        primed = true
        count.value = count.value + 1
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return count
}

/** Padding of the text inside a card widget (Nothing stats card: 14 to 16 dp at the sides). */
internal val InfoCardPadding: PaddingValues = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp)

/** Padding of the text inside a capsule widget. */
internal val InfoCapsulePadding: PaddingValues = PaddingValues(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 10.dp)

/** Height of one line of a 9 sp mono caption, in dp. */
internal const val INFO_CAPTION_HEIGHT: Float = 12f

/** Font size in sp that is exactly [size] dp tall whatever the user's font scale is. */
@Composable
internal fun infoSp(size: Float): TextUnit {
    val density = LocalDensity.current
    return with(density) { size.dp.toSp() }
}

/** The small mono capital caption (9 sp). */
@Composable
internal fun infoCaptionStyle(color: Color): TextStyle {
    val size = infoSp(9f)
    val line = infoSp(INFO_CAPTION_HEIGHT)
    val base = DotlineTheme.type.caption
    return remember(base, size, line, color) {
        base.copy(fontSize = size, lineHeight = line, color = color)
    }
}

/** Space Grotesk body text of [sizeDp] with a line height of [lineDp]. */
@Composable
internal fun infoBodyStyle(sizeDp: Float, lineDp: Float, color: Color): TextStyle {
    val size = infoSp(sizeDp)
    val line = infoSp(lineDp)
    val base = DotlineTheme.type.body
    return remember(base, size, line, color) {
        base.copy(fontSize = size, lineHeight = line, color = color)
    }
}

/** One line of mono caps caption. */
@Composable
internal fun InfoCaption(text: String, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier,
        style = infoCaptionStyle(color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Space Grotesk text that ends in an ellipsis after [maxLines] lines. */
@Composable
internal fun InfoBodyText(
    text: String,
    sizeDp: Float,
    lineDp: Float,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = infoBodyStyle(sizeDp, lineDp, color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Makes everything inside one accessibility node (the dot matrix is drawn on canvases). */
internal fun Modifier.infoDescription(text: String): Modifier =
    this.semantics(mergeDescendants = true) { contentDescription = text }

/**
 * The whole widget is one tap target with the faint flat press overlay (it is clipped to the
 * widget shape by the surface). A long press is swallowed so that picking a widget up to move it
 * never also counts as a tap.
 */
@Composable
internal fun InfoWidgetTapArea(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .flatClickable(RectangleShape, onLongClick = {}, onClick = onClick),
        content = content,
    )
}

/**
 * A time such as "14:30" or "2:30 PM" in fine dot-matrix. [dot] is the dot diameter in dp, the gap
 * is a third of it (3 dp dots with 1 dp gaps at the largest size). The AM / PM mark is drawn with
 * smaller dots on the baseline.
 */
@Composable
internal fun InfoDotTime(time: String, dot: Float, color: Color, modifier: Modifier = Modifier) {
    val parts = remember(time) { InfoWidgetMath.splitTime(time) }
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        DotText(
            text = parts.first,
            dot = dot.dp,
            gap = (dot * InfoWidgetMath.GAP_RATIO).dp,
            color = color,
        )
        val suffix = parts.second
        if (suffix != null) {
            val small = dot * InfoWidgetMath.SUFFIX_SCALE
            Spacer(Modifier.width((dot * InfoWidgetMath.SUFFIX_SPACE).dp))
            DotText(
                text = suffix,
                dot = small.dp,
                gap = (small * InfoWidgetMath.GAP_RATIO).dp,
                color = color,
            )
        }
    }
}

/** A column of tiny round dots (the vertical twin of DottedDivider), as high as the caller makes it. */
@Composable
internal fun InfoVerticalDots(modifier: Modifier = Modifier) {
    val color = DotlineTheme.colors.outline
    Canvas(modifier.width(2.dp)) {
        val diameter = 1.5.dp.toPx()
        val pitch = 4.dp.toPx()
        val radius = diameter / 2f
        if (size.height >= diameter) {
            val count = ((size.height - diameter) / pitch).toInt() + 1
            val used = (count - 1) * pitch + diameter
            val start = (size.height - used) / 2f + radius
            val cx = size.width / 2f
            for (i in 0 until count) {
                drawCircle(color = color, radius = radius, center = Offset(cx, start + i * pitch))
            }
        }
    }
}

/**
 * The widget has nothing to show (calendar permission missing, no events, no cities, empty note).
 * Card: [caption] at the top, an optional dim dot-matrix [placeholder] and [message] at the bottom.
 * Capsule: caption over message, centred vertically. A circle only shows the caption.
 */
@Composable
internal fun InfoNotice(
    shape: WidgetShape,
    caption: String,
    message: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    messageIsCaption: Boolean = false,
) {
    val colors = DotlineTheme.colors
    when (shape) {
        WidgetShape.CARD -> {
            BoxWithConstraints(modifier.fillMaxSize().padding(InfoCardPadding)) {
                val innerW = maxWidth.value
                Column(Modifier.fillMaxSize()) {
                    InfoCaption(caption, colors.secondary)
                    if (placeholder != null) {
                        Spacer(Modifier.height(10.dp))
                        val dot = InfoWidgetMath.fitTextDot(placeholder, innerW, 3f)
                        DotText(
                            text = placeholder,
                            dot = dot.dp,
                            gap = (dot * InfoWidgetMath.GAP_RATIO).dp,
                            color = colors.tertiary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (messageIsCaption) {
                        InfoCaption(message, colors.secondary)
                    } else {
                        InfoBodyText(message, 16f, 21f, colors.primary, 2)
                    }
                }
            }
        }
        WidgetShape.CAPSULE -> {
            Column(
                modifier = modifier.fillMaxSize().padding(InfoCapsulePadding),
                verticalArrangement = Arrangement.Center,
            ) {
                InfoCaption(caption, colors.secondary)
                Spacer(Modifier.height(2.dp))
                if (messageIsCaption) {
                    InfoCaption(message, colors.secondary)
                } else {
                    InfoBodyText(message, 14f, 19f, colors.primary, 1)
                }
            }
        }
        WidgetShape.CIRCLE -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InfoCaption(caption, colors.secondary)
            }
        }
    }
}
