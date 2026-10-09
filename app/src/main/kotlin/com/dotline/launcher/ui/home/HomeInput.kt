package com.dotline.launcher.ui.home

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.GestureAction
import com.dotline.launcher.data.Settings
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.FolderItem
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.HostedWidgetItem
import com.dotline.launcher.home.DragSource
import com.dotline.launcher.home.GestureConfig
import com.dotline.launcher.home.GestureEngine
import com.dotline.launcher.home.GestureOutput
import com.dotline.launcher.home.GestureStep
import com.dotline.launcher.home.HitItem
import com.dotline.launcher.home.HomeController
import com.dotline.launcher.home.HomeGeometry
import com.dotline.launcher.home.HomeLayoutMath
import com.dotline.launcher.home.HomeMenu
import com.dotline.launcher.home.ScrollRequest
import com.dotline.launcher.home.ZoneSet
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/* ------------------------------------------------------------------------------------------
 * ONE pointer handler for the whole home screen.
 *
 * It sits on the full-screen root and watches every touch in the Initial pass, before the
 * children and the pager see it. All decisions are made by the pure GestureEngine (what is a tap,
 * a long press, a drag, a swipe, a pinch) and HomeController (what a drag does); this file only
 * feeds them events and renders their answers. Events are consumed only when the engine says so,
 * so the pager and the items' own clicks keep working.
 * ------------------------------------------------------------------------------------------ */

/** Distance a vertical swipe has to travel to count, in dp. */
private val SwipeDistance = 96.dp

/** A long press is never shorter than this, whatever the system setting says. */
private const val MIN_LONG_PRESS_MS = 350L

/**
 * Everything the pointer handler needs. All inputs are read live (at event time) through the
 * providers, so the host is created once and the pointer handler never restarts.
 */
@Stable
internal class HomeGestureHost(
    val controller: HomeController,
    val engine: GestureEngine,
    private val metrics: HomeMetrics,
    private val pager: HomePager,
    private val drawer: DrawerController,
    private val actions: HomeActions,
    private val settingsProvider: () -> Settings,
    private val layoutProvider: () -> HomeLayout,
    private val enabledProvider: () -> Boolean,
    private val haptic: () -> Unit,
) {
    /** Last known finger position in root px. A drag that starts inside a child (folder popup) needs it. */
    var pointerX: Float = 0f
    var pointerY: Float = 0f

    /** The item that was under the finger when the long press fired. */
    private var pressedItem: HitItem? = null

    private var zoneSource: DragSource? = null
    private var zoneSet: ZoneSet = ZoneSet.NONE

    /** False while something covers the home screen (drawer, picker, menu, folder, panels). */
    fun isEnabled(): Boolean = enabledProvider()

    // ---- queries ----------------------------------------------------------------------------

    private fun geometry(zones: ZoneSet): HomeGeometry? {
        val s = settingsProvider()
        return metrics.geometry(s.gridColumns, s.gridRows, zones)
    }

    private fun zonesFor(source: DragSource): ZoneSet {
        if (source !== zoneSource) {
            zoneSource = source
            zoneSet = HomeLayoutMath.zoneSetFor(source, layoutProvider())
        }
        return zoneSet
    }

    /** The home item under a point (dock item or grid item of the current page), or null. */
    fun itemAt(x: Float, y: Float): HitItem? {
        if (metrics.isOverChrome(x, y)) return null
        val geo = geometry(ZoneSet.NONE) ?: return null
        return HomeLayoutMath.itemAt(geo, layoutProvider(), pager.logicalPage, x, y)
    }

    /** True when a drag exists that this pointer handler's engine did not start (drawer, folder popup). */
    fun hasForeignDrag(): Boolean = controller.ui.value.drag != null && !engine.isDragging

    // ---- gesture life cycle -----------------------------------------------------------------

    /** A finger went down on an enabled home screen: configure and start the engine. */
    fun beginGesture(x: Float, y: Float, uptime: Long, touchSlop: Float, swipePx: Float, longPressMs: Long) {
        pressedItem = null
        staleDragCheck()
        val hit = itemAt(x, y)
        engine.config = buildConfig(touchSlop, swipePx, longPressMs, hit)
        engine.onDown(x, y, uptime, hit != null)
    }

    /** A finger went down while the home screen is covered. No drag can be running at this point. */
    fun staleDragCheck() {
        if (controller.ui.value.drag != null) controller.cancelDrag()
    }

    private fun buildConfig(touchSlop: Float, swipePx: Float, longPressMs: Long, hit: HitItem?): GestureConfig {
        val s = settingsProvider()
        val editing = controller.ui.value.editMode && !s.lockLayout
        // A hosted widget may scroll on its own: leave vertical movement to it.
        val overHosted = hit != null && hit.item is HostedWidgetItem
        return GestureConfig(
            touchSlop = touchSlop,
            swipeDistance = swipePx,
            longPressMs = longPressMs,
            editMode = editing,
            swipeUpEnabled = s.swipeUp != GestureAction.NONE && !overHosted,
            swipeDownEnabled = s.swipeDown != GestureAction.NONE && !overHosted,
            pinchEnabled = s.pinch != GestureAction.NONE,
            doubleTapEnabled = s.doubleTap != GestureAction.NONE,
        )
    }

    /** The gesture is over (or was cancelled): whatever the engine still owns is dropped cleanly. */
    fun endGesture(externalDrag: Boolean) {
        val step = engine.onCancel()
        for (o in step.outputs) handle(o)
        if (externalDrag && controller.ui.value.drag != null) controller.cancelDrag()
    }

    // ---- engine output ----------------------------------------------------------------------

    /** Applies a step: runs its outputs and consumes the event when the engine asks for it. */
    fun apply(step: GestureStep, event: PointerEvent?) {
        for (o in step.outputs) handle(o)
        if (step.consume && event != null) {
            for (c in event.changes) c.consume()
        }
    }

    private fun handle(o: GestureOutput) {
        when (o) {
            is GestureOutput.Tap -> onTap(o.x, o.y)
            is GestureOutput.LongPress -> onLongPress(o.x, o.y, o.onItem)
            is GestureOutput.LongPressReleased -> Unit
            is GestureOutput.DragStart -> onDragStart(o.x, o.y)
            is GestureOutput.DragMove -> dragMove(o.x, o.y)
            is GestureOutput.DragEnd -> dragEnd(o.x, o.y)
            is GestureOutput.DragCancel -> controller.cancelDrag()
            is GestureOutput.VerticalProgress -> onVertical(o.dy)
            is GestureOutput.SwipeCommitted -> onSwipeCommitted(o.up)
            is GestureOutput.SwipeCancelled -> drawer.settle(false)
            is GestureOutput.PinchIn -> {
                val s = settingsProvider()
                haptic()
                actions.perform(s.pinch, s.lockLayout)
            }
            is GestureOutput.DoubleTap -> onDoubleTap()
        }
    }

    private fun onTap(x: Float, y: Float) {
        val ui = controller.ui.value
        if (!ui.editMode && ui.menu == null && ui.openFolderId == null) return
        if (itemAt(x, y) != null) return
        if (metrics.isOverChrome(x, y)) return
        controller.closeTopmost()
    }

    private fun onDoubleTap() {
        // Only empty space counts: two quick taps on an icon launch it, they never lock the screen.
        if (itemAt(pointerX, pointerY) != null) return
        val s = settingsProvider()
        actions.perform(s.doubleTap, s.lockLayout)
    }

    private fun onLongPress(x: Float, y: Float, onItem: Boolean) {
        val s = settingsProvider()
        haptic()
        if (!onItem) {
            if (!s.lockLayout && !controller.ui.value.editMode) controller.enterEdit()
            return
        }
        val hit = itemAt(x, y) ?: return
        pressedItem = hit
        val item = hit.item
        val menu: HomeMenu? = when (item) {
            is AppItem -> HomeMenu.App(item.id, item.app, hit.rect)
            is FolderItem -> HomeMenu.Folder(item.id, hit.rect)
            else -> if (s.lockLayout) null else HomeMenu.Widget(item.id, hit.rect)
        }
        if (menu != null) controller.showMenu(menu)
    }

    // ---- dragging ---------------------------------------------------------------------------

    private fun onDragStart(x: Float, y: Float) {
        if (settingsProvider().lockLayout) return
        val hit = pressedItem ?: itemAt(x, y) ?: return
        val source = DragSource.OnHome(hit.item.id)
        val geo = geometry(zonesFor(source)) ?: return
        if (pressedItem == null) haptic()
        controller.beginDrag(source, x - hit.rect.left, y - hit.rect.top, x, y, geo, pager.logicalPage)
    }

    private fun dragMove(x: Float, y: Float) {
        val drag = controller.ui.value.drag ?: return
        val geo = geometry(zonesFor(drag.source)) ?: return
        val request = controller.updateDrag(x, y, geo, pager.logicalPage, SystemClock.uptimeMillis())
        if (request != ScrollRequest.NONE) pager.scrollBy(request)
    }

    private fun dragEnd(x: Float, y: Float) {
        val drag = controller.ui.value.drag ?: return
        val geo = geometry(zonesFor(drag.source))
        if (geo == null) {
            controller.cancelDrag()
            return
        }
        val outcome = controller.endDrag(x, y, geo, pager.logicalPage)
        actions.applyDrop(outcome)
    }

    /** A drag started elsewhere (drawer, folder popup) is moved by the finger that is still down. */
    fun externalMove(x: Float, y: Float) {
        dragMove(x, y)
    }

    fun externalEnd(x: Float, y: Float) {
        dragEnd(x, y)
    }

    /** An app was long-pressed inside the open folder: lift it out and let the finger carry it. */
    fun beginFolderDrag(folderId: String, app: AppInfo, rootCenter: Offset, rootTopLeft: Offset) {
        if (settingsProvider().lockLayout || controller.ui.value.drag != null) return
        val source = DragSource.FromFolder(folderId, app.key)
        val geo = geometry(zonesFor(source)) ?: return
        val halfW = (rootCenter.x - rootTopLeft.x).coerceAtLeast(1f)
        val halfH = (rootCenter.y - rootTopLeft.y).coerceAtLeast(1f)
        val grabX = (pointerX - rootTopLeft.x).coerceIn(0f, halfW * 2f)
        val grabY = (pointerY - rootTopLeft.y).coerceIn(0f, halfH * 2f)
        haptic()
        controller.beginDrag(source, grabX, grabY, pointerX, pointerY, geo, pager.logicalPage)
    }

    /** An app is being dragged out of the drawer by the finger at [pointer] (root px). */
    fun beginDrawerDrag(app: AppInfo, pointer: Offset, grab: Offset) {
        if (settingsProvider().lockLayout || controller.ui.value.drag != null) return
        val source = DragSource.NewApp(app.key)
        val geo = geometry(zonesFor(source)) ?: return
        controller.beginDrag(source, grab.x, grab.y, pointer.x, pointer.y, geo, pager.logicalPage)
    }

    // ---- swipes -----------------------------------------------------------------------------

    private fun onVertical(dy: Float) {
        if (settingsProvider().swipeUp != GestureAction.OPEN_DRAWER) return
        // Dragging back down (or a downward swipe) leaves the drawer closed.
        if (dy >= 0f && drawer.progress.value <= 0f) return
        val travel = metrics.screen.height * 0.55f
        if (travel <= 0f) return
        drawer.follow((-dy).coerceAtLeast(0f) / travel)
    }

    private fun onSwipeCommitted(up: Boolean) {
        val s = settingsProvider()
        if (up) {
            actions.perform(s.swipeUp, s.lockLayout)
        } else {
            actions.perform(s.swipeDown, s.lockLayout)
        }
    }
}

/** Puts the home gesture handler on a full-screen root. */
internal fun Modifier.homeGestures(host: HomeGestureHost): Modifier =
    this.pointerInput(host) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                trackGesture(host, down)
            }
        }
    }

/** Distance between the first two pressed pointers of an event, 0 when there are fewer than two. */
private fun pointerDistance(changes: List<PointerInputChange>): Float {
    var firstX = 0f
    var firstY = 0f
    var found = 0
    for (c in changes) {
        if (!c.pressed) continue
        if (found == 0) {
            firstX = c.position.x
            firstY = c.position.y
            found = 1
        } else {
            return hypot(c.position.x - firstX, c.position.y - firstY)
        }
    }
    return 0f
}

private fun consumeAll(event: PointerEvent) {
    for (c in event.changes) c.consume()
}

/**
 * Follows one touch sequence from the first finger down to the last finger up. Three modes:
 * the engine owns the gesture (normal), a drag that began elsewhere is carried by this finger
 * (external), or the home screen is covered and the sequence is only watched (disabled).
 */
private suspend fun AwaitPointerEventScope.trackGesture(host: HomeGestureHost, down: PointerInputChange) {
    val engine = host.engine
    val primary = down.id
    var x = down.position.x
    var y = down.position.y
    host.pointerX = x
    host.pointerY = y

    val engineOn = host.isEnabled()
    var external = false
    var multi = false
    var finished = false
    if (engineOn) {
        val longPress = viewConfiguration.longPressTimeoutMillis.coerceAtLeast(MIN_LONG_PRESS_MS)
        host.beginGesture(x, y, down.uptimeMillis, viewConfiguration.touchSlop, SwipeDistance.toPx(), longPress)
    } else {
        host.staleDragCheck()
    }

    try {
        while (true) {
            val waitMs: Long? = if (engineOn && !external && !multi && !finished) {
                engine.nextTimeoutMs(SystemClock.uptimeMillis())
            } else {
                null
            }
            val event: PointerEvent? = if (waitMs != null) {
                withTimeoutOrNull(waitMs) { awaitPointerEvent(PointerEventPass.Initial) }
            } else {
                awaitPointerEvent(PointerEventPass.Initial)
            }
            if (event == null) {
                // The long-press timer elapsed without any event.
                host.apply(engine.onTimeout(SystemClock.uptimeMillis()), null)
                continue
            }

            val changes = event.changes
            val pressedCount = changes.count { it.pressed }
            val primaryChange = changes.firstOrNull { it.id == primary } ?: changes.firstOrNull()
            if (primaryChange == null) {
                if (pressedCount == 0) break
                continue
            }
            x = primaryChange.position.x
            y = primaryChange.position.y
            if (primaryChange.pressed) {
                host.pointerX = x
                host.pointerY = y
            }

            // A drag that began outside this handler (drawer, folder popup): this finger carries it.
            if (!external && host.hasForeignDrag()) {
                external = true
                if (engineOn) engine.onCancel()
            }
            if (external) {
                if (primaryChange.pressed) host.externalMove(x, y) else host.externalEnd(x, y)
                consumeAll(event)
                if (!primaryChange.pressed) break
                continue
            }

            if (!engineOn) {
                if (pressedCount == 0) break
                continue
            }

            // A second finger: a pinch, unless the engine already owns the gesture.
            if (!engine.isActive && (multi || pressedCount >= 2)) {
                if (!multi) {
                    multi = true
                    engine.onSecondPointerDown(pointerDistance(changes))
                } else if (pressedCount >= 2) {
                    host.apply(engine.onPinchMove(pointerDistance(changes)), event)
                } else {
                    engine.onPinchEnd()
                }
                if (pressedCount == 0) break
                continue
            }

            if (finished) {
                if (pressedCount == 0) break
                continue
            }

            if (primaryChange.pressed) {
                host.apply(engine.onMove(x, y, primaryChange.uptimeMillis), event)
            } else {
                host.apply(engine.onUp(x, y, primaryChange.uptimeMillis), event)
                finished = true
                if (pressedCount == 0) break
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CrashLog.record("home gesture", e)
    } finally {
        host.endGesture(external)
    }
}
