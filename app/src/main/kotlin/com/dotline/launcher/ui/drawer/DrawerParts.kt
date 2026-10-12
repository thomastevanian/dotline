package com.dotline.launcher.ui.drawer

import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dotline.launcher.data.icons.AppIconView
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.drawer.DrawerLogic
import com.dotline.launcher.home.FRect
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.CloseIcon
import com.dotline.launcher.ui.components.SearchIcon
import com.dotline.launcher.ui.home.HomeBounds
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.flatClickable
import kotlin.math.roundToInt

/** Icon size in the drawer before the user's icon size setting is applied. */
internal val DrawerIconBase = 56.dp

private val CellPadV = 8.dp
private val CellPadH = 4.dp
private val CellPressShape = 16.dp

/** Where an app was long-pressed: the app, its icon tile (root px) and the tile size. */
internal class DrawerPress(val app: AppInfo, val tile: FRect)

/**
 * One app in the drawer grid. Tap launches, a long press opens the app menu, and moving the finger
 * after the long press lifts the app out of the drawer so it can be dropped on the home screen.
 */
@Composable
internal fun DrawerAppCell(
    app: AppInfo,
    hasDot: Boolean,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onMenu: (DrawerPress) -> Unit,
    onDragOut: (DrawerPress) -> Unit,
) {
    val settings = LocalSettings.current
    val density = LocalDensity.current
    val colors = DotlineTheme.colors
    val holder = remember { HomeBounds() }
    val tilePx = with(density) { (DrawerIconBase * settings.iconSize).toPx() }
    val padTopPx = with(density) { CellPadV.toPx() }
    val cornerPx = with(density) { CellPressShape.toPx() }
    var pressed by remember { mutableStateOf(false) }
    val latestLaunch by rememberUpdatedState(onLaunch)
    val latestMenu by rememberUpdatedState(onMenu)
    val latestDrag by rememberUpdatedState(onDragOut)
    val overlay = colors.pressOverlay
    val describe: Modifier = if (settings.showLabels) {
        Modifier
    } else {
        Modifier.semantics { contentDescription = app.label }
    }

    fun press(): DrawerPress? {
        val c = holder.coordinates ?: return null
        if (!c.isAttached) return null
        val origin = c.localToRoot(Offset.Zero)
        val left = origin.x + (c.size.width - tilePx) / 2f
        val top = origin.y + padTopPx
        return DrawerPress(app, FRect(left, top, left + tilePx, top + tilePx))
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AppIconView(
            app = app,
            modifier = Modifier
                .onGloballyPositioned { holder.coordinates = it }
                .then(describe)
                .drawWithContent {
                    drawContent()
                    if (pressed) drawRoundRect(overlay, cornerRadius = CornerRadius(cornerPx, cornerPx))
                }
                .pointerInput(app.key) {
                    cellGestures(
                        onPressed = { pressed = it },
                        onTap = { latestLaunch(app, holder.tileInWindow(tilePx, padTopPx)) },
                        onLongPress = { press()?.let { latestMenu(it) } },
                        onDragOut = { press()?.let { latestDrag(it) } },
                    )
                }
                .padding(horizontal = CellPadH, vertical = CellPadV),
            iconSize = DrawerIconBase,
            showLabel = settings.showLabels,
            notificationDot = hasDot,
        )
    }
}

/**
 * Tap, long press, and "long press then move" for one cell. Nothing is consumed until the long press
 * has fired, so a vertical swipe still scrolls the grid.
 */
private suspend fun PointerInputScope.cellGestures(
    onPressed: (Boolean) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragOut: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPressed(true)
        val longPress = awaitLongPressOrCancellation(down.id)
        onPressed(false)
        if (longPress != null) {
            onLongPress()
            val slop = viewConfiguration.touchSlop * 2f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
                if ((change.position - longPress.position).getDistance() > slop) {
                    onDragOut()
                    break
                }
            }
        } else {
            val change = currentEvent.changes.firstOrNull { it.id == down.id }
            if (change != null && change.changedToUp()) onTap()
        }
    }
}

/** The flat pill at the bottom: magnifier, "Search" hint, the typed text and a clear button. */
@Composable
internal fun DrawerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val pill = DotlineTheme.shapes.pill
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(colors.searchPill, pill)
            .border(1.dp, colors.outline, pill)
            .padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchIcon(color = colors.secondary)
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                BasicText(text = "Search", style = type.body.copy(color = colors.tertiary))
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = type.body.copy(color = colors.primary),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = "Clear search" }
                    .flatClickable(CircleShape, onClick = { onQueryChange("") }),
                contentAlignment = Alignment.Center,
            ) {
                CloseIcon(color = colors.secondary)
            }
        }
    }
}

private val ScrollerWidth = 28.dp
private val BubbleSize = 64.dp
private val ScrollerPadV = 8.dp

/** Width the grid should leave free for the scroller. */
internal val DrawerScrollerGutter = ScrollerWidth

/**
 * Thin A-Z scroller on the right edge in the small mono face. Touching or dragging along it calls
 * [onLetter]; a round indicator with the big dot-matrix letter floats beside the finger while it is down.
 */
@Composable
internal fun DrawerScroller(
    letters: List<Char>,
    available: Set<Char>,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    var active: Char? by remember { mutableStateOf<Char?>(null) }
    var touchY by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0) }
    val latestLetters by rememberUpdatedState(letters)
    val latestOnLetter by rememberUpdatedState(onLetter)
    val latestAvailable by rememberUpdatedState(available)

    Box(modifier = modifier.fillMaxHeight().width(ScrollerWidth + BubbleSize + 16.dp)) {
        // The letter column.
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(ScrollerWidth)
                .fillMaxHeight()
                .padding(vertical = ScrollerPadV)
                .onSizeChanged { heightPx = it.height }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun handle(y: Float) {
                            touchY = y
                            val letter = DrawerLogic.letterAt(y, size.height.toFloat(), latestLetters)
                            if (letter != active) {
                                active = letter
                                latestOnLetter(letter)
                            }
                        }
                        down.consume()
                        handle(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            handle(change.position.y)
                        }
                        active = null
                    }
                },
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (letter in letters) {
                val isOn = letter in latestAvailable
                BasicText(
                    text = letter.toString(),
                    style = type.caption.copy(
                        color = if (active == letter) colors.primary else if (isOn) colors.secondary else colors.outline,
                    ),
                )
            }
        }
        // The floating indicator.
        val shown = active
        if (shown != null && heightPx > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val y = (touchY + ScrollerPadV.toPx() - BubbleSize.toPx() / 2f).roundToInt()
                        IntOffset(0, y.coerceAtLeast(0))
                    }
                    .size(BubbleSize)
                    .background(colors.cardRaised, CircleShape)
                    .border(1.dp, colors.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(text = shown.toString(), style = type.heading.copy(color = colors.primary))
            }
        }
    }
}

/** Flat card shape used by the drawer's long-press menu. */
internal val DrawerMenuRowShape = RoundedCornerShape(16.dp)
