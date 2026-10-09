package com.dotline.launcher.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Rect as AndroidRect
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotline.launcher.AppGraph
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.AppShortcut
import com.dotline.launcher.data.CalendarRepository
import com.dotline.launcher.data.IconShape
import com.dotline.launcher.data.LayoutEngine
import com.dotline.launcher.data.WidgetProviderEntry
import com.dotline.launcher.data.model.BuiltinWidget
import com.dotline.launcher.data.model.FolderItem
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.HostedWidgetItem
import com.dotline.launcher.data.model.WidgetItem
import com.dotline.launcher.home.DragSource
import com.dotline.launcher.home.DropTarget
import com.dotline.launcher.home.FRect
import com.dotline.launcher.home.GridMetrics
import com.dotline.launcher.home.HomeController
import com.dotline.launcher.home.HomeGeometry
import com.dotline.launcher.home.HomeLayoutMath
import com.dotline.launcher.home.HomeMenu
import com.dotline.launcher.home.WidgetHostMath
import com.dotline.launcher.home.ZoneSet
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.DottedDivider
import com.dotline.launcher.ui.components.PillButton
import com.dotline.launcher.ui.drawer.AppDrawer
import com.dotline.launcher.ui.home.widgets.NoteEditorPanel
import com.dotline.launcher.ui.home.widgets.WidgetActions
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.LocalReduceMotion
import com.dotline.launcher.ui.theme.flatClickable
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* ------------------------------------------------------------------------------------------
 * Everything that is drawn over the home pages: edit mode chrome, drop zones, the drag preview and
 * destination hints, the long-press menu, the open folder, the drawer, the widget picker and the
 * small panels of the widget flows. Each overlay is its own composable that reads only the slice
 * of state it needs, and the ones that follow the finger read its position in graphicsLayer / draw
 * lambdas only.
 * ------------------------------------------------------------------------------------------ */

/** Drag preview: the lifted item is a little larger and slightly see-through. */
private const val DRAG_SCALE = 1.08f
private const val DRAG_ALPHA = 0.92f

/** Most app shortcuts shown in an app's long-press menu. */
private const val MAX_SHORTCUTS = 4

private val MenuWidth = 232.dp
private val MenuRowShape = RoundedCornerShape(16.dp)

// ---------------------------------------------------------------------------------------------
// Edit mode chrome
// ---------------------------------------------------------------------------------------------

/**
 * The dotted grid (shown while editing or dragging) and the top button panel (edit mode, not while
 * dragging). Both fade in 150 ms.
 */
@Composable
internal fun EditChrome(
    env: HomeEnv,
    onOpenWidgets: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val settings = LocalSettings.current
    val metrics = env.metrics
    val editMode = env.slices.editMode
    val dragging = env.slices.dragging
    val spec: AnimationSpec<Float> = if (reduceMotion) {
        snap<Float>()
    } else {
        tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)
    }
    val dotsAlpha = animateFloatAsState(
        targetValue = if (editMode || dragging) 1f else 0f,
        animationSpec = spec,
        label = "gridDots",
    )
    val panelAlpha = animateFloatAsState(
        targetValue = if (editMode && !dragging) 1f else 0f,
        animationSpec = spec,
        label = "editPanel",
    )
    val dotColor = DotlineTheme.colors.tertiary
    val cols = settings.gridColumns
    val rows = settings.gridRows

    if (dotsAlpha.value > 0f) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dotsAlpha.value },
        ) {
            val cm = metrics.cellMetrics(cols, rows)
            if (cm != null) {
                val area = metrics.pageArea
                val radius = 1.dp.toPx()
                val left = area.left + cm.left
                for (r in 0..rows) {
                    for (c in 0..cols) {
                        drawCircle(
                            color = dotColor,
                            radius = radius,
                            center = Offset(left + c * cm.cellW, area.top + r * cm.cellH),
                        )
                    }
                }
            }
        }
    }
    if (panelAlpha.value > 0f) {
        EditPanel(
            metrics = metrics,
            fade = panelAlpha,
            onOpenWidgets = onOpenWidgets,
            onOpenWallpaper = onOpenWallpaper,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
private fun EditPanel(
    metrics: HomeMetrics,
    fade: State<Float>,
    onOpenWidgets: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    DisposableEffect(metrics) {
        onDispose { metrics.updatePanel(null) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding()
            .graphicsLayer { alpha = fade.value },
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = HomeDims.ChromeTopGap)
                .onGloballyPositioned { metrics.updatePanel(it.boundsInRoot().toFRect()) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChromePill(text = "Widgets", onClick = onOpenWidgets)
            ChromePill(text = "Wallpaper", onClick = onOpenWallpaper)
            ChromePill(text = "Settings", onClick = onOpenSettings)
        }
    }
}

@Composable
private fun ChromePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DotlineTheme.colors
    val shape = DotlineTheme.shapes.pill
    val baseStyle = DotlineTheme.type.bodyMedium
    val style = remember(baseStyle, colors.primary) {
        baseStyle.copy(fontSize = 14.sp, lineHeight = 20.sp, color = colors.primary)
    }
    Box(
        modifier = modifier
            .height(HomeDims.ChromeHeight)
            .background(colors.searchPill, shape)
            .flatClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = style, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Drag: destination hints, drop zones, preview
// ---------------------------------------------------------------------------------------------

private fun cellRect(col: Int, row: Int, spanX: Int, spanY: Int, cm: GridMetrics, area: FRect): FRect = FRect(
    area.left + cm.left + col * cm.cellW,
    area.top + row * cm.cellH,
    area.left + cm.left + (col + spanX) * cm.cellW,
    area.top + (row + spanY) * cm.cellH,
)

private fun dockSlotRect(dock: FRect, slot: Int): FRect {
    val w = dock.width / HomeLayout.DOCK_SLOTS
    return FRect(dock.left + slot * w, dock.top, dock.left + (slot + 1) * w, dock.bottom)
}

/** An outline in the shape of an icon tile (a circle, or a rounded square with the 28 percent corner). */
private fun DrawScope.drawTileOutline(
    cx: Float,
    cy: Float,
    size: Float,
    rounded: Boolean,
    color: Color,
    strokeWidth: Float,
) {
    if (rounded) {
        drawRoundRect(
            color = color,
            topLeft = Offset(cx - size / 2f, cy - size / 2f),
            size = Size(size, size),
            cornerRadius = CornerRadius(size * 0.28f),
            style = Stroke(width = strokeWidth),
        )
    } else {
        drawCircle(color = color, radius = size / 2f, center = Offset(cx, cy), style = Stroke(width = strokeWidth))
    }
}

/**
 * Where the dragged item would land: a 2 dp white ring around the app or folder it is hovering
 * (drop = make or join a folder), or a 1 dp outline of the destination cell. Recomposes only when
 * the target changes, never on every finger move.
 */
@Composable
internal fun DragHints(env: HomeEnv, layout: HomeLayout, cols: Int, rows: Int, tileDp: Dp) {
    if (!env.slices.dragging) return
    val target = env.slices.dragTarget ?: return
    val metrics = env.metrics
    val density = LocalDensity.current
    val colors = DotlineTheme.colors
    val settings = LocalSettings.current
    val tilePx = with(density) { tileDp.toPx() }
    val labelBlockPx = if (settings.showLabels) {
        with(density) { (HomeDims.LabelGap + HomeDims.LabelLine).toPx() }
    } else {
        0f
    }
    val rounded = settings.iconShape == IconShape.ROUNDED_SQUARE
    val ignoreId = env.slices.dragVisual?.info?.itemId
    // A cell that is taken (so the drop would be refused) is outlined much fainter.
    val cellFree = remember(target, layout, ignoreId) {
        if (target is DropTarget.Cell) {
            LayoutEngine.isFree(layout.pages.getOrNull(target.page).orEmpty(), target.placement, ignoreId)
        } else {
            true
        }
    }
    val ringColor = colors.primary
    val outlineColor = colors.primary.copy(alpha = if (cellFree) 0.6f else 0.2f)

    Canvas(Modifier.fillMaxSize()) {
        val cm = metrics.cellMetrics(cols, rows)
        val area = metrics.pageArea
        val dock = metrics.dock
        when (target) {
            is DropTarget.OnItem -> {
                var rect: FRect? = null
                var inDock = false
                if (target.page == HomeGeometry.DOCK_PAGE) {
                    val item = layout.dock.firstOrNull { it.id == target.itemId }
                    if (item != null && dock != null) {
                        rect = dockSlotRect(dock, item.placement.col)
                        inDock = true
                    }
                } else {
                    val item = LayoutEngine.findItem(layout, target.itemId)
                    if (item != null && cm != null) {
                        val p = item.placement
                        rect = cellRect(p.col, p.row, p.spanX, p.spanY, cm, area)
                    }
                }
                if (rect != null) {
                    val cx = (rect.left + rect.right) / 2f
                    val cy = if (inDock) {
                        (rect.top + rect.bottom) / 2f
                    } else {
                        HomeLayoutMath.tileCenterY(rect.top, rect.height, tilePx, labelBlockPx)
                    }
                    drawTileOutline(cx, cy, tilePx + 12.dp.toPx(), rounded, ringColor, 2.dp.toPx())
                }
            }
            is DropTarget.Cell -> {
                if (cm != null) {
                    val p = target.placement
                    val rect = cellRect(p.col, p.row, p.spanX, p.spanY, cm, area)
                    if (p.spanX == 1 && p.spanY == 1) {
                        drawTileOutline(
                            cx = (rect.left + rect.right) / 2f,
                            cy = HomeLayoutMath.tileCenterY(rect.top, rect.height, tilePx, labelBlockPx),
                            size = tilePx,
                            rounded = rounded,
                            color = outlineColor,
                            strokeWidth = 1.dp.toPx(),
                        )
                    } else {
                        val inset = HomeDims.WidgetInset.toPx()
                        drawRoundRect(
                            color = outlineColor,
                            topLeft = Offset(rect.left + inset, rect.top + inset),
                            size = Size(rect.width - 2f * inset, rect.height - 2f * inset),
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
            }
            is DropTarget.Dock -> {
                if (dock != null) {
                    val rect = dockSlotRect(dock, target.slot)
                    drawTileOutline(
                        cx = (rect.left + rect.right) / 2f,
                        cy = (rect.top + rect.bottom) / 2f,
                        size = tilePx,
                        rounded = rounded,
                        color = outlineColor,
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            else -> Unit
        }
    }
}

/** The drop zones along the top while dragging: Remove, Uninstall, App info. The hovered one is inverted. */
@Composable
internal fun DragZones(env: HomeEnv, layout: HomeLayout) {
    val visual = env.slices.dragVisual ?: return
    val zones = remember(visual.source, layout) { HomeLayoutMath.zoneSetFor(visual.source, layout) }
    if (zones == ZoneSet.NONE) return
    val hover = env.slices.dragTarget
    val z = env.metrics.zoneLayout(zones)
    ZonePill(text = "Remove", rect = z.removePill, hovered = hover is DropTarget.Remove)
    ZonePill(text = "Uninstall", rect = z.uninstallPill, hovered = hover is DropTarget.Uninstall)
    ZonePill(text = "App info", rect = z.infoPill, hovered = hover is DropTarget.AppInfo)
}

@Composable
private fun ZonePill(text: String, rect: FRect?, hovered: Boolean) {
    if (rect == null) return
    val colors = DotlineTheme.colors
    val shape = DotlineTheme.shapes.pill
    val density = LocalDensity.current
    val fill = if (hovered) colors.highlight else colors.widget
    val outline = if (hovered) colors.highlight else colors.tertiary
    val textColor = if (hovered) colors.onHighlight else colors.primary
    val baseStyle = DotlineTheme.type.bodyMedium
    val style = remember(baseStyle, textColor) {
        baseStyle.copy(fontSize = 14.sp, lineHeight = 20.sp, color = textColor)
    }
    val widthDp = with(density) { rect.width.toDp() }
    val heightDp = with(density) { rect.height.toDp() }
    Box(
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(widthDp, heightDp)
            .background(fill, shape)
            .border(1.dp, outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = style, maxLines = 1)
    }
}

/**
 * The lifted item under the finger. Its content is composed once per drag; the finger position is
 * read in the graphicsLayer lambda only, so following the finger costs no recomposition.
 */
@Composable
internal fun DragPreview(env: HomeEnv, layout: HomeLayout, render: HomeRender, cols: Int, rows: Int) {
    val visual = env.slices.dragVisual ?: return
    val metrics = env.metrics
    val cell = metrics.cellMetrics(cols, rows) ?: return
    val density = LocalDensity.current
    val source = visual.source
    val info = visual.info
    val item = remember(source, layout) {
        when (source) {
            is DragSource.OnHome -> LayoutEngine.findItem(layout, source.itemId)
            is DragSource.FromFolder -> com.dotline.launcher.data.model.AppItem(
                "drag", source.app, com.dotline.launcher.data.model.Placement(0, 0),
            )
            is DragSource.NewApp -> com.dotline.launcher.data.model.AppItem(
                "drag", source.app, com.dotline.launcher.data.model.Placement(0, 0),
            )
            is DragSource.NewWidget -> WidgetItem(
                "drag", source.kind, com.dotline.launcher.data.model.Placement(0, 0, source.spanX, source.spanY),
            )
        }
    } ?: return

    val fromHome = source is DragSource.OnHome
    val dockRect = metrics.dock
    val inDock = source is DragSource.OnHome && layout.dock.any { it.id == source.itemId }
    val widthPx: Float
    val heightPx: Float
    if (fromHome && inDock && dockRect != null) {
        widthPx = dockRect.width / HomeLayout.DOCK_SLOTS
        heightPx = dockRect.height
    } else {
        widthPx = info.spanX * cell.cellW
        heightPx = info.spanY * cell.cellH
    }
    // Dragged from the home screen the finger keeps its grip; out of the drawer or a folder it holds the middle.
    val grabX = if (fromHome) info.grabX else widthPx / 2f
    val grabY = if (fromHome) info.grabY else heightPx / 2f
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }
    val uiState = env.uiState
    Box(
        modifier = Modifier
            .size(widthDp, heightDp)
            .graphicsLayer {
                val d = uiState.value.drag
                if (d != null) {
                    translationX = d.x - grabX
                    translationY = d.y - grabY
                }
                scaleX = DRAG_SCALE
                scaleY = DRAG_SCALE
                alpha = DRAG_ALPHA
            },
    ) {
        if (item is HostedWidgetItem) {
            // A second live app widget view for the same widget id is wasteful: lift a flat card instead.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(HomeDims.WidgetInset)
                    .background(DotlineTheme.colors.widget, RoundedCornerShape(20.dp)),
            )
        } else {
            HomeItemContent(
                item = item,
                render = render,
                showLabel = fromHome && !inDock,
                interactive = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The overlay stack
// ---------------------------------------------------------------------------------------------

/** Everything that covers the home pages, in z-order. Edit chrome and hints are drawn before it. */
@Composable
internal fun HomeOverlayHost(
    env: HomeEnv,
    layout: HomeLayout,
    apps: AppIndex,
    render: HomeRender,
    flows: WidgetFlows,
    drawer: DrawerController,
    focusSearch: Boolean,
    cols: Int,
    rows: Int,
) {
    DrawerLayer(env = env, layout = layout, drawer = drawer, focusSearch = focusSearch, cols = cols, rows = rows)
    HomeFolderLayer(env = env, layout = layout, apps = apps)
    HomeMenuLayer(env = env, layout = layout)
    HomePickerLayer(env = env, flows = flows, cols = cols, rows = rows)
    WidgetFlowPanels(flows = flows)
    DragZones(env = env, layout = layout)
    DragPreview(env = env, layout = layout, render = render, cols = cols, rows = rows)
}

/** The app drawer. Composed only while it is visible, so a closed drawer costs nothing. */
@Composable
private fun DrawerLayer(
    env: HomeEnv,
    layout: HomeLayout,
    drawer: DrawerController,
    focusSearch: Boolean,
    cols: Int,
    rows: Int,
) {
    val progress = drawer.progress
    val visible by remember(progress) { derivedStateOf { progress.value > 0f } }
    val open = env.slices.drawerOpen
    if (visible || open) {
        AppDrawer(
            progress = { progress.value },
            open = open,
            onClose = { env.controller.setDrawerOpen(false) },
            onLaunch = { app, bounds ->
                env.actions.launch(app, bounds)
                env.controller.setDrawerOpen(false)
            },
            onAddToHome = { app ->
                val page = HomeLayoutMath.pageForNewItem(layout, cols, rows, 1, 1)
                if (env.controller.addAppToHome(app.key)) {
                    env.controller.setDrawerOpen(false)
                    if (page >= 0) env.pager.goToLogical(page, animate = false)
                } else {
                    env.actions.toast("Home screen is full")
                }
            },
            onStartDrag = { app, pointer, grab -> env.host.beginDrawerDrag(app, pointer, grab) },
            focusSearchOnOpen = focusSearch,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Open folder
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeFolderLayer(env: HomeEnv, layout: HomeLayout, apps: AppIndex) {
    val folderId = env.slices.openFolderId ?: return
    val folder = LayoutEngine.findItem(layout, folderId) as? FolderItem
    val folderApps = remember(folder, apps) {
        folder?.apps?.mapNotNull { apps[it] } ?: emptyList()
    }
    if (folder == null || folderApps.isEmpty()) {
        // Nothing to show (the folder vanished or all its apps are gone): never leave it "open".
        LaunchedEffect(folderId) { env.controller.closeFolder() }
        return
    }
    FolderPopup(
        name = folder.name,
        apps = folderApps,
        onRename = { name -> env.controller.renameFolder(folder.id, name) },
        onLaunch = { app, bounds ->
            env.actions.launch(app, bounds)
            env.controller.closeFolder()
        },
        onAppLongPress = { app, center, topLeft -> env.host.beginFolderDrag(folder.id, app, center, topLeft) },
        onDismiss = { env.controller.closeFolder() },
        modifier = Modifier.fillMaxSize(),
    )
}

// ---------------------------------------------------------------------------------------------
// Long-press menu
// ---------------------------------------------------------------------------------------------

private class MenuEntry(val text: String, val onClick: () -> Unit)

@Composable
private fun rememberShortcuts(menu: HomeMenu, graph: AppGraph): State<List<AppShortcut>> {
    val appKey = (menu as? HomeMenu.App)?.app
    return produceState(initialValue = emptyList<AppShortcut>(), key1 = appKey) {
        value = if (appKey == null) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) { graph.apps.shortcuts(appKey).take(MAX_SHORTCUTS) }
        }
    }
}

private fun resizeWidget(controller: HomeController, item: WidgetItem, actions: HomeActions) {
    val candidates = HomeLayoutMath.resizeCandidates(item.kind, item.placement.spanX, item.placement.spanY)
    for (size in candidates) {
        if (controller.resizeWidget(item.id, size.first, size.second)) return
    }
    actions.toast("No room to resize here")
}

private fun buildMenuEntries(
    menu: HomeMenu,
    shortcuts: List<AppShortcut>,
    layout: HomeLayout,
    locked: Boolean,
    bounds: AndroidRect?,
    env: HomeEnv,
    graph: AppGraph,
): List<MenuEntry> {
    val controller = env.controller
    val out = ArrayList<MenuEntry>()
    when (menu) {
        is HomeMenu.App -> {
            for (shortcut in shortcuts) {
                out.add(
                    MenuEntry(shortcut.label) {
                        controller.dismissMenu()
                        graph.apps.launchShortcut(shortcut, bounds)
                    },
                )
            }
            out.add(
                MenuEntry("App info") {
                    controller.dismissMenu()
                    graph.apps.openAppInfo(menu.app, bounds)
                },
            )
            out.add(
                MenuEntry("Uninstall") {
                    controller.dismissMenu()
                    graph.apps.requestUninstall(menu.app)
                },
            )
            val itemId = menu.itemId
            if (itemId != null && !locked) {
                out.add(
                    MenuEntry("Remove from home") {
                        controller.dismissMenu()
                        controller.removeFromHome(itemId)
                    },
                )
            }
        }
        is HomeMenu.Folder -> {
            out.add(MenuEntry("Rename") { controller.openFolder(menu.itemId) })
            if (!locked) {
                out.add(
                    MenuEntry("Remove folder") {
                        controller.dismissMenu()
                        controller.removeFromHome(menu.itemId)
                    },
                )
            }
        }
        is HomeMenu.Widget -> {
            val item = LayoutEngine.findItem(layout, menu.itemId)
            if (item is WidgetItem && !locked) {
                out.add(
                    MenuEntry("Resize") {
                        controller.dismissMenu()
                        resizeWidget(controller, item, env.actions)
                    },
                )
            }
            if (!locked) {
                out.add(
                    MenuEntry("Remove") {
                        controller.dismissMenu()
                        controller.removeFromHome(menu.itemId)
                    },
                )
            }
        }
    }
    return out
}

/**
 * The long-press popup: a flat 24 dp card next to the pressed item on a faint scrim. Tapping the
 * scrim closes it. App shortcuts are loaded off the main thread and appear when ready.
 */
@Composable
private fun HomeMenuLayer(env: HomeEnv, layout: HomeLayout) {
    val menu = env.slices.menu ?: return
    val graph = LocalAppGraph.current
    val settings = LocalSettings.current
    val colors = DotlineTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val enter = remember(menu) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(menu) {
        if (enter.value < 1f) {
            enter.animateTo(1f, tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing))
        }
    }
    val shortcuts = rememberShortcuts(menu, graph)
    val bounds = remember(menu) {
        menu.anchor?.let { AndroidRect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
    }
    val entries = remember(menu, shortcuts.value, layout, settings.lockLayout) {
        buildMenuEntries(menu, shortcuts.value, layout, settings.lockLayout, bounds, env, graph)
    }
    if (entries.isEmpty()) return

    val scrim = colors.scrim.copy(alpha = 0.4f)
    val controller = env.controller
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value }
            .background(scrim)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controller.dismissMenu() })
            },
    ) {
        MenuPlacement(anchor = menu.anchor) {
            val shape = DotlineTheme.shapes.card
            Column(
                modifier = Modifier
                    .width(MenuWidth)
                    .background(colors.card, shape)
                    .border(1.dp, colors.outline, shape)
                    // Taps on the card's own padding must not close the menu.
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { })
                    }
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

/** Places its single child next to [anchor] (root px), clamped to the screen. */
@Composable
private fun MenuPlacement(anchor: FRect?, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val card = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val pos = HomeLayoutMath.menuPosition(
            anchor = anchor,
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
            .flatClickable(shape = MenuRowShape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        BasicText(text = text, style = style, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Widget picker and the flows behind it
// ---------------------------------------------------------------------------------------------

/** The calendar permission explanation: [kind] is the widget to add afterwards, null when the widget itself asked. */
@Immutable
internal class CalendarPrompt(val kind: BuiltinWidget?, val spanX: Int, val spanY: Int)

private class PendingHosted(val id: Int, val entry: WidgetProviderEntry)

/**
 * State and logic of adding widgets: built-in ones (the calendar one needs an explained, optional
 * permission), Android app widgets (bind, optional configure, add) and the note editor. The three
 * system launchers are attached by [rememberWidgetFlows].
 */
@Stable
internal class WidgetFlows(
    private val context: android.content.Context,
    private val graph: AppGraph,
    private val controller: HomeController,
    private val actions: HomeActions,
    private val metrics: HomeMetrics,
    private val pager: HomePager,
) {
    var calendarPrompt: CalendarPrompt? by mutableStateOf<CalendarPrompt?>(null)
        private set
    var noteEditorOpen: Boolean by mutableStateOf(false)
        private set

    internal var launchPermission: () -> Unit = {}
    internal var launchBind: (Intent) -> Unit = {}
    internal var launchConfig: (Intent) -> Unit = {}

    private var pending: PendingHosted? = null

    /** True while one of the flow panels covers the home screen. */
    val overlayOpen: Boolean get() = calendarPrompt != null || noteEditorOpen

    /** What the built-in widgets can ask the home screen to do. */
    val widgetActions: WidgetActions = WidgetActions(
        requestCalendarPermission = { calendarPrompt = CalendarPrompt(null, 0, 0) },
        openClockApp = { actions.openClockApp() },
        openCalendarApp = { actions.openCalendarApp() },
        editNote = { noteEditorOpen = true },
    )

    fun closeNoteEditor() {
        noteEditorOpen = false
    }

    /** The Home button: whatever panel is open goes away (nothing is added). */
    fun closePanels() {
        calendarPrompt = null
        noteEditorOpen = false
    }

    // ---- built-in widgets -------------------------------------------------------------------

    fun onPickBuiltin(kind: BuiltinWidget, spanX: Int, spanY: Int) {
        controller.closePicker()
        if (kind == BuiltinWidget.CALENDAR && !CalendarRepository.hasPermission(context)) {
            calendarPrompt = CalendarPrompt(kind, spanX, spanY)
        } else {
            addBuiltin(kind, spanX, spanY)
        }
    }

    private fun addBuiltin(kind: BuiltinWidget, spanX: Int, spanY: Int) {
        val s = graph.settings.settings.value
        val page = HomeLayoutMath.pageForNewItem(graph.layout.layout.value, s.gridColumns, s.gridRows, spanX, spanY)
        if (controller.addWidget(kind, spanX, spanY)) {
            if (page >= 0) pager.goToLogical(page, animate = false)
        } else {
            actions.toast("Home screen is full")
        }
    }

    /** "Continue" on the explanation: Android asks for the permission now. */
    fun confirmCalendar() {
        try {
            launchPermission()
        } catch (e: Exception) {
            CrashLog.record("home: calendar permission", e)
            finishCalendar()
        }
    }

    /** "Not now": the widget is still added, it shows how to allow calendar access later. */
    fun skipCalendar() {
        finishCalendar()
    }

    fun onCalendarPermissionResult() {
        finishCalendar()
    }

    private fun finishCalendar() {
        val prompt = calendarPrompt ?: return
        calendarPrompt = null
        val kind = prompt.kind
        if (kind != null) addBuiltin(kind, prompt.spanX, prompt.spanY)
    }

    // ---- app widgets ------------------------------------------------------------------------

    fun onPickProvider(entry: WidgetProviderEntry) {
        controller.closePicker()
        val host = graph.widgetHost
        val id = host.allocateId()
        try {
            if (host.bindNow(id, entry)) {
                afterBind(id, entry)
            } else {
                pending = PendingHosted(id, entry)
                launchBind(host.bindPermissionIntent(id, entry))
            }
        } catch (e: Exception) {
            CrashLog.record("home: bind widget", e)
            discard(id)
            actions.toast("This widget could not be added")
        }
    }

    fun onBindResult(resultCode: Int) {
        val p = pending ?: return
        pending = null
        if (resultCode == Activity.RESULT_OK) afterBind(p.id, p.entry) else discard(p.id)
    }

    private fun afterBind(id: Int, entry: WidgetProviderEntry) {
        val intent = graph.widgetHost.configureIntent(id)
        if (intent == null) {
            finishHosted(id, entry)
            return
        }
        pending = PendingHosted(id, entry)
        try {
            launchConfig(intent)
        } catch (e: Exception) {
            CrashLog.record("home: configure widget", e)
            pending = null
            discard(id)
            actions.toast("This widget could not be set up")
        }
    }

    fun onConfigResult(resultCode: Int) {
        val p = pending ?: return
        pending = null
        if (resultCode == Activity.RESULT_OK) finishHosted(p.id, p.entry) else discard(p.id)
    }

    private fun finishHosted(id: Int, entry: WidgetProviderEntry) {
        val s = graph.settings.settings.value
        val cols = s.gridColumns
        val rows = s.gridRows
        val cm = metrics.cellMetrics(cols, rows)
        val density = if (metrics.density > 0f) metrics.density else 1f
        val cellWidthDp = if (cm != null) cm.cellW / density else 77f
        val cellHeightDp = if (cm != null) cm.cellH / density else 100f
        val span = WidgetHostMath.spanFor(entry.minWidthDp, entry.minHeightDp, cellWidthDp, cellHeightDp, cols, rows)
        val page = HomeLayoutMath.pageForNewItem(graph.layout.layout.value, cols, rows, span.first, span.second)
        if (controller.addHostedWidget(id, entry.provider.flattenToString(), span.first, span.second)) {
            if (page >= 0) pager.goToLogical(page, animate = false)
        } else {
            discard(id)
            actions.toast("Home screen is full")
        }
    }

    private fun discard(id: Int) {
        try {
            graph.widgetHost.deleteId(id)
        } catch (e: Exception) {
            CrashLog.record("home: delete widget id", e)
        }
    }
}

/** Creates the [WidgetFlows] and attaches the three system launchers it needs. */
@Composable
internal fun rememberWidgetFlows(
    graph: AppGraph,
    controller: HomeController,
    actions: HomeActions,
    metrics: HomeMetrics,
    pager: HomePager,
): WidgetFlows {
    val context = LocalContext.current
    val flows = remember(graph, controller, actions, metrics, pager) {
        WidgetFlows(context, graph, controller, actions, metrics, pager)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        flows.onCalendarPermissionResult()
    }
    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        flows.onBindResult(result.resultCode)
    }
    val configLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        flows.onConfigResult(result.resultCode)
    }
    SideEffect {
        flows.launchPermission = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }
        flows.launchBind = { intent: Intent -> bindLauncher.launch(intent) }
        flows.launchConfig = { intent: Intent -> configLauncher.launch(intent) }
    }
    return flows
}

/** The full-screen widget picker while the controller says it is open. */
@Composable
private fun HomePickerLayer(env: HomeEnv, flows: WidgetFlows, cols: Int, rows: Int) {
    if (!env.slices.pickerOpen) return
    val density = LocalDensity.current
    val cm = env.metrics.cellMetrics(cols, rows)
    val cellWidthDp = if (cm != null) cm.cellW / density.density else 77f
    val cellHeightDp = if (cm != null) cm.cellH / density.density else 100f
    WidgetPicker(
        onPickBuiltin = { kind, spanX, spanY -> flows.onPickBuiltin(kind, spanX, spanY) },
        onPickProvider = { entry -> flows.onPickProvider(entry) },
        onDismiss = { env.controller.closePicker() },
        cellWidthDp = cellWidthDp,
        cellHeightDp = cellHeightDp,
        modifier = Modifier.fillMaxSize(),
    )
}

/** The calendar permission explanation and the note editor. */
@Composable
private fun WidgetFlowPanels(flows: WidgetFlows) {
    if (flows.calendarPrompt != null) {
        CalendarPermissionPanel(onContinue = { flows.confirmCalendar() }, onNotNow = { flows.skipCalendar() })
    }
    if (flows.noteEditorOpen) {
        BackHandler { flows.closeNoteEditor() }
        NoteEditorPanel(onDismiss = { flows.closeNoteEditor() }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun CalendarPermissionPanel(onContinue: () -> Unit, onNotNow: () -> Unit) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val shape = DotlineTheme.shapes.card
    BackHandler { onNotNow() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { })
            }
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .background(colors.card, shape)
                .padding(24.dp),
        ) {
            BasicText(text = "Calendar", style = type.heading.copy(color = colors.primary))
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = "Dotline reads your next calendar event only when the home screen opens. " +
                    "Android will ask for calendar access.",
                style = type.body.copy(color = colors.secondary),
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(text = "Not now", onClick = onNotNow, modifier = Modifier.weight(1f), filled = false)
                PillButton(text = "Continue", onClick = onContinue, modifier = Modifier.weight(1f), filled = true)
            }
        }
    }
}
