package com.dotline.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetHostMathTest {
    @Test
    fun smallWidgetIsOneByOne() {
        assertEquals(1 to 1, WidgetHostMath.spanFor(40, 40, 77f, 100f, 5, 6))
        assertEquals(1 to 1, WidgetHostMath.spanFor(70, 70, 77f, 100f, 5, 6))
    }

    @Test
    fun wideWidgetSpansFourColumns() {
        assertEquals(4 to 1, WidgetHostMath.spanFor(250, 40, 77f, 100f, 5, 6))
    }

    @Test
    fun sizeIsClampedToTheGrid() {
        assertEquals(4 to 6, WidgetHostMath.spanFor(900, 900, 77f, 100f, 4, 6))
    }

    @Test
    fun unmeasuredGridFallsBack() {
        assertEquals(2 to 2, WidgetHostMath.spanFor(110, 110, Float.NaN, 0f, 5, 6))
    }

    @Test
    fun zeroOrNegativeMinimumIsOneCell() {
        assertEquals(1 to 1, WidgetHostMath.spanFor(0, -5, 77f, 100f, 5, 6))
    }
}
