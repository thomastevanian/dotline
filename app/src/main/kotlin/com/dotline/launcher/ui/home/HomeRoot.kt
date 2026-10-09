package com.dotline.launcher.ui.home

import android.graphics.Rect as AndroidRect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.LayoutIds
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.home.DragItemInfo
import com.dotline.launcher.home.DragSource
import com.dotline.launcher.home.DropTarget
import com.dotline.launcher.home.DropZoneLayout
import com.dotline.launcher.home.FRect
import com.dotline.launcher.home.GestureConfig
import com.dotline.launcher.home.GestureEngine
import com.dotline.launcher.home.GridMetrics
import com.dotline.launcher.home.HomeController
import com.dotline.launcher.home.HomeGeometry
import com.dotline.launcher.home.HomeLayoutMath
import com.dotline.launcher.home.HomeMenu
import com.dotline.launcher.home.HomeUiState
import com.dotline.launcher.home.ScrollRequest
import com.dotline.launcher.home.ZoneSet
import com.dotline.launcher.service.NotificationDots
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.home.widgets.LocalWidgetActions
import com.dotline.launcher.ui.theme.LocalReduceMotion
import com.dotline.launcher.widgets.WidgetSupport
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/* ------------------------------------------------------------------------------------------
 * The Nothing OS home screen.
 *
 * Structure: one full-screen root Box carries the single pointer handler (HomeInput.kt). Inside it
 * the home content (pager of icon pages, page dots, dock, search pill) and the overlays (edit chrome,
 * drop zones, drag preview, menus, folder, drawer, widget picker).
 *
 * Performance rules: the controller state changes on every drag move, so HomeRoot never reads it
 * directly. It reads derived slices (HomeSlices) that change only when something that matters
 * changes; the finger position is read only in graphicsLayer / draw lambdas of small leaf
 * composables. The drawer progress is read only in graphicsLayer lambdas and in the leaf that
 * decides whether the drawer is composed.
 * ------------------------------------------------------------------------------------------ */

/** What the drag preview needs to know about the drag: unchanged while the finger moves. */
internal data class DragVisual(val source: DragSource, val info: DragItemInfo)

/**
 * Slices of [HomeUiState]. Each property is a derived state, so a composable that reads one is only
 * invalidated when that slice changes, not on every finger move.
 */
@Stable
internal class HomeSlices(private val ui: State<HomeUiState>) {
    val editMode: Boolean by derivedStateOf { ui.value.editMode }
    val dragging: Boolean by derivedStateOf { ui.value.drag != null }
    val draggedItemId: String? by derivedStateOf { (ui.value.drag?.source as? DragSource.OnHome)?.itemId }
    val dragVisual: DragVisual? by derivedStateOf { ui.value.drag?.let { DragVisual(it.source, it.info) } }
    val dragTarget: DropTarget? by derivedStateOf { ui.value.drag?.target }
    val openFolderId: String? by derivedStateOf { ui.value.openFolderId }
    val menu: HomeMenu? by derivedStateOf { ui.value.menu }
    val pickerOpen: Boolean by derivedStateOf { ui.value.pickerOpen }
    val drawerOpen: Boolean by derivedStateOf { ui.value.drawerOpen }

    /** True while Back has something to close. */
    val anyOpen: Boolean by derivedStateOf {
        val s = ui.value
        s.drag != null || s.menu != null || s.pickerOpen || s.openFolderId != null || s.drawerOpen || s.editMode
    }
}

/**
 * Measured geometry of the home screen in root pixels, reported by onGloballyPositioned callbacks.
 * The rects are state, so draw lambdas that read them redraw when the layout changes; the pointer
 * handler reads them at event time. [geometry] builds the [HomeGeometry] the controller needs and
 * caches it until a measurement, the grid size or the zone set changes.
 */
@Stable
internal class HomeMetrics {
    var screen by mutableStateOf(EmptyRect)
        private set

    /** The screen minus status bar, cutout and navigation bar. */
    var safe by mutableStateOf(EmptyRect)
        private set

    /** Where the pager sits; the icon grid is anchored to its top. */
    var pageArea by mutableStateOf(EmptyRect)
        private set

    var dock: FRect? by mutableStateOf<FRect?>(null)
        private set

    /** The edit mode button panel (taps on it are not "taps on empty space"). */
    var panel: FRect? by mutableStateOf<FRect?>(null)
        private set

    /** Screen density; set from composition. */
    var density: Float = 1f

    private var version = 0
    private var cached: HomeGeometry? = null
    private var cachedVersion = -1
    private var cachedCols = 0
    private var cachedRows = 0
    private var cachedZones = ZoneSet.NONE
    private var cachedDensity = 0f

    fun setScreen(r: FRect) {
        if (r != screen) { screen = r; version++ }
    }

    fun setSafe(r: FRect) {
        if (r != safe) { safe = r; version++ }
    }

    fun setPageArea(r: FRect) {
        if (r != pageArea) { pageArea = r; version++ }
    }

    fun setDock(r: FRect?) {
        if (r != dock) { dock = r; version++ }
    }

    fun setPanel(r: FRect?) {
        if (r != panel) panel = r
    }

    fun isOverChrome(x: Float, y: Float): Boolean = panel?.contains(x, y) == true

    /** Cell metrics of the grid for the current page area, or null before the first measurement. */
    fun cellMetrics(cols: Int, rows: Int): GridMetrics? {
        val a = pageArea
        if (a.width <= 0f || a.height <= 0f) return null
        return HomeLayoutMath.gridMetrics(
            areaWidth = a.width,
            areaHeight = a.height,
            cols = cols,
            rows = rows,
            marginPx = HomeDims.GridMargin.value * density,
            maxCellHeightPx = HomeDims.MaxCellHeight.value * density,
        )
    }

    /** The pills and hit areas of the drop zones for [zones]. */
    fun zoneLayout(zones: ZoneSet): DropZoneLayout {
        val s = screen
        return HomeLayoutMath.dropZones(
            set = zones,
            screen = s,
            safeTop = safe.top - s.top,
            marginPx = HomeDims.ZoneMargin.value * density,
            gapPx = HomeDims.ZoneGap.value * density,
            pillHeightPx = HomeDims.ZoneHeight.value * density,
            topGapPx = HomeDims.ChromeTopGap.value * density,
            hitBelowPx = HomeDims.ZoneHitBelow.value * density,
        )
    }

    /** Geometry for hit testing and drops, or null before the screen has been measured. */
    fun geometry(cols: Int, rows: Int, zones: ZoneSet): HomeGeometry? {
        val s = screen
        val a = pageArea
        if (s.width <= 0f || a.width <= 0f || a.height <= 0f) return null
        val old = cached
        if (old != null && cachedVersion == version && cachedCols == cols && cachedRows == rows &&
            cachedZones == zones && cachedDensity == density
        ) {
            return old
        }
        val m = cellMetrics(cols, rows) ?: return null
        val z = zoneLayout(zones)
        val g = HomeGeometry(
            screen = s,
            grid = HomeLayoutMath.gridRect(a, m),
            cols = cols,
            rows = rows,
            dock = dock,
            removeZone = z.removeHit,
            uninstallZone = z.uninstallHit,
            infoZone = z.infoHit,
            edgePx = HomeDims.EdgeBand.value * density,
        )
        cached = g
        cachedVersion = version
        cachedCols = cols
        cachedRows = rows
        cachedZones = zones
        cachedDensity = density
        return g
    }

    private companion object {
        val EmptyRect = FRect(0f, 0f, 0f, 0f)
    }
}

/**
 * The pager and the mapping between its pages and the layout's pages (they differ while infinite
 * scrolling is on). All state it needs is read live, so it can be used from the pointer handler.
 */
@Stable
internal class HomePager(
    val state: PagerState,
    private val scope: CoroutineScope,
    /** Number of layout pages. */
    private val countState: State<Int>,
    private val infiniteState: State<Boolean>,
    private val reduceMotionState: State<Boolean>,
    initialCount: Int,
    initialInfinite: Boolean,
) {
    /** The page count / mode the pager's current page number was last mapped with. */
    var mappedCount by mutableIntStateOf(initialCount)
    var mappedInfinite by mutableStateOf(initialInfinite)

    val pageCount: Int get() = countState.value
    val infinite: Boolean get() = infiniteState.value

    /** The layout page the pager is showing (nearest page while scrolling). */
    val logicalPage: Int get() = HomeLayoutMath.logicalPage(state.currentPage, pageCount, infinite)

    private fun spec(): AnimationSpec<Float> =
        if (reduceMotionState.value) snap<Float>() else tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)

    /**
     * Shows layout page [page] (animated, instant with reduced motion). A page that is just being
     * created is waited for, so "move to the new page" works right after the layout changed.
     */
    fun goToLogical(page: Int, animate: Boolean) {
        scope.launch {
            if (page >= countState.value || mappedCount != countState.value || mappedInfinite != infiniteState.value) {
                withTimeoutOrNull(600L) {
                    snapshotFlow {
                        countState.value > page && mappedCount == countState.value && mappedInfinite == infiniteState.value
                    }.first { it }
                }
            }
            val target = HomeLayoutMath.nearestPagerPage(state.currentPage, page, pageCount, infinite)
            if (animate) {
                state.animateScrollToPage(page = target, animationSpec = spec())
            } else {
                state.scrollToPage(target)
            }
        }
    }

    /** One page left or right, as asked by a drag held at the screen edge. */
    fun scrollBy(request: ScrollRequest) {
        val dir = when (request) {
            ScrollRequest.NEXT -> 1
            ScrollRequest.PREVIOUS -> -1
            ScrollRequest.NONE -> return
        }
        val target = logicalPage + dir
        if (target < 0) return
        goToLogical(target, animate = true)
    }
}

/** Drives the drawer's slide: 0 = closed, 1 = open. Only the leaf composables read [progress]. */
@Stable
internal class DrawerController(
    val progress: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope,
    private val reduceMotionState: State<Boolean>,
) {
    fun spec(): AnimationSpec<Float> =
        if (reduceMotionState.value) snap<Float>() else tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)

    /** The finger is dragging the drawer up: follow it. */
    fun follow(value: Float) {
        scope.launch { progress.snapTo(value.coerceIn(0f, 1f)) }
    }

    /** Animates the rest of the way to open or closed. */
    fun settle(open: Boolean) {
        scope.launch { progress.animateTo(if (open) 1f else 0f, spec()) }
    }
}

/** What the overlay composables share. Immutable after creation. */
@Stable
internal class HomeEnv(
    val controller: HomeController,
    val uiState: State<HomeUiState>,
    val slices: HomeSlices,
    val metrics: HomeMetrics,
    val actions: HomeActions,
    val host: HomeGestureHost,
    val pager: HomePager,
)

// ---------------------------------------------------------------------------------------------
// HomeRoot
// ---------------------------------------------------------------------------------------------

/** The Nothing OS home screen. Replaces the v1 HomeScreen. */
@Composable
fun HomeRoot(
    onOpenSettings: () -> Unit,
    onOpenWallpaperStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val settings = LocalSettings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // ---- state ------------------------------------------------------------------------------

    val controller = remember(graph) {
        HomeController(
            store = graph.layout,
            grid = {
                val s = graph.settings.settings.value
                s.gridColumns to s.gridRows
            },
            newId = LayoutIds::newId,
        )
    }
    val uiState = controller.ui.collectAsStateWithLifecycle()
    val slices = remember(uiState) { HomeSlices(uiState) }
    val layout by graph.layout.layout.collectAsStateWithLifecycle()
    val apps by graph.apps.apps.collectAsStateWithLifecycle()
    val appIndex = remember(apps) { AppIndex(apps.associateBy { it.key }) }
    val reduceMotionState = rememberUpdatedState(reduceMotion)

    val metrics = remember { HomeMetrics() }
    metrics.density = density.density
    val actions = remember(context, graph, controller) { HomeActions(context, graph, controller) }

    // ---- pager ------------------------------------------------------------------------------

    val pageCount = layout.pages.size.coerceAtLeast(1)
    val infinite = HomeLayoutMath.isInfinite(settings.infiniteScroll, pageCount)
    val countState = rememberUpdatedState(pageCount)
    val infiniteState = rememberUpdatedState(infinite)
    val pagerCountState = rememberUpdatedState(HomeLayoutMath.pagerCount(pageCount, infinite))
    val pagerState = rememberPagerState(initialPage = HomeLayoutMath.initialPage(pageCount, infinite)) {
        pagerCountState.value
    }
    val pager = remember(pagerState) {
        HomePager(pagerState, scope, countState, infiniteState, reduceMotionState, pageCount, infinite)
    }

    // When the number of pages or the paging mode changes the same pager page would show another
    // layout page: move to the page that shows the layout page the user was on.
    LaunchedEffect(infinite, pageCount) {
        val logical = HomeLayoutMath.logicalPage(pagerState.currentPage, pager.mappedCount, pager.mappedInfinite)
        val target = HomeLayoutMath.initialPage(pageCount, infinite, logical)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
        pager.mappedCount = pageCount
        pager.mappedInfinite = infinite
    }

    // ---- drawer -----------------------------------------------------------------------------

    val drawerProgress = remember { Animatable(0f) }
    val drawer = remember(drawerProgress, scope) { DrawerController(drawerProgress, scope, reduceMotionState) }
    var focusSearch by remember { mutableStateOf(false) }
    val drawerOpen = slices.drawerOpen
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) {
            drawerProgress.animateTo(1f, drawer.spec())
        } else {
            if (drawerProgress.value > 0f) drawerProgress.animateTo(0f, drawer.spec())
            focusSearch = false
        }
    }

    // ---- widget flows and gesture host ------------------------------------------------------

    val flows = rememberWidgetFlows(graph, controller, actions, metrics, pager)
    val engine = remember { GestureEngine(GestureConfig(touchSlop = 8f, swipeDistance = 96f)) }
    val host = remember(controller, pager, drawer, actions, metrics, graph, flows) {
        HomeGestureHost(
            controller = controller,
            engine = engine,
            metrics = metrics,
            pager = pager,
            drawer = drawer,
            actions = actions,
            settingsProvider = { graph.settings.settings.value },
            layoutProvider = { graph.layout.layout.value },
            enabledProvider = {
                !slices.drawerOpen && drawerProgress.value <= 0f && !slices.pickerOpen &&
                    slices.menu == null && slices.openFolderId == null && !flows.overlayOpen
            },
            haptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
        )
    }
    val env = remember(controller, uiState, slices, metrics, actions, host, pager) {
        HomeEnv(controller, uiState, slices, metrics, actions, host, pager)
    }

    // ---- lifecycle, home button, back --------------------------------------------------------

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, graph) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    graph.weather.refreshIfStale()
                    try {
                        WidgetSupport.refreshDataWidgets(context)
                    } catch (e: Exception) {
                        CrashLog.record("home: refresh data widgets", e)
                    }
                    graph.widgetHost.start()
                }
                Lifecycle.Event.ON_STOP -> graph.widgetHost.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            graph.widgetHost.stop()
        }
    }

    LaunchedEffect(graph, controller, pager, flows) {
        graph.homePressed.collect {
            controller.closeAll()
            flows.closePanels()
            pager.goToLogical(0, animate = true)
        }
    }

    BackHandler(enabled = slices.anyOpen) {
        controller.closeTopmost()
    }

    // ---- content ----------------------------------------------------------------------------

    val editMode = slices.editMode
    val dim = settings.wallpaperDim
    val dotsState: State<Set<String>> = if (settings.notificationDots) {
        NotificationDots.active.collectAsStateWithLifecycle()
    } else {
        remember { mutableStateOf(emptySet<String>()) }
    }
    val onLaunch = remember(actions) {
        { app: AppInfo, bounds: AndroidRect? -> actions.launch(app, bounds) }
    }
    val onOpenFolder = remember(controller) {
        { id: String -> controller.openFolder(id) }
    }
    val hiddenId = remember(slices) { { slices.draggedItemId } }

    CompositionLocalProvider(LocalWidgetActions provides flows.widgetActions) {
        // The root Box sits at the origin of the composition and fills the screen: pointer positions
        // seen by the gesture handler are root coordinates, the same space as every measured rect.
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .drawBehind {
                    if (dim > 0f) drawRect(color = Color.Black, alpha = dim)
                }
                .onGloballyPositioned { metrics.setScreen(it.boundsInRoot().toFRect()) }
                .homeGestures(host),
        ) {
            val cols = settings.gridColumns
            val rows = settings.gridRows
            val gridWidth = maxWidth - HomeDims.GridMargin * 2
            val iconBase = gridWidth / cols * HomeLayoutMath.ICON_FRACTION
            val tileDp = iconBase * settings.iconSize
            val dockHeight = tileDp + HomeDims.DockVerticalPad * 2
            val render = remember(appIndex, iconBase, editMode, dotsState, onLaunch, onOpenFolder) {
                HomeRender(appIndex, iconBase, editMode, dotsState, onLaunch, onOpenFolder)
            }
            // While editing or dragging the pages start lower, below the button panel / drop zones.
            val insetSpec: AnimationSpec<Float> = if (reduceMotion) {
                snap<Float>()
            } else {
                tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)
            }
            val insetFraction = animateFloatAsState(
                targetValue = if (editMode || slices.dragging) 1f else 0f,
                animationSpec = insetSpec,
                label = "editInset",
            )
            val insetPx = with(density) { HomeDims.EditInset.toPx() }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // The icons fade out while the drawer slides in over the wallpaper.
                        alpha = (1f - drawerProgress.value * 1.25f).coerceIn(0f, 1f)
                    }
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .navigationBarsPadding()
                    .onGloballyPositioned { metrics.setSafe(it.boundsInRoot().toFRect()) },
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val inset = (insetPx * insetFraction.value).roundToInt().coerceIn(0, constraints.maxHeight)
                            val height = constraints.maxHeight - inset
                            val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place(0, inset)
                            }
                        }
                        .onGloballyPositioned { metrics.setPageArea(it.boundsInRoot().toFRect()) },
                    beyondViewportPageCount = if (infinite) 1 else pageCount,
                    userScrollEnabled = !slices.dragging,
                ) { page ->
                    val logical = HomeLayoutMath.logicalPage(page, pageCount, infinite)
                    PageGrid(
                        items = layout.pages.getOrElse(logical) { emptyList() },
                        render = render,
                        cols = cols,
                        rows = rows,
                        hiddenId = hiddenId,
                    )
                }
                HomePageDots(pagerState = pagerState, pageCount = pageCount, infinite = infinite)
                if (settings.showDock) {
                    HomeDockRow(
                        dock = layout.dock,
                        render = render,
                        hiddenId = hiddenId,
                        metrics = metrics,
                        rowHeight = dockHeight,
                    )
                }
                if (settings.showSearchBar) {
                    Spacer(Modifier.height(HomeDims.DockToSearchGap))
                    HomeSearchPill(
                        enabled = !editMode,
                        onClick = {
                            focusSearch = true
                            controller.setDrawerOpen(true)
                        },
                    )
                }
                Spacer(Modifier.height(HomeDims.BottomGap))
            }

            // Overlays, drawn in this order over the content.
            EditChrome(
                env = env,
                onOpenWidgets = { controller.openPicker() },
                onOpenWallpaper = onOpenWallpaperStudio,
                onOpenSettings = onOpenSettings,
            )
            DragHints(env = env, layout = layout, cols = cols, rows = rows, tileDp = tileDp)
            HomeOverlayHost(
                env = env,
                layout = layout,
                apps = appIndex,
                render = render,
                flows = flows,
                drawer = drawer,
                focusSearch = focusSearch || settings.drawerAutoKeyboard,
                cols = cols,
                rows = rows,
            )
        }
    }
}
