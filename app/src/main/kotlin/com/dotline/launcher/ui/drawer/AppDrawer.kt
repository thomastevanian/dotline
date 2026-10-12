package com.dotline.launcher.ui.drawer

import android.graphics.Rect as AndroidRect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.drawer.DrawerLogic
import com.dotline.launcher.service.NotificationDots
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.theme.DotlineTheme
import kotlinx.coroutines.launch

/** How far, in dp, the grid has to be pulled down past its top to close the drawer. */
private val PullCloseDistance = 96.dp

/**
 * The full-screen black app drawer: an alphabetical grid of apps (a recents row on top when
 * enabled), the A-Z scroller on the right edge and the flat search pill at the bottom. Search runs
 * as you type; Go on the keyboard launches the first match. The pill sits above the keyboard and the
 * grid is laid out above the pill, so the keyboard never covers results.
 *
 * [progress] is 0 (closed) to 1 (open) and is read only inside graphicsLayer, so sliding the drawer
 * never recomposes its contents.
 */
@Composable
fun AppDrawer(
    progress: () -> Float,
    open: Boolean,
    onClose: () -> Unit,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onAddToHome: (AppInfo) -> Unit,
    onStartDrag: (AppInfo, Offset, Offset) -> Unit,
    focusSearchOnOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val settings = LocalSettings.current
    val colors = DotlineTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val apps by graph.apps.apps.collectAsStateWithLifecycle()
    val recents by graph.recents.recent.collectAsStateWithLifecycle()
    val dotKeys by NotificationDots.active.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var press by remember { mutableStateOf<DrawerPress?>(null) }

    val visible = remember(apps, settings.hiddenApps) {
        DrawerLogic.visible(apps, settings.hiddenApps).distinctBy { it.key.flat }
    }
    val results = remember(visible, query) { DrawerLogic.search(visible, query) }
    val searching = query.isNotBlank()
    val sections = remember(results, searching) {
        if (searching) emptyList() else DrawerLogic.sections(results)
    }
    val letters = remember(sections) { DrawerLogic.scrollerLetters(sections) }
    val availableLetters = remember(sections) { sections.map { it.letter }.toSet() }
    val recentApps = remember(recents, visible, settings.drawerShowRecents, searching, settings.gridColumns) {
        if (!settings.drawerShowRecents || searching) {
            emptyList()
        } else {
            val index = visible.associateBy { it.key }
            recents.mapNotNull { index[it] }.take(settings.gridColumns)
        }
    }
    val hasRecents = recentApps.isNotEmpty()
    val showScroller = !searching && results.size >= MIN_APPS_FOR_SCROLLER

    // ---- state that follows the drawer opening and closing --------------------------------------

    LaunchedEffect(open) {
        if (!open) {
            query = ""
            press = null
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    LaunchedEffect(open, focusSearchOnOpen) {
        if (open && focusSearchOnOpen) {
            try {
                focusRequester.requestFocus()
                keyboard?.show()
            } catch (e: Exception) {
                // The field is not attached yet on a very slow frame; the user can tap it.
            }
        }
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(query) { gridState.scrollToItem(0) }

    BackHandler(enabled = press != null) { press = null }
    BackHandler(enabled = press == null && query.isNotEmpty()) {
        query = ""
        focusManager.clearFocus()
    }

    // ---- pull the grid down at its top to close ----------------------------------------------------

    val latestClose by rememberUpdatedState(onClose)
    val pullPx = with(density) { PullCloseDistance.toPx() }
    val pullToClose = remember(pullPx) {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                pulled = if (available.y > 0f) pulled + available.y else 0f
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val close = pulled >= pullPx
                pulled = 0f
                if (close) latestClose()
                return Velocity.Zero
            }
        }
    }

    // ---- UI -------------------------------------------------------------------------------------

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val columns = settings.gridColumns

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                translationY = (1f - p) * size.height
                alpha = if (p <= 0f) 0f else 1f
            }
            .background(colors.background)
            // Never let a touch fall through to the home screen underneath.
            .pointerInput(Unit) { detectTapGestures(onTap = { }) },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullToClose),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = if (showScroller) DrawerScrollerGutter + 4.dp else 12.dp,
                        top = topInset + 16.dp,
                        bottom = 16.dp,
                    ),
                ) {
                    if (hasRecents) {
                        item(key = "recents", span = { GridItemSpan(maxLineSpan) }) {
                            RecentsRow(
                                apps = recentApps,
                                dotKeys = dotKeys,
                                onLaunch = onLaunch,
                                onMenu = { press = it },
                                onDragOut = { p ->
                                    press = null
                                    onStartDrag(p.app, tileCenter(p), halfTile(p))
                                },
                            )
                        }
                    }
                    items(results, key = { app -> app.key.flat }) { app ->
                        DrawerAppCell(
                            app = app,
                            hasDot = dotKeys.contains(NotificationDots.key(app.packageName, app.key.userSerial)),
                            onLaunch = onLaunch,
                            onMenu = { press = it },
                            onDragOut = { p ->
                                press = null
                                onStartDrag(p.app, tileCenter(p), halfTile(p))
                            },
                        )
                    }
                }
                if (results.isEmpty() && searching) {
                    BasicText(
                        text = "No apps found",
                        modifier = Modifier.align(Alignment.Center),
                        style = DotlineTheme.type.label.copy(color = colors.secondary),
                    )
                }
                if (showScroller) {
                    DrawerScroller(
                        letters = letters,
                        available = availableLetters,
                        onLetter = { letter ->
                            val offset = if (hasRecents) 1 else 0
                            val index = DrawerLogic.indexForLetter(sections, letter) + offset
                            scope.launch { gridState.scrollToItem(index) }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = topInset),
                    )
                }
            }
            DrawerSearchBar(
                query = query,
                onQueryChange = { query = it },
                onSubmit = {
                    results.firstOrNull()?.let { first ->
                        keyboard?.hide()
                        onLaunch(first, null)
                    }
                },
                focusRequester = focusRequester,
                modifier = Modifier
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset.coerceAtLeast(8.dp) + 4.dp),
            )
        }

        val menuTarget = press
        if (menuTarget != null) {
            DrawerMenu(
                press = menuTarget,
                onDismiss = { press = null },
                onAddToHome = onAddToHome,
            )
        }
    }
}

/** The scroller is only worth showing when there is something to scroll. */
private const val MIN_APPS_FOR_SCROLLER = 24

private fun tileCenter(p: DrawerPress): Offset =
    Offset((p.tile.left + p.tile.right) / 2f, (p.tile.top + p.tile.bottom) / 2f)

private fun halfTile(p: DrawerPress): Offset = Offset(p.tile.width / 2f, p.tile.height / 2f)

/** Last used apps, one row, with a small mono caption. */
@Composable
private fun RecentsRow(
    apps: List<AppInfo>,
    dotKeys: Set<String>,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onMenu: (DrawerPress) -> Unit,
    onDragOut: (DrawerPress) -> Unit,
) {
    val colors = DotlineTheme.colors
    val columns = LocalSettings.current.gridColumns
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        BasicText(
            text = "RECENT",
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            style = DotlineTheme.type.caption.copy(color = colors.secondary),
        )
        Row(Modifier.fillMaxWidth()) {
            for (app in apps) {
                Box(Modifier.weight(1f)) {
                    DrawerAppCell(
                        app = app,
                        hasDot = dotKeys.contains(NotificationDots.key(app.packageName, app.key.userSerial)),
                        onLaunch = onLaunch,
                        onMenu = onMenu,
                        onDragOut = onDragOut,
                    )
                }
            }
            // Keep the cells the same width as the grid below when fewer apps than columns are shown.
            repeat((columns - apps.size).coerceAtLeast(0)) { Box(Modifier.weight(1f)) }
        }
    }
}
