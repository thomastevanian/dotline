package com.dotline.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GestureEngineTest {
    private fun engine(
        editMode: Boolean = false,
        up: Boolean = true,
        down: Boolean = true,
        pinch: Boolean = true,
        doubleTap: Boolean = false,
    ) = GestureEngine(
        GestureConfig(
            touchSlop = 10f, swipeDistance = 120f, editMode = editMode,
            swipeUpEnabled = up, swipeDownEnabled = down, pinchEnabled = pinch, doubleTapEnabled = doubleTap,
        ),
    )

    @Test
    fun plainTapIsReportedAndNotConsumed() {
        val e = engine()
        e.onDown(100f, 100f, 0, onItem = true)
        val s = e.onUp(101f, 100f, 80)
        assertEquals(listOf<GestureOutput>(GestureOutput.Tap(101f, 100f)), s.outputs)
        assertFalse(s.consume)
    }

    @Test
    fun longPressThenDragOnItem() {
        val e = engine()
        e.onDown(100f, 100f, 0, onItem = true)
        assertEquals(450L, e.nextTimeoutMs(0))
        assertEquals(50L, e.nextTimeoutMs(400))
        assertTrue(e.onTimeout(300).outputs.isEmpty(), "too early")
        val lp = e.onTimeout(450)
        assertEquals(listOf<GestureOutput>(GestureOutput.LongPress(100f, 100f, true)), lp.outputs)
        assertTrue(lp.consume)
        assertNull(e.nextTimeoutMs(500))
        assertTrue(e.onMove(103f, 102f, 500).outputs.isEmpty(), "within slop: still just held")
        val start = e.onMove(140f, 160f, 520)
        assertEquals(GestureOutput.DragStart(100f, 100f), start.outputs[0])
        assertEquals(GestureOutput.DragMove(140f, 160f), start.outputs[1])
        assertTrue(e.isDragging)
        assertEquals(listOf<GestureOutput>(GestureOutput.DragMove(150f, 170f)), e.onMove(150f, 170f, 540).outputs)
        assertEquals(listOf<GestureOutput>(GestureOutput.DragEnd(150f, 170f)), e.onUp(150f, 170f, 560).outputs)
        assertFalse(e.isActive)
    }

    @Test
    fun longPressReleasedWithoutMovingKeepsMenuOpen() {
        val e = engine()
        e.onDown(50f, 50f, 0, true)
        e.onTimeout(500)
        val up = e.onUp(50f, 50f, 600)
        assertEquals(listOf<GestureOutput>(GestureOutput.LongPressReleased), up.outputs)
        assertTrue(up.consume, "the release must not trigger the item's click")
    }

    @Test
    fun longPressOnEmptySpaceNeverDrags() {
        val e = engine()
        e.onDown(300f, 900f, 0, onItem = false)
        val lp = e.onTimeout(500)
        assertEquals(GestureOutput.LongPress(300f, 900f, false), lp.outputs.single())
        assertTrue(e.onMove(400f, 1000f, 520).outputs.isEmpty())
        assertFalse(e.isDragging)
    }

    @Test
    fun horizontalSwipeIsLeftToThePager() {
        val e = engine()
        e.onDown(500f, 500f, 0, true)
        val s = e.onMove(400f, 505f, 30)
        assertTrue(s.outputs.isEmpty())
        assertFalse(s.consume)
        assertNull(e.nextTimeoutMs(40), "no long press once the finger moved away")
        assertFalse(e.onMove(300f, 505f, 60).consume)
        assertFalse(e.onUp(300f, 505f, 90).consume)
    }

    @Test
    fun verticalSwipeUpCommitsWhenFarEnough() {
        val e = engine()
        e.onDown(500f, 1500f, 0, true)
        val p = e.onMove(502f, 1450f, 50)
        assertEquals(GestureOutput.VerticalProgress(-50f), p.outputs.single())
        assertTrue(p.consume)
        e.onMove(505f, 1300f, 400)
        val up = e.onUp(505f, 1300f, 800)
        assertEquals(GestureOutput.SwipeCommitted(up = true), up.outputs.single())
    }

    @Test
    fun slowShortSwipeCancelsButFastShortSwipeCommits() {
        val slow = engine()
        slow.onDown(500f, 1500f, 0, false)
        slow.onMove(500f, 1450f, 300)
        slow.onMove(500f, 1440f, 900)
        assertEquals(GestureOutput.SwipeCancelled, slow.onUp(500f, 1440f, 1200).outputs.single())

        val fast = engine()
        fast.onDown(500f, 1500f, 0, false)
        fast.onMove(500f, 1480f, 20)
        fast.onMove(500f, 1440f, 40)
        fast.onMove(500f, 1410f, 60)
        assertEquals(GestureOutput.SwipeCommitted(true), fast.onUp(500f, 1400f, 70).outputs.single())
    }

    @Test
    fun swipeDownCommitsAndRespectsTheSwitch() {
        val e = engine()
        e.onDown(500f, 300f, 0, false)
        e.onMove(500f, 360f, 40)
        assertEquals(GestureOutput.SwipeCommitted(up = false), e.onUp(500f, 500f, 200).outputs.single())

        val off = engine(down = false)
        off.onDown(500f, 300f, 0, false)
        val s = off.onMove(500f, 400f, 40)
        assertTrue(s.outputs.isEmpty())
        assertFalse(s.consume)
    }

    @Test
    fun editModeDragsImmediatelyAndDoesNotSwipe() {
        val e = engine(editMode = true)
        e.onDown(100f, 100f, 0, onItem = true)
        val s = e.onMove(100f, 130f, 20)
        assertEquals(GestureOutput.DragStart(100f, 100f), s.outputs[0])
        assertTrue(e.isDragging)

        val empty = engine(editMode = true)
        empty.onDown(300f, 900f, 0, onItem = false)
        val m = empty.onMove(300f, 800f, 30)
        assertTrue(m.outputs.isEmpty(), "no drawer swipe while editing")
        assertFalse(m.consume)
    }

    @Test
    fun cancelEndsDragAndSwipeCleanly() {
        val e = engine()
        e.onDown(0f, 0f, 0, true)
        e.onTimeout(500)
        e.onMove(100f, 100f, 520)
        assertEquals(listOf<GestureOutput>(GestureOutput.DragCancel), e.onCancel().outputs)
        assertFalse(e.isActive)
        assertTrue(e.onCancel().outputs.isEmpty())
    }

    @Test
    fun pinchInFiresOnceAndCancelsLongPress() {
        val e = engine()
        e.onDown(400f, 800f, 0, false)
        e.onSecondPointerDown(400f)
        assertNull(e.nextTimeoutMs(10), "pinch cancels the long press timer")
        assertTrue(e.onPinchMove(380f).outputs.isEmpty())
        assertEquals(GestureOutput.PinchIn, e.onPinchMove(300f).outputs.single())
        assertTrue(e.onPinchMove(200f).outputs.isEmpty(), "only once")
        e.onPinchEnd()

        val off = engine(pinch = false)
        off.onDown(400f, 800f, 0, false)
        off.onSecondPointerDown(400f)
        assertTrue(off.onPinchMove(100f).outputs.isEmpty())
    }

    @Test
    fun doubleTapOnlyWhenEnabledAndQuickEnough() {
        val on = engine(doubleTap = true)
        on.onDown(200f, 200f, 0, false); on.onUp(200f, 200f, 50)
        on.onDown(202f, 201f, 150, false)
        val second = on.onUp(202f, 201f, 200)
        assertTrue(second.outputs.contains(GestureOutput.DoubleTap))

        val slow = engine(doubleTap = true)
        slow.onDown(200f, 200f, 0, false); slow.onUp(200f, 200f, 50)
        slow.onDown(200f, 200f, 800, false)
        assertFalse(slow.onUp(200f, 200f, 850).outputs.contains(GestureOutput.DoubleTap))

        val off = engine(doubleTap = false)
        off.onDown(200f, 200f, 0, false); off.onUp(200f, 200f, 50)
        off.onDown(200f, 200f, 150, false)
        assertFalse(off.onUp(200f, 200f, 200).outputs.contains(GestureOutput.DoubleTap))
    }

    @Test
    fun tripleTapDoesNotFireTwice() {
        val e = engine(doubleTap = true)
        var doubles = 0
        var t = 0L
        repeat(3) {
            e.onDown(200f, 200f, t, false)
            if (e.onUp(200f, 200f, t + 40).outputs.contains(GestureOutput.DoubleTap)) doubles++
            t += 120
        }
        assertEquals(1, doubles)
    }
}
