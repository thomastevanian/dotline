package com.dotline.launcher.ui.home

import android.graphics.Rect as AndroidRect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotline.launcher.data.GestureAction
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.DotPageIndicator
import com.dotline.launcher.ui.components.DottedDivider
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.LocalReduceMotion
import com.dotline.launcher.ui.theme.flatClickable
import kotlin.math.roundToInt

/* ------------------------------------------------------------------------------------------
 * Stage 1 home screen. Layout: pager of fixed app grid pages (page 0 carries the clock block),
 * page dots, dock. Swipe up opens an in-tree All apps overlay, long press opens a flat app menu.
 *
 * Overlay visibility lives in HomeOverlayState and is READ only inside the overlay composables,
 * so opening or closing an overlay never recomposes the clock or the grid.
 * ------------------------------------------------------------------------------------------ */

/** Upward drag distance that counts as a swipe up. */
private val SwipeUpDistance = 56.dp

/** Width of the long-press app menu card. */
private val MenuWidth = 220.dp

/** Press overlay shape of a menu row; inset from the rounded card so it never pokes out of a corner. */
private val MenuRowShape = RoundedCornerShape(16.dp)

/** Open/closed state of the overlays. Only the overlay composables read these fields. */
@Stable
private class HomeOverlayState {
    var drawerOpen: Boolean by mutableStateOf(false)
    var menu: AppMenuTarget? by mutableStateOf<AppMenuTarget?>(null)
}

/** The app a long press happened on and where its icon is (window pixels), or null when unknown. */
@Immutable
private class AppMenuTarget(val app: AppInfo, val anchor: AndroidRect?)

/** Window position of the screen root, so window-space icon bounds can be turned into local ones. */
private class RootOrigin {
    var x: Float = 0f
    var y: Float = 0f
}

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenCrashLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val settings = LocalSettings.current
    val reduceMotion = LocalReduceMotion.current
    val apps by graph.apps.apps.collectAsStateWithLifecycle()

    val overlay = remember { HomeOverlayState() }
    val origin = remember { RootOrigin() }

    val columns = settings.gridColumns
    val rows = settings.gridRows
    val reservedRows = clockReservedRows(settings.hideClock, rows)
    val lists = remember(apps, settings.hiddenApps, settings.showDock, columns, rows, reservedRows) {
        buildHomeAppLists(apps, settings.hiddenApps, settings.showDock, columns, rows, reservedRows)
    }
    val pageCount = lists.pages.size
    val pagerState = rememberPagerState(pageCount = { pageCount })

    val onLaunch = remember(graph) {
        { app: AppInfo, bounds: AndroidRect? ->
            graph.apps.launch(app.key, bounds)
            Unit
        }
    }
    val onLongPress = remember(overlay) {
        { app: AppInfo, bounds: AndroidRect? ->
            overlay.menu = AppMenuTarget(app, bounds)
        }
    }
    val onAppInfo = remember(graph) {
        { app: AppInfo, bounds: AndroidRect? ->
            graph.apps.openAppInfo(app.key, bounds)
            Unit
        }
    }
    val onUninstall = remember(graph) {
        { app: AppInfo ->
            graph.apps.requestUninstall(app.key)
            Unit
        }
    }
    val onSwipeUp = remember(overlay) {
        {
            if (overlay.menu == null) overlay.drawerOpen = true
        }
    }

    // Home button while already home: close everything and return to the first page.
    val reduceMotionNow by rememberUpdatedState(reduceMotion)
    LaunchedEffect(graph, pagerState, overlay) {
        graph.homePressed.collect {
            overlay.drawerOpen = false
            overlay.menu = null
            if (pagerState.currentPage != 0) {
                if (reduceMotionNow) {
                    pagerState.scrollToPage(0)
                } else {
                    pagerState.animateScrollToPage(
                        page = 0,
                        animationSpec = tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing),
                    )
                }
            }
        }
    }

    val dim = settings.wallpaperDim
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (dim > 0f) drawRect(color = Color.Black, alpha = dim)
            }
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                origin.x = position.x
                origin.y = position.y
            },
    ) {
        HomeContent(
            lists = lists,
            pagerState = pagerState,
            columns = columns,
            rows = rows,
            reservedRows = reservedRows,
            showDock = settings.showDock,
            swipeUpEnabled = settings.swipeUp == GestureAction.OPEN_DRAWER,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
            onSwipeUp = onSwipeUp,
        )
        AllAppsOverlay(
            overlay = overlay,
            apps = lists.drawer,
            columns = columns,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
        )
        AppMenuOverlay(
            overlay = overlay,
            origin = origin,
            onAppInfo = onAppInfo,
            onUninstall = onUninstall,
            onOpenSettings = onOpenSettings,
            onOpenCrashLog = onOpenCrashLog,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Home content: pager, page dots, dock
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeContent(
    lists: HomeAppLists,
    pagerState: PagerState,
    columns: Int,
    rows: Int,
    reservedRows: Int,
    showDock: Boolean,
    swipeUpEnabled: Boolean,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onLongPress: (AppInfo, AndroidRect?) -> Unit,
    onSwipeUp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .swipeUpGesture(enabled = swipeUpEnabled, onSwipeUp = onSwipeUp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            HomePage(
                page = page,
                apps = lists.pages.getOrNull(page) ?: emptyList<AppInfo>(),
                columns = columns,
                rows = rows,
                reservedRows = reservedRows,
                onLaunch = onLaunch,
                onLongPress = onLongPress,
            )
        }
        HomePageIndicator(pagerState = pagerState, pageCount = lists.pages.size)
        if (showDock) {
            HomeDock(dock = lists.dock, onLaunch = onLaunch, onLongPress = onLongPress)
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** One pager page: the app grid, plus the clock block on page 0. Both respect the status bar. */
@Composable
private fun HomePage(
    page: Int,
    apps: List<AppInfo>,
    columns: Int,
    rows: Int,
    reservedRows: Int,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onLongPress: (AppInfo, AndroidRect?) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AppGridPage(
            apps = apps,
            columns = columns,
            rows = rows,
            reservedTopRows = if (page == 0) reservedRows else 0,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
        )
        if (page == 0) {
            HomeClockBlock(Modifier.align(Alignment.TopStart))
        }
    }
}

/** Page dots above the dock. The current page is read here so paging recomposes only the dots. */
@Composable
private fun HomePageIndicator(pagerState: PagerState, pageCount: Int) {
    if (pageCount > 1) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            DotPageIndicator(count = pageCount, current = pagerState.currentPage)
        }
    }
}

/** Four icon slots without any background panel. */
@Composable
private fun HomeDock(
    dock: List<AppInfo>,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onLongPress: (AppInfo, AndroidRect?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (slot in 0 until HomeLayout.DOCK_SLOTS) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val app = dock.getOrNull(slot)
                if (app != null) {
                    key(app.key.flat) {
                        AppCell(app = app, onLaunch = onLaunch, onLongPress = onLongPress, showLabel = false)
                    }
                }
            }
        }
    }
}

/** Fires [onSwipeUp] once per gesture when the finger travels up by [SwipeUpDistance]. Horizontal paging wins. */
@Composable
private fun Modifier.swipeUpGesture(enabled: Boolean, onSwipeUp: () -> Unit): Modifier {
    val latest by rememberUpdatedState(onSwipeUp)
    return this.pointerInput(enabled) {
        if (enabled) {
            val threshold = SwipeUpDistance.toPx()
            var total = 0f
            var fired = false
            detectVerticalDragGestures(
                onDragStart = {
                    total = 0f
                    fired = false
                },
                onDragEnd = { total = 0f },
                onDragCancel = { total = 0f },
                onVerticalDrag = { _, dragAmount ->
                    total += dragAmount
                    if (!fired && total < -threshold) {
                        fired = true
                        latest()
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// All apps overlay (Stage 1 drawer)
// ---------------------------------------------------------------------------------------------

/**
 * Full-screen list of every app. Slides up with a one-shot 150 ms ease-out (instant when animations
 * are reduced); the slide is driven from a graphicsLayer lambda, so it costs no recomposition.
 * Closes on back or Home (the screen collects homePressed). Search arrives in a later stage.
 */
@Composable
private fun AllAppsOverlay(
    overlay: HomeOverlayState,
    apps: List<AppInfo>,
    columns: Int,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onLongPress: (AppInfo, AndroidRect?) -> Unit,
) {
    val open = overlay.drawerOpen
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }

    LaunchedEffect(open, reduceMotion) {
        val spec: AnimationSpec<Float> = if (reduceMotion) {
            snap<Float>()
        } else {
            tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)
        }
        if (open) {
            mounted = true
            progress.animateTo(1f, spec)
        } else {
            if (progress.value != 0f) {
                progress.animateTo(0f, spec)
            }
            mounted = false
        }
    }

    BackHandler(enabled = open) {
        overlay.drawerOpen = false
    }

    if (mounted) {
        val launchAndClose = remember(onLaunch, overlay) {
            { app: AppInfo, bounds: AndroidRect? ->
                onLaunch(app, bounds)
                overlay.drawerOpen = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = size.height * (1f - progress.value)
                }
                .background(DotlineTheme.colors.background)
                // Swallow taps so nothing underneath the overlay can be hit.
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { })
                },
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .displayCutoutPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                items(items = apps, key = { app -> app.key.flat }) { app ->
                    AppCell(
                        app = app,
                        onLaunch = launchAndClose,
                        onLongPress = onLongPress,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Long-press app menu
// ---------------------------------------------------------------------------------------------

/**
 * Flat popup drawn in the tree (no Popup window): a dimmed scrim that closes on tap and a card with
 * App info, Uninstall, Settings (Dotline) and Crash log (temporary dev entry). The card sits below the
 * pressed icon, or above it when there is no room.
 */
@Composable
private fun AppMenuOverlay(
    overlay: HomeOverlayState,
    origin: RootOrigin,
    onAppInfo: (AppInfo, AndroidRect?) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCrashLog: () -> Unit,
) {
    val target = overlay.menu
    if (target != null) {
        val reduceMotion = LocalReduceMotion.current
        val enter = remember(target) { Animatable(if (reduceMotion) 1f else 0f) }
        LaunchedEffect(target) {
            if (enter.value < 1f) {
                enter.animateTo(1f, tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing))
            }
        }
        BackHandler {
            overlay.menu = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = enter.value
                }
                .background(DotlineTheme.colors.scrim)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { overlay.menu = null })
                },
        ) {
            MenuPlacement(target = target, origin = origin) {
                MenuCard(
                    onAppInfo = {
                        overlay.menu = null
                        onAppInfo(target.app, target.anchor)
                    },
                    onUninstall = {
                        overlay.menu = null
                        onUninstall(target.app)
                    },
                    onOpenSettings = {
                        overlay.menu = null
                        onOpenSettings()
                    },
                    onOpenCrashLog = {
                        overlay.menu = null
                        onOpenCrashLog()
                    },
                )
            }
        }
    }
}

/** Places its single child below (or above) the pressed icon, clamped to the screen. */
@Composable
private fun MenuPlacement(
    target: AppMenuTarget,
    origin: RootOrigin,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val card = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val margin = 16.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        var x = (width - card.width) / 2
        var y = (height - card.height) / 2
        val anchor = target.anchor
        if (anchor != null) {
            val left = (anchor.left - origin.x).roundToInt()
            val right = (anchor.right - origin.x).roundToInt()
            val top = (anchor.top - origin.y).roundToInt()
            val bottom = (anchor.bottom - origin.y).roundToInt()
            val centreX = (left + right) / 2
            x = (centreX - card.width / 2).coerceIn(margin, maxOf(margin, width - card.width - margin))
            val below = bottom + gap
            y = if (below + card.height + margin <= height) {
                below
            } else {
                maxOf(margin, top - gap - card.height)
            }
        }
        layout(width, height) {
            card.place(x, y)
        }
    }
}

@Composable
private fun MenuCard(
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCrashLog: () -> Unit,
) {
    val colors = DotlineTheme.colors
    val shape = DotlineTheme.shapes.card
    Column(
        modifier = Modifier
            .width(MenuWidth)
            .background(colors.card, shape)
            .border(1.dp, colors.outline, shape)
            // Swallow taps on the card's own padding so they do not close the menu.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { })
            }
            .padding(vertical = 8.dp),
    ) {
        MenuRow(text = "App info", onClick = onAppInfo)
        DottedDivider(Modifier.padding(horizontal = 20.dp))
        MenuRow(text = "Uninstall", onClick = onUninstall)
        DottedDivider(Modifier.padding(horizontal = 20.dp))
        MenuRow(text = "Settings (Dotline)", onClick = onOpenSettings)
        DottedDivider(Modifier.padding(horizontal = 20.dp))
        MenuRow(text = "Crash log (dev)", onClick = onOpenCrashLog)
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .flatClickable(shape = MenuRowShape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        BasicText(
            text = text,
            style = DotlineTheme.type.bodyMedium.copy(color = DotlineTheme.colors.primary),
            maxLines = 1,
        )
    }
}
