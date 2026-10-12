package com.dotline.launcher.ui.drawer

import android.graphics.Rect as AndroidRect
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotline.launcher.data.AppShortcut
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.home.HomeLayoutMath
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.components.DottedDivider
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.flatClickable
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MenuWidth = 232.dp
private const val MAX_SHORTCUTS = 4

private class MenuEntry(val text: String, val onClick: () -> Unit)

/**
 * The drawer's long-press popup: a flat 24 dp card next to the pressed icon on a faint scrim with
 * the app's shortcuts (loaded off the main thread), App info, Uninstall, Add to home screen and
 * Hide app. Tapping the scrim closes it.
 */
@Composable
internal fun DrawerMenu(
    press: DrawerPress,
    onDismiss: () -> Unit,
    onAddToHome: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val colors = DotlineTheme.colors
    val app = press.app

    val shortcuts: List<AppShortcut> by produceState(initialValue = emptyList<AppShortcut>(), app.key) {
        value = withContext(Dispatchers.Default) { graph.apps.shortcuts(app.key).take(MAX_SHORTCUTS) }
    }
    val bounds = remember(press) {
        AndroidRect(press.tile.left.toInt(), press.tile.top.toInt(), press.tile.right.toInt(), press.tile.bottom.toInt())
    }
    val entries = remember(app, shortcuts) {
        val out = ArrayList<MenuEntry>()
        for (shortcut in shortcuts) {
            out.add(
                MenuEntry(shortcut.label) {
                    onDismiss()
                    graph.apps.launchShortcut(shortcut, bounds)
                },
            )
        }
        out.add(
            MenuEntry("App info") {
                onDismiss()
                graph.apps.openAppInfo(app.key, bounds)
            },
        )
        out.add(
            MenuEntry("Uninstall") {
                onDismiss()
                graph.apps.requestUninstall(app.key)
            },
        )
        out.add(
            MenuEntry("Add to home screen") {
                onDismiss()
                onAddToHome(app)
            },
        )
        out.add(
            MenuEntry("Hide app") {
                onDismiss()
                graph.settings.update { it.copy(hiddenApps = it.hiddenApps + app.key.flat) }
                Toast.makeText(context, "Hidden. Show it again in Settings > Drawer.", Toast.LENGTH_LONG).show()
            },
        )
        out
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
    ) {
        Placement(press = press) {
            val shape = DotlineTheme.shapes.card
            Column(
                modifier = Modifier
                    .width(MenuWidth)
                    .background(colors.card, shape)
                    .border(1.dp, colors.outline, shape)
                    // Taps on the card's own padding must not close the menu.
                    .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                    .padding(vertical = 8.dp),
            ) {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) DottedDivider(Modifier.padding(horizontal = 20.dp))
                    MenuRow(text = entry.text, onClick = entry.onClick)
                }
            }
        }
    }
}

/** Places its single child next to the pressed icon, clamped to the screen. */
@Composable
private fun Placement(press: DrawerPress, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val card = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val pos = HomeLayoutMath.menuPosition(
            anchor = press.tile,
            menuW = card.width.toFloat(),
            menuH = card.height.toFloat(),
            screenW = width.toFloat(),
            screenH = height.toFloat(),
            margin = 16.dp.toPx(),
            gap = 8.dp.toPx(),
        )
        layout(width, height) {
            card.place(pos.x.roundToInt(), pos.y.roundToInt())
        }
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    val colors = DotlineTheme.colors
    val baseStyle = DotlineTheme.type.bodyMedium
    val style = remember(baseStyle, colors.primary) { baseStyle.copy(color = colors.primary) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .flatClickable(shape = DrawerMenuRowShape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        BasicText(text = text, style = style, maxLines = 1)
    }
}
