package com.dotline.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PointerSessionTest {
    private fun engine(editMode: Boolean = false, pinch: Boolean = true) = GestureEngine(
        GestureConfig(
            touchSlop = 10f, swipeDistance = 120f, editMode = editMode,
            swipeUpEnabled = true, swipeDownEnabled = true, pinchEnabled = pinch, doubleTapEnabled = false,
        ),
    )

    private fun p(id: Long, x: Float, y: Float, pressed: Boolean = true, t: Long = 0L) = PointerSample(id, x, y, pressed, t)

    /** Starts an engine-mode session the way the UI layer does: engine.onDown first, then begin. */
    private fun start(e: GestureEngine, onItem: Boolean = true, x: Float = 100f, y: Float = 100f): PointerSession {
        e.onDown(x, y, 0L, onItem)
        val s = PointerSession(e)
        s.begin(1L, engineOn = true)
        return s
    }

    @Test
    fun tapIsReportedOnUpAndNotConsumed() {
        val e = engine()
        val s = start(e)
        val r = s.onFrame(listOf(p(1, 101f, 100f, pressed = false, t = 80)), foreignDrag = false)
        assertEquals(listOf<GestureOutput>(GestureOutput.Tap(101f, 100f)), r.step.outputs)
        assertFalse(r.step.consume)
        assertTrue(r.done)
    }

    @Test
    fun longPressThroughTheTimerThenReleaseKeepsTheMenuAndConsumesTheUp() {
        val e = engine()
        val s = start(e)
        assertEquals(450L, s.nextTimeoutMs(0L))
        val lp = s.onTimeout(450L)
        assertEquals(listOf<GestureOutput>(GestureOutput.LongPress(100f, 100f, true)), lp.outputs)
        assertNull(s.nextTimeoutMs(500L), "no timer once the long press fired")
        val up = s.onFrame(listOf(p(1, 100f, 100f, pressed = false, t = 600)), foreignDrag = false)
        assertEquals(listOf<GestureOutput>(GestureOutput.LongPressReleased), up.step.outputs)
        assertTrue(up.step.consume)
        assertTrue(up.done)
    }

    @Test
    fun longPressThenDragThenDrop() {
        val e = engine()
        val s = start(e)
        s.onTimeout(450L)
        val move = s.onFrame(listOf(p(1, 150f, 160f, t = 520)), foreignDrag = false)
        assertEquals(GestureOutput.DragStart(100f, 100f), move.step.outputs[0])
        assertEquals(GestureOutput.DragMove(150f, 160f), move.step.outputs[1])
        assertTrue(move.step.consume)
        assertFalse(move.done)
        val end = s.onFrame(listOf(p(1, 160f, 170f, pressed = false, t = 560)), foreignDrag = false)
        assertEquals(listOf<GestureOutput>(GestureOutput.DragEnd(160f, 170f)), end.step.outputs)
        assertTrue(end.done)
    }

    @Test
    fun primaryPositionIsTrackedWhilePressed() {
        val e = engine()
        val s = start(e)
        s.onFrame(listOf(p(1, 105f, 103f, t = 10)), foreignDrag = false)
        assertEquals(105f, s.x)
        assertEquals(103f, s.y)
    }

    @Test
    fun horizontalSwipeIsNeverConsumed() {
        val e = engine()
        val s = start(e)
        val r = s.onFrame(listOf(p(1, 40f, 105f, t = 30)), foreignDrag = false)
        assertTrue(r.step.outputs.isEmpty())
        assertFalse(r.step.consume)
        assertNull(s.nextTimeoutMs(40L), "the long press timer is gone once the finger moved away")
        val end = s.onFrame(listOf(p(1, 20f, 105f, pressed = false, t = 80)), foreignDrag = false)
        assertTrue(end.step.outputs.isEmpty())
        assertTrue(end.done)
    }

    @Test
    fun verticalSwipeUpReportsProgressAndCommits() {
        val e = engine()
        val s = start(e, onItem = false, x = 200f, y = 800f)
        val a = s.onFrame(listOf(p(1, 202f, 700f, t = 40)), foreignDrag = false)
        assertEquals(GestureOutput.VerticalProgress(-100f), a.step.outputs.single())
        assertTrue(a.step.consume)
        val b = s.onFrame(listOf(p(1, 202f, 600f, t = 80)), foreignDrag = false)
        assertEquals(GestureOutput.VerticalProgress(-200f), b.step.outputs.single())
        val up = s.onFrame(listOf(p(1, 202f, 600f, pressed = false, t = 120)), foreignDrag = false)
        assertEquals(GestureOutput.SwipeCommitted(true), up.step.outputs.single())
        assertTrue(up.done)
    }

    @Test
    fun twoFingersPinchInOnceAndEndOnlyWhenBothAreUp() {
        val e = engine()
        val s = start(e, onItem = false, x = 100f, y = 100f)
        // second finger lands 200 px away
        val down2 = s.onFrame(listOf(p(1, 100f, 100f, t = 20), p(2, 300f, 100f, t = 20)), foreignDrag = false)
        assertTrue(down2.step.outputs.isEmpty())
        assertFalse(down2.done)
        assertNull(s.nextTimeoutMs(30L), "no long press while pinching")
        // 200 -> 120: ratio 0.6 <= 0.82
        val pinch = s.onFrame(listOf(p(1, 140f, 100f, t = 60), p(2, 260f, 100f, t = 60)), foreignDrag = false)
        assertEquals(listOf<GestureOutput>(GestureOutput.PinchIn), pinch.step.outputs)
        assertTrue(pinch.step.consume)
        // fires once only
        val again = s.onFrame(listOf(p(1, 160f, 100f, t = 80), p(2, 240f, 100f, t = 80)), foreignDrag = false)
        assertTrue(again.step.outputs.isEmpty())
        // one finger up: not done yet
        val oneUp = s.onFrame(listOf(p(1, 160f, 100f, pressed = false, t = 100), p(2, 240f, 100f, t = 100)), foreignDrag = false)
        assertFalse(oneUp.done)
        val lastUp = s.onFrame(listOf(p(2, 240f, 100f, pressed = false, t = 120)), foreignDrag = false)
        assertTrue(lastUp.done)
        assertTrue(lastUp.step.outputs.isEmpty())
    }

    @Test
    fun pinchDisabledReportsNothing() {
        val e = engine(pinch = false)
        val s = start(e, onItem = false)
        s.onFrame(listOf(p(1, 100f, 100f), p(2, 300f, 100f)), foreignDrag = false)
        val r = s.onFrame(listOf(p(1, 150f, 100f), p(2, 250f, 100f)), foreignDrag = false)
        assertTrue(r.step.outputs.isEmpty())
    }

    @Test
    fun aSecondFingerDoesNotDisturbADragInProgress() {
        val e = engine()
        val s = start(e)
        s.onTimeout(450L)
        s.onFrame(listOf(p(1, 150f, 160f, t = 520)), foreignDrag = false) // drag started
        assertTrue(e.isDragging)
        val withSecond = s.onFrame(listOf(p(1, 170f, 180f, t = 540), p(2, 500f, 500f, t = 540)), foreignDrag = false)
        assertEquals(listOf<GestureOutput>(GestureOutput.DragMove(170f, 180f)), withSecond.step.outputs)
        assertFalse(withSecond.done)
        // the primary lifts first: the drag ends there even though the other finger is still down
        val end = s.onFrame(listOf(p(1, 175f, 185f, pressed = false, t = 560), p(2, 500f, 500f, t = 560)), foreignDrag = false)
        assertEquals(listOf<GestureOutput>(GestureOutput.DragEnd(175f, 185f)), end.step.outputs)
        assertFalse(end.done, "still waiting for the other finger")
        val rest = s.onFrame(listOf(p(2, 500f, 500f, pressed = false, t = 580)), foreignDrag = false)
        assertTrue(rest.done)
        assertTrue(rest.step.outputs.isEmpty())
    }

    @Test
    fun editModeDragStartsImmediatelyOnAnItem() {
        val e = engine(editMode = true)
        val s = start(e)
        val r = s.onFrame(listOf(p(1, 130f, 100f, t = 30)), foreignDrag = false)
        assertEquals(GestureOutput.DragStart(100f, 100f), r.step.outputs[0])
        assertTrue(r.step.consume)
    }

    // ---- a drag that started elsewhere ----

    @Test
    fun aForeignDragIsCarriedByThisFingerAndConsumed() {
        val e = engine()
        val s = start(e) // engine thinks: pending press on an item
        val a = s.onFrame(listOf(p(1, 120f, 130f, t = 30)), foreignDrag = true)
        assertTrue(s.isExternal)
        assertEquals(FPoint(120f, 130f), a.externalMove)
        assertTrue(a.consumeAll)
        assertTrue(a.step.outputs.isEmpty())
        assertNull(s.nextTimeoutMs(40L), "the engine's long press timer is off")
        val b = s.onFrame(listOf(p(1, 140f, 150f, t = 60)), foreignDrag = true)
        assertEquals(FPoint(140f, 150f), b.externalMove)
        val end = s.onFrame(listOf(p(1, 141f, 151f, pressed = false, t = 90)), foreignDrag = true)
        assertEquals(FPoint(141f, 151f), end.externalEnd)
        assertTrue(end.done)
        assertTrue(end.consumeAll)
        // the engine was reset, so a long press can no longer fire from the abandoned press
        assertTrue(e.onTimeout(10_000L).outputs.isEmpty())
    }

    @Test
    fun aForeignDragWorksWhileTheHomeScreenIsCoveredToo() {
        val e = engine()
        val s = PointerSession(e)
        s.begin(7L, engineOn = false)
        assertNull(s.nextTimeoutMs(0L))
        val quiet = s.onFrame(listOf(p(7, 10f, 10f, t = 10)), foreignDrag = false)
        assertTrue(quiet.step.outputs.isEmpty())
        assertNull(quiet.externalMove)
        assertFalse(quiet.done)
        val drag = s.onFrame(listOf(p(7, 40f, 60f, t = 50)), foreignDrag = true)
        assertEquals(FPoint(40f, 60f), drag.externalMove)
        val end = s.onFrame(listOf(p(7, 40f, 60f, pressed = false, t = 90)), foreignDrag = true)
        assertNotNull(end.externalEnd)
        assertTrue(end.done)
    }

    @Test
    fun theEnginesOwnDragIsNeverForeign() {
        // The UI computes foreignDrag as "a drag exists && !engine.isDragging", so the engine's own
        // drag arrives here with foreignDrag = false and is handled by the engine.
        val e = engine()
        val s = start(e)
        s.onTimeout(450L)
        val r = s.onFrame(listOf(p(1, 150f, 160f, t = 520)), foreignDrag = false)
        assertFalse(s.isExternal)
        assertNull(r.externalMove)
        assertTrue(r.step.outputs.isNotEmpty())
    }

    // ---- covered home screen ----

    @Test
    fun aCoveredHomeScreenOnlyWatchesUntilTheLastFingerIsUp() {
        val e = engine()
        val s = PointerSession(e)
        s.begin(1L, engineOn = false)
        assertFalse(s.onFrame(listOf(p(1, 5f, 5f), p(2, 50f, 50f)), false).done)
        assertFalse(s.onFrame(listOf(p(1, 5f, 5f, pressed = false), p(2, 50f, 50f)), false).done)
        assertTrue(s.onFrame(listOf(p(2, 50f, 50f, pressed = false)), false).done)
    }

    @Test
    fun anEventWithoutPointersEndsTheSequence() {
        val s = PointerSession(engine())
        s.begin(1L, engineOn = true)
        assertTrue(s.onFrame(emptyList(), false).done)
    }

    @Test
    fun timerOnlyRunsInEngineModeAndOnlyBeforeAnythingHappened() {
        val e = engine()
        val s = start(e)
        assertEquals(450L, s.nextTimeoutMs(0L))
        // after the finger lifted there is no timer
        s.onFrame(listOf(p(1, 100f, 100f, pressed = false, t = 50)), false)
        assertNull(s.nextTimeoutMs(60L))
    }

    @Test
    fun theSecondFingerIsUsedWhenThePrimaryIsMissingFromAnEvent() {
        val e = engine()
        val s = PointerSession(e)
        s.begin(1L, engineOn = false)
        // the primary id is not in the event: the first pointer stands in, nothing breaks
        val r = s.onFrame(listOf(p(9, 5f, 5f)), false)
        assertFalse(r.done)
    }
}
