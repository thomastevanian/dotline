package com.dotline.launcher.ui.home

import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.dotline.launcher.data.icons.AppIconView
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.theme.flatClickable
import kotlin.math.roundToInt

/* ------------------------------------------------------------------------------------------
 * Stage 1 home contents: apps laid out alphabetically on fixed grid pages plus the dock.
 * Stage 2 replaces buildHomeAppLists() with the persisted layout; AppGridPage and AppCell stay.
 * ------------------------------------------------------------------------------------------ */

/** Grid rows at the top of page 0 that are kept free for the clock block. */
private const val CLOCK_RESERVED_ROWS = 2

/** Smallest grid height (in rows) that still leaves room for the clock block. */
private const val MIN_ROWS_FOR_CLOCK = 5

/** Rows of page 0 to leave free for the clock block: none when the clock is hidden or the grid is short. */
internal fun clockReservedRows(hideClock: Boolean, gridRows: Int): Int {
    return if (!hideClock && gridRows >= MIN_ROWS_FOR_CLOCK) CLOCK_RESERVED_ROWS else 0
}

/**
 * Everything the Stage 1 home screen shows, grouped once per change of the inputs.
 * [pages] are the grid pages (at least one, possibly empty), [dock] the pinned apps in slot order and
 * [drawer] every non-hidden app (alphabetical) for the All apps overlay.
 */
@Immutable
internal class HomeAppLists(
    val pages: List<List<AppInfo>>,
    val dock: List<AppInfo>,
    val drawer: List<AppInfo>,
)

/** Preferred dock apps by slot: phone, messages, browser, camera. First installed package wins. */
private val DockPreferences: List<List<String>> = listOf(
    listOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer"),
    listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
    listOf("com.android.chrome", "com.sec.android.app.sbrowser"),
    listOf("com.sec.android.app.camera", "com.android.camera"),
)

private fun isHiddenApp(app: AppInfo, hidden: Set<String>): Boolean {
    return hidden.contains(app.key.flat) || hidden.contains(app.packageName)
}

/**
 * The dock apps for [visible] (alphabetical). Each slot takes the first installed app of its
 * preference list (personal profile before work profile); slots without a match are filled with the
 * first apps alphabetically that are not already pinned.
 */
private fun pickDockApps(visible: List<AppInfo>): List<AppInfo> {
    val slots = arrayOfNulls<AppInfo>(HomeLayout.DOCK_SLOTS)
    val used = HashSet<AppKey>()
    val preferenceSlots = minOf(slots.size, DockPreferences.size)
    for (slot in 0 until preferenceSlots) {
        for (packageName in DockPreferences[slot]) {
            val match: AppInfo? = visible.firstOrNull { it.packageName == packageName && !it.isWorkProfile && !used.contains(it.key) }
                ?: visible.firstOrNull { it.packageName == packageName && !used.contains(it.key) }
            if (match != null) {
                slots[slot] = match
                used.add(match.key)
                break
            }
        }
    }
    var next = 0
    for (slot in slots.indices) {
        if (slots[slot] != null) continue
        while (next < visible.size && used.contains(visible[next].key)) next++
        if (next >= visible.size) break
        val fallback = visible[next]
        slots[slot] = fallback
        used.add(fallback.key)
        next++
    }
    return slots.filterNotNull()
}

/**
 * Splits [apps] (already alphabetical, as published by the app repository) into grid pages.
 * Hidden apps are dropped (matched by [AppKey.flat] or package name); when [showDock] is on the dock
 * apps are taken out of the grid. Page 0 leaves [reservedTopRows] rows free. At most
 * [HomeLayout.MAX_PAGES] pages; apps that do not fit stay reachable in the All apps overlay.
 * No sorting happens here, so it is cheap enough to run during composition.
 */
internal fun buildHomeAppLists(
    apps: List<AppInfo>,
    hidden: Set<String>,
    showDock: Boolean,
    columns: Int,
    rows: Int,
    reservedTopRows: Int,
): HomeAppLists {
    val visible: List<AppInfo> = if (hidden.isEmpty()) apps else apps.filter { !isHiddenApp(it, hidden) }
    val dock: List<AppInfo> = if (showDock) pickDockApps(visible) else emptyList<AppInfo>()
    val grid: List<AppInfo> = if (dock.isEmpty()) {
        visible
    } else {
        val dockKeys = HashSet<AppKey>()
        for (app in dock) dockKeys.add(app.key)
        visible.filter { !dockKeys.contains(it.key) }
    }

    val pages = ArrayList<List<AppInfo>>()
    var index = 0
    var page = 0
    while (page < HomeLayout.MAX_PAGES && index < grid.size) {
        val freeRows = if (page == 0) rows - reservedTopRows else rows
        val capacity = columns * freeRows
        if (capacity <= 0) break
        val end = minOf(index + capacity, grid.size)
        pages.add(grid.subList(index, end).toList())
        index = end
        page++
    }
    if (pages.isEmpty()) pages.add(emptyList<AppInfo>())
    return HomeAppLists(pages = pages, dock = dock, drawer = visible)
}

/** Press overlay shape of an icon cell: rounded 16dp. */
private val CellShape = RoundedCornerShape(16.dp)

/** Remembers where an icon was last placed so launch and menu can use its window bounds. */
internal class BoundsHolder {
    var coordinates: LayoutCoordinates? = null

    /** Bounds in window pixels, or null while the icon is not on screen. Computed on demand only. */
    fun windowBounds(): AndroidRect? {
        val c = coordinates ?: return null
        if (!c.isAttached) return null
        val r = c.boundsInWindow()
        return AndroidRect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
    }
}

/**
 * One fixed grid page: [columns] x [rows] equally sized cells filling the available space, the apps
 * placed row by row. The first [reservedTopRows] rows are left free (page 0 draws the clock there).
 * A plain Layout with manual placement; no lazy grid, so nothing is re-measured while paging.
 */
@Composable
fun AppGridPage(
    apps: List<AppInfo>,
    columns: Int,
    rows: Int,
    reservedTopRows: Int,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onLongPress: (AppInfo, AndroidRect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeColumns = if (columns < 1) 1 else columns
    val safeRows = if (rows < 1) 1 else rows
    val skippedCells = reservedTopRows.coerceIn(0, safeRows) * safeColumns
    Layout(
        content = {
            for (app in apps) {
                key(app.key.flat) {
                    AppCell(app = app, onLaunch = onLaunch, onLongPress = onLongPress)
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val cellWidth = width / safeColumns
        val cellHeight = height / safeRows
        val startX = (width - cellWidth * safeColumns) / 2
        val cellConstraints = Constraints.fixed(cellWidth, cellHeight)
        val placeables = measurables.map { it.measure(cellConstraints) }
        layout(width, height) {
            for (i in placeables.indices) {
                val slot = i + skippedCells
                placeables[i].place(startX + (slot % safeColumns) * cellWidth, (slot / safeColumns) * cellHeight)
            }
        }
    }
}

/**
 * A tappable app icon: [AppIconView] with the flat press overlay. Tap launches, long press asks the
 * caller to open the app menu; both get the icon's window bounds as the launch animation source.
 * Centres itself in whatever size it is given.
 */
@Composable
internal fun AppCell(
    app: AppInfo,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onLongPress: (AppInfo, AndroidRect?) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val holder = remember { BoundsHolder() }
    val labelVisible = showLabel && LocalSettings.current.showLabels
    val describe: Modifier = if (labelVisible) {
        Modifier
    } else {
        Modifier.semantics { contentDescription = app.label }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppIconView(
            app = app,
            modifier = Modifier
                .onGloballyPositioned { holder.coordinates = it }
                .then(describe)
                .flatClickable(
                    shape = CellShape,
                    onLongClick = { onLongPress(app, holder.windowBounds()) },
                    onClick = { onLaunch(app, holder.windowBounds()) },
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
            showLabel = showLabel,
        )
    }
}
