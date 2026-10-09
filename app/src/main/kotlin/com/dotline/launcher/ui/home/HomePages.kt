package com.dotline.launcher.ui.home

import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotline.launcher.data.icons.AppIconView
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.FolderItem
import com.dotline.launcher.data.model.HomeItem
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.HostedWidgetItem
import com.dotline.launcher.data.model.Placement
import com.dotline.launcher.data.model.WidgetItem
import com.dotline.launcher.home.FRect
import com.dotline.launcher.home.HomeLayoutMath
import com.dotline.launcher.service.NotificationDots
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.DotPageIndicator
import com.dotline.launcher.ui.components.SearchIcon
import com.dotline.launcher.ui.home.widgets.BuiltinWidgetView
import com.dotline.launcher.ui.home.widgets.HostedWidgetView
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.flatClickable

/* ------------------------------------------------------------------------------------------
 * Rendering of the home pages: the icon grid, the dock, the search pill and the page dots.
 *
 * Everything here is positioned from measured sizes (a custom Layout, no per-item offsets) and
 * reads no per-frame state: the dragged item is hidden from a draw lambda, so a drag never
 * recomposes the grid, the dock or the widgets.
 * ------------------------------------------------------------------------------------------ */

/** Fixed dimensions of the Nothing OS home screen (see NOTHING_SPEC.md). */
internal object HomeDims {
    /** Horizontal margin of the icon grid. */
    val GridMargin: Dp = 13.dp

    /** Grid cells are never taller than this; the grid is anchored to the top of the page area. */
    val MaxCellHeight: Dp = 112.dp

    /** Gap between a widget and the edge of the cells it spans. */
    val WidgetInset: Dp = 3.dp

    /** Horizontal margin of the dock row (4 equal slots). */
    val DockMargin: Dp = 20.dp

    /** Space above and below the dock icons. */
    val DockVerticalPad: Dp = 10.dp

    /** Space between the dock row and the search pill, on top of the dock's own padding. */
    val DockToSearchGap: Dp = 14.dp

    val SearchMargin: Dp = 21.dp
    val SearchHeight: Dp = 46.dp

    /** Space under the search pill (or under the dock when the pill is hidden). */
    val BottomGap: Dp = 12.dp

    /** Width of the left / right bands that scroll to the next page while dragging. */
    val EdgeBand: Dp = 36.dp

    /** Gap between the icon and its label, and the height of the label line. */
    val LabelGap: Dp = 6.dp
    val LabelLine: Dp = 14.dp

    val ChromeTopGap: Dp = 8.dp
    val ChromeHeight: Dp = 44.dp

    val ZoneMargin: Dp = 16.dp
    val ZoneGap: Dp = 8.dp
    val ZoneHeight: Dp = 44.dp
    val ZoneHitBelow: Dp = 24.dp
}

/** Rounded press area of an icon cell. */
private val HomeCellShape = RoundedCornerShape(16.dp)

/** Compose rect (root pixels) to the plain rect the pure home logic uses. */
internal fun Rect.toFRect(): FRect = FRect(left, top, right, bottom)

/** Apps of the launcher by key, for resolving the keys stored in the home layout. */
@Immutable
internal class AppIndex(private val byKey: Map<AppKey, AppInfo>) {
    operator fun get(key: AppKey): AppInfo? = byKey[key]
}

/** Everything an item needs to draw itself and react to a tap. Recreated when any input changes. */
@Stable
internal class HomeRender(
    val apps: AppIndex,
    /** Icon size before the user's icon size setting is applied. */
    val tileBase: Dp,
    val editMode: Boolean,
    /** "package#userSerial" of apps that currently have a notification (always empty unless enabled). */
    val dots: State<Set<String>>,
    val onLaunch: (AppInfo, AndroidRect?) -> Unit,
    val onOpenFolder: (String) -> Unit,
)

/** Remembers where an item is on screen so a launch can animate out of its icon. */
internal class HomeBounds {
    var coordinates: LayoutCoordinates? = null

    /** Bounds in window pixels, or null while the item is not on screen. Computed on demand only. */
    fun windowBounds(): AndroidRect? {
        val c = coordinates ?: return null
        if (!c.isAttached) return null
        val r = c.boundsInWindow()
        return AndroidRect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
    }
}

// ---------------------------------------------------------------------------------------------
// Items
// ---------------------------------------------------------------------------------------------

/**
 * Draws one home item filling the box it is given: an app (icon + label), a folder (lighter circle +
 * label) or a widget (inset by [HomeDims.WidgetInset]). [interactive] is false for the drag preview.
 */
@Composable
internal fun HomeItemContent(
    item: HomeItem,
    render: HomeRender,
    showLabel: Boolean,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is AppItem -> {
            val app = render.apps[item.app]
            if (app != null) {
                HomeAppItem(app, render, showLabel, interactive, modifier)
            }
        }
        is FolderItem -> HomeFolderItem(item, render, showLabel, interactive, modifier)
        is WidgetItem -> Box(modifier.padding(HomeDims.WidgetInset)) {
            BuiltinWidgetView(item = item, modifier = Modifier.fillMaxSize())
        }
        is HostedWidgetItem -> Box(modifier.padding(HomeDims.WidgetInset)) {
            HostedWidgetView(item = item, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun HomeAppItem(
    app: AppInfo,
    render: HomeRender,
    showLabel: Boolean,
    interactive: Boolean,
    modifier: Modifier,
) {
    val holder = remember { HomeBounds() }
    val dotKey = remember(app.key) { NotificationDots.key(app.packageName, app.key.userSerial) }
    val hasDot = render.dots.value.contains(dotKey)
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
                    shape = HomeCellShape,
                    enabled = interactive && !render.editMode,
                    onClick = { render.onLaunch(app, holder.windowBounds()) },
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
            iconSize = render.tileBase,
            showLabel = showLabel,
            notificationDot = hasDot,
        )
    }
}

@Composable
private fun HomeFolderItem(
    item: FolderItem,
    render: HomeRender,
    showLabel: Boolean,
    interactive: Boolean,
    modifier: Modifier,
) {
    val settings = LocalSettings.current
    val colors = DotlineTheme.colors
    val folderApps = remember(item.apps, render.apps) { item.apps.mapNotNull { render.apps[it] } }
    val baseStyle = DotlineTheme.type.iconLabel
    val labelStyle = remember(baseStyle, colors.primary) {
        baseStyle.copy(color = colors.primary, textAlign = TextAlign.Center)
    }
    val labelVisible = showLabel && settings.showLabels
    val describe: Modifier = if (labelVisible) {
        Modifier
    } else {
        Modifier.semantics { contentDescription = item.name }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .then(describe)
                .semantics { role = Role.Button }
                .flatClickable(
                    shape = HomeCellShape,
                    enabled = interactive,
                    onClick = { render.onOpenFolder(item.id) },
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FolderTile(apps = folderApps, size = render.tileBase * settings.iconSize)
            if (labelVisible) {
                Spacer(Modifier.height(HomeDims.LabelGap))
                BasicText(
                    text = item.name,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    style = labelStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Page grid
// ---------------------------------------------------------------------------------------------

/**
 * One home page. Cells come from [HomeLayoutMath.gridMetrics] (the same maths the drag geometry uses),
 * every item is measured to exactly the cells it spans and placed once. Apps that are not (or no
 * longer) installed are skipped. The item with id [hiddenId] stays composed but is not drawn while it
 * is being dragged, so its pointer stream never breaks.
 */
@Composable
internal fun PageGrid(
    items: List<HomeItem>,
    render: HomeRender,
    cols: Int,
    rows: Int,
    hiddenId: () -> String?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { HomeDims.GridMargin.toPx() }
    val maxCellHeightPx = with(density) { HomeDims.MaxCellHeight.toPx() }
    Layout(
        content = {
            for (item in items) {
                key(item.id) {
                    if (item !is AppItem || render.apps[item.app] != null) {
                        Box(
                            modifier = Modifier
                                .layoutId(item.placement)
                                .drawWithContent {
                                    if (hiddenId() != item.id) drawContent()
                                },
                        ) {
                            HomeItemContent(
                                item = item,
                                render = render,
                                showLabel = true,
                                interactive = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val m = HomeLayoutMath.gridMetrics(width.toFloat(), height.toFloat(), cols, rows, marginPx, maxCellHeightPx)
        val placeables = ArrayList<Placeable>(measurables.size)
        val xs = ArrayList<Int>(measurables.size)
        val ys = ArrayList<Int>(measurables.size)
        for (measurable in measurables) {
            val p = measurable.layoutId as? Placement ?: continue
            val left = m.xEdge(p.col)
            val top = m.yEdge(p.row)
            val right = m.xEdge(p.col + p.spanX)
            val bottom = m.yEdge(p.row + p.spanY)
            val w = (right - left).coerceAtLeast(0)
            val h = (bottom - top).coerceAtLeast(0)
            placeables.add(measurable.measure(Constraints.fixed(w, h)))
            xs.add(left)
            ys.add(top)
        }
        layout(width, height) {
            for (i in placeables.indices) {
                placeables[i].place(xs[i], ys[i])
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dock, page dots, search pill
// ---------------------------------------------------------------------------------------------

/**
 * The dock: [HomeLayout.DOCK_SLOTS] equal slots, no panel behind them, no labels. Its bounds are
 * reported to [metrics] (and cleared when the dock leaves the screen) for the drop geometry.
 */
@Composable
internal fun HomeDockRow(
    dock: List<HomeItem>,
    render: HomeRender,
    hiddenId: () -> String?,
    metrics: HomeMetrics,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(metrics) {
        onDispose { metrics.setDock(null) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HomeDims.DockMargin)
            .height(rowHeight)
            .onGloballyPositioned { metrics.setDock(it.boundsInRoot().toFRect()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (slot in 0 until HomeLayout.DOCK_SLOTS) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val item = dock.firstOrNull { it.placement.col == slot }
                if (item != null && (item !is AppItem || render.apps[item.app] != null)) {
                    key(item.id) {
                        HomeItemContent(
                            item = item,
                            render = render,
                            showLabel = false,
                            interactive = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    if (hiddenId() != item.id) drawContent()
                                },
                        )
                    }
                }
            }
        }
    }
}

/** Page dots above the dock. The current page is read here, so paging recomposes only the dots. */
@Composable
internal fun HomePageDots(
    pagerState: PagerState,
    pageCount: Int,
    infinite: Boolean,
    modifier: Modifier = Modifier,
) {
    if (pageCount > 1) {
        val current = HomeLayoutMath.logicalPage(pagerState.currentPage, pageCount, infinite)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            DotPageIndicator(count = pageCount, current = current)
        }
    }
}

/**
 * The "Search" pill under the dock: 46 dp high, fully rounded, the magnifier at the left and the word
 * centred. Tapping it opens the drawer with the keyboard.
 */
@Composable
internal fun HomeSearchPill(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DotlineTheme.colors
    val shape = DotlineTheme.shapes.pill
    val baseStyle = DotlineTheme.type.body
    val textStyle = remember(baseStyle, colors.secondary) {
        baseStyle.copy(fontSize = 14.sp, lineHeight = 20.sp, color = colors.secondary)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HomeDims.SearchMargin)
            .height(HomeDims.SearchHeight)
            .background(colors.searchPill, shape)
            .semantics {
                contentDescription = "Search apps"
                role = Role.Button
            }
            .flatClickable(shape = shape, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SearchIcon(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
                .size(20.dp),
            color = colors.secondary,
        )
        BasicText(text = "Search", style = textStyle, maxLines = 1)
    }
}
