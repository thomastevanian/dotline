package com.dotline.launcher.home

import kotlin.math.abs
import kotlin.math.hypot

/** Thresholds and switches for [GestureEngine]. Distances are pixels, times are milliseconds. */
data class GestureConfig(
    val touchSlop: Float,
    val swipeDistance: Float,
    val longPressMs: Long = 450L,
    val doubleTapMs: Long = 280L,
    /** Finger speed (px/s) that commits a swipe even when it is short. */
    val swipeVelocity: Float = 1100f,
    /** Two-finger distance ratio (now / at start) below which a pinch-in is reported. */
    val pinchRatio: Float = 0.82f,
    /** Edit mode: pressing on an item and moving beyond the slop drags it immediately. */
    val editMode: Boolean = false,
    val swipeUpEnabled: Boolean = true,
    val swipeDownEnabled: Boolean = true,
    val pinchEnabled: Boolean = true,
    val doubleTapEnabled: Boolean = false,
)

/** What the engine asks the UI to do. */
sealed interface GestureOutput {
    /** A short press that stayed in place. Informational: the item's own click still fires. */
    data class Tap(val x: Float, val y: Float) : GestureOutput
    /** Held in place for the long-press time. [onItem] says whether an item was under the finger. */
    data class LongPress(val x: Float, val y: Float, val onItem: Boolean) : GestureOutput
    /** The finger was lifted after a long press without dragging (menus stay open). */
    data object LongPressReleased : GestureOutput
    data class DragStart(val x: Float, val y: Float) : GestureOutput
    data class DragMove(val x: Float, val y: Float) : GestureOutput
    data class DragEnd(val x: Float, val y: Float) : GestureOutput
    data object DragCancel : GestureOutput
    /** Live vertical travel since the finger went down (negative = up). */
    data class VerticalProgress(val dy: Float) : GestureOutput
    data class SwipeCommitted(val up: Boolean) : GestureOutput
    data object SwipeCancelled : GestureOutput
    data object PinchIn : GestureOutput
    data object DoubleTap : GestureOutput
}

/**
 * Result of feeding an event: what happened, and whether the UI should CONSUME the pointer
 * events (so children and the pager no longer see them). Horizontal swipes are never consumed so
 * the pager keeps working.
 */
data class GestureStep(val outputs: List<GestureOutput>, val consume: Boolean) {
    companion object {
        val Nothing = GestureStep(emptyList(), false)
    }
}

/**
 * Pure state machine behind the home screen's touch handling. It never touches Compose or Android:
 * the UI feeds it down/move/up/timeout events with timestamps, and acts on the returned outputs.
 * One finger at a time, plus a two-finger pinch.
 */
class GestureEngine(var config: GestureConfig) {
    private enum class Phase { IDLE, PENDING, PASSIVE, LONG_PRESSED, DRAGGING, VERTICAL, PINCHING }

    private var phase = Phase.IDLE
    private var downX = 0f
    private var downY = 0f
    private var downT = 0L
    private var onItem = false
    private var lastX = 0f
    private var lastY = 0f
    private var pinchStart = 0f
    private var pinchFired = false

    private var lastTapT = Long.MIN_VALUE / 2
    private var lastTapX = 0f
    private var lastTapY = 0f

    // Last few samples of the y position for the swipe velocity.
    private val sampleT = LongArray(SAMPLES)
    private val sampleY = FloatArray(SAMPLES)
    private var sampleCount = 0

    /** True while the engine owns the gesture and events should be consumed. */
    val isActive: Boolean
        get() = phase == Phase.LONG_PRESSED || phase == Phase.DRAGGING || phase == Phase.VERTICAL

    val isDragging: Boolean get() = phase == Phase.DRAGGING

    fun onDown(x: Float, y: Float, t: Long, onItem: Boolean): GestureStep {
        phase = Phase.PENDING
        downX = x; downY = y; downT = t
        lastX = x; lastY = y
        this.onItem = onItem
        pinchFired = false
        sampleCount = 0
        addSample(t, y)
        return GestureStep.Nothing
    }

    /** Milliseconds until the long press fires, or null when no timer is needed. */
    fun nextTimeoutMs(now: Long): Long? =
        if (phase == Phase.PENDING) (downT + config.longPressMs - now).coerceAtLeast(0L) else null

    /** Call when the long-press timer elapsed (or any time later: it re-checks the clock). */
    fun onTimeout(now: Long): GestureStep {
        if (phase != Phase.PENDING || now - downT < config.longPressMs) return GestureStep.Nothing
        phase = Phase.LONG_PRESSED
        return GestureStep(listOf(GestureOutput.LongPress(downX, downY, onItem)), consume = true)
    }

    fun onMove(x: Float, y: Float, t: Long): GestureStep {
        lastX = x; lastY = y
        addSample(t, y)
        val dx = x - downX
        val dy = y - downY
        return when (phase) {
            Phase.PENDING -> {
                if (hypot(dx, dy) <= config.touchSlop) return GestureStep.Nothing
                if (config.editMode && onItem) {
                    phase = Phase.DRAGGING
                    return GestureStep(listOf(GestureOutput.DragStart(downX, downY), GestureOutput.DragMove(x, y)), true)
                }
                val vertical = abs(dy) > abs(dx) * 1.2f
                val allowed = if (dy < 0f) config.swipeUpEnabled else config.swipeDownEnabled
                if (vertical && allowed && !config.editMode) {
                    phase = Phase.VERTICAL
                    GestureStep(listOf(GestureOutput.VerticalProgress(dy)), true)
                } else {
                    phase = Phase.PASSIVE
                    GestureStep.Nothing
                }
            }
            Phase.LONG_PRESSED -> {
                if (onItem || config.editMode) {
                    if (hypot(dx, dy) > config.touchSlop) {
                        phase = Phase.DRAGGING
                        GestureStep(listOf(GestureOutput.DragStart(downX, downY), GestureOutput.DragMove(x, y)), true)
                    } else {
                        GestureStep(emptyList(), true)
                    }
                } else {
                    // Long press on empty space: the UI entered edit mode; further movement is ignored.
                    GestureStep(emptyList(), true)
                }
            }
            Phase.DRAGGING -> GestureStep(listOf(GestureOutput.DragMove(x, y)), true)
            Phase.VERTICAL -> GestureStep(listOf(GestureOutput.VerticalProgress(dy)), true)
            else -> GestureStep.Nothing
        }
    }

    fun onUp(x: Float, y: Float, t: Long): GestureStep {
        addSample(t, y)
        val previous = phase
        phase = Phase.IDLE
        return when (previous) {
            Phase.PENDING -> {
                val outputs = ArrayList<GestureOutput>(2)
                outputs += GestureOutput.Tap(x, y)
                val near = hypot(x - lastTapX, y - lastTapY) <= config.touchSlop * 3f
                if (config.doubleTapEnabled && t - lastTapT <= config.doubleTapMs && near) {
                    outputs += GestureOutput.DoubleTap
                    lastTapT = Long.MIN_VALUE / 2
                } else {
                    lastTapT = t; lastTapX = x; lastTapY = y
                }
                GestureStep(outputs, false)
            }
            Phase.LONG_PRESSED -> GestureStep(listOf(GestureOutput.LongPressReleased), true)
            Phase.DRAGGING -> GestureStep(listOf(GestureOutput.DragEnd(x, y)), true)
            Phase.VERTICAL -> {
                val dy = y - downY
                val up = dy < 0f
                val vy = velocityY()
                val fastEnough = if (up) vy <= -config.swipeVelocity else vy >= config.swipeVelocity
                val farEnough = abs(dy) >= config.swipeDistance
                GestureStep(listOf(if (farEnough || fastEnough) GestureOutput.SwipeCommitted(up) else GestureOutput.SwipeCancelled), true)
            }
            else -> GestureStep.Nothing
        }
    }

    /** The system cancelled the gesture (or the UI lost it). */
    fun onCancel(): GestureStep {
        val previous = phase
        phase = Phase.IDLE
        return when (previous) {
            Phase.DRAGGING -> GestureStep(listOf(GestureOutput.DragCancel), true)
            Phase.VERTICAL -> GestureStep(listOf(GestureOutput.SwipeCancelled), true)
            Phase.LONG_PRESSED -> GestureStep(listOf(GestureOutput.LongPressReleased), true)
            else -> GestureStep.Nothing
        }
    }

    /** A second finger touched down: a pinch begins (cancels any pending long press). */
    fun onSecondPointerDown(distance: Float): GestureStep {
        if (phase == Phase.PENDING || phase == Phase.PASSIVE) {
            phase = Phase.PINCHING
            pinchStart = distance
            pinchFired = false
        }
        return GestureStep.Nothing
    }

    /** Two-finger distance changed. Reports [GestureOutput.PinchIn] once per gesture. */
    fun onPinchMove(distance: Float): GestureStep {
        if (phase != Phase.PINCHING || pinchFired || pinchStart <= 0f) return GestureStep.Nothing
        if (config.pinchEnabled && distance / pinchStart <= config.pinchRatio) {
            pinchFired = true
            return GestureStep(listOf(GestureOutput.PinchIn), true)
        }
        return GestureStep.Nothing
    }

    /** Back to a single finger after a pinch: ignore the rest of the gesture. */
    fun onPinchEnd() {
        if (phase == Phase.PINCHING) phase = Phase.PASSIVE
    }

    private fun addSample(t: Long, y: Float) {
        if (sampleCount == SAMPLES) {
            for (i in 1 until SAMPLES) { sampleT[i - 1] = sampleT[i]; sampleY[i - 1] = sampleY[i] }
            sampleCount--
        }
        sampleT[sampleCount] = t
        sampleY[sampleCount] = y
        sampleCount++
    }

    /** Vertical speed in px/s over the last few samples. */
    private fun velocityY(): Float {
        if (sampleCount < 2) return 0f
        val dt = sampleT[sampleCount - 1] - sampleT[0]
        if (dt <= 0L) return 0f
        return (sampleY[sampleCount - 1] - sampleY[0]) * 1000f / dt
    }

    private companion object {
        const val SAMPLES = 5
    }
}
