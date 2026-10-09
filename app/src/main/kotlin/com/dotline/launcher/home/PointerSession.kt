package com.dotline.launcher.home

import kotlin.math.hypot

/** One pointer of one pointer event, in root pixels. */
data class PointerSample(val id: Long, val x: Float, val y: Float, val pressed: Boolean, val uptimeMs: Long)

/**
 * What the UI layer does after one pointer event: apply [step] (and consume the event when the step
 * says so), move or drop a drag that began elsewhere, consume everything, and stop following the
 * touch when [done].
 */
data class SessionResult(
    val step: GestureStep = GestureStep.Nothing,
    /** A drag that did not start in the engine is carried by this finger: move it here. */
    val externalMove: FPoint? = null,
    /** ... and drop it here (the finger was lifted). */
    val externalEnd: FPoint? = null,
    /** Consume every change of the event (children and the pager must not see an external drag). */
    val consumeAll: Boolean = false,
    /** The last finger is up (or the drag ended): the touch sequence is over. */
    val done: Boolean = false,
)

/**
 * Follows ONE touch sequence, from the first finger down to the last finger up, on top of the
 * [GestureEngine]. It decides which of three modes the sequence is in:
 *
 * - engine mode: the engine owns the gesture (tap, long press, drag, swipe, pinch);
 * - external mode: a drag that began elsewhere (dragged out of the app drawer or an open folder, whose
 *   finger is already down) is carried by this finger, and the engine stays out of it;
 * - disabled mode (the home screen is covered): the sequence is only watched until it ends.
 *
 * Pure logic with plain data in and out, so the Compose pointer loop stays a thin translation layer.
 * The caller calls [GestureEngine.onDown] itself when it starts an engine-mode sequence (it needs
 * hit testing for that), then [begin].
 */
class PointerSession(private val engine: GestureEngine) {
    private var primary = 0L
    private var engineOn = false
    private var external = false
    private var multi = false
    private var finished = false

    /** Position of the primary finger as of the last frame. */
    var x = 0f
        private set
    var y = 0f
        private set

    /** True once a drag from elsewhere is carried by this sequence. */
    val isExternal: Boolean get() = external

    fun begin(primaryId: Long, engineOn: Boolean) {
        primary = primaryId
        this.engineOn = engineOn
        external = false
        multi = false
        finished = false
    }

    /** Milliseconds until the long press fires, or null when no timer is needed. */
    fun nextTimeoutMs(now: Long): Long? =
        if (engineOn && !external && !multi && !finished) engine.nextTimeoutMs(now) else null

    fun onTimeout(now: Long): GestureStep = engine.onTimeout(now)

    /**
     * Feeds one pointer event. [foreignDrag] tells whether a drag exists that the engine did not start
     * (so this finger should carry it).
     */
    fun onFrame(samples: List<PointerSample>, foreignDrag: Boolean): SessionResult {
        var pressedCount = 0
        var primarySample: PointerSample? = null
        var firstSample: PointerSample? = null
        for (s in samples) {
            if (s.pressed) pressedCount++
            if (firstSample == null) firstSample = s
            if (s.id == primary) primarySample = s
        }
        val p = primarySample ?: firstSample ?: return SessionResult(done = true)
        if (p.pressed) {
            x = p.x
            y = p.y
        }

        if (!external && foreignDrag) {
            external = true
            if (engineOn) engine.onCancel()
        }
        if (external) {
            val at = FPoint(p.x, p.y)
            return if (p.pressed) {
                SessionResult(externalMove = at, consumeAll = true)
            } else {
                SessionResult(externalEnd = at, consumeAll = true, done = true)
            }
        }

        if (!engineOn) return SessionResult(done = pressedCount == 0)

        // A second finger starts a pinch, unless the engine already owns the gesture.
        if (!engine.isActive && (multi || pressedCount >= 2)) {
            if (!multi) {
                multi = true
                engine.onSecondPointerDown(distanceOfFirstTwo(samples))
                return SessionResult(done = pressedCount == 0)
            }
            val step = if (pressedCount >= 2) {
                engine.onPinchMove(distanceOfFirstTwo(samples))
            } else {
                engine.onPinchEnd()
                GestureStep.Nothing
            }
            return SessionResult(step = step, done = pressedCount == 0)
        }

        if (finished) return SessionResult(done = pressedCount == 0)

        return if (p.pressed) {
            SessionResult(step = engine.onMove(p.x, p.y, p.uptimeMs))
        } else {
            finished = true
            SessionResult(step = engine.onUp(p.x, p.y, p.uptimeMs), done = pressedCount == 0)
        }
    }

    private fun distanceOfFirstTwo(samples: List<PointerSample>): Float {
        var fx = 0f
        var fy = 0f
        var found = 0
        for (s in samples) {
            if (!s.pressed) continue
            if (found == 0) {
                fx = s.x
                fy = s.y
                found = 1
            } else {
                return hypot(s.x - fx, s.y - fy)
            }
        }
        return 0f
    }
}
