package com.dotline.launcher.home

import com.dotline.launcher.data.model.BuiltinWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetSizesTest {
    @Test
    fun everyWidgetHasSizesThatFitTheSmallestGrid() {
        for (kind in BuiltinWidget.entries) {
            val sizes = WidgetSizes.supported(kind)
            assertTrue(sizes.isNotEmpty(), "$kind has sizes")
            assertTrue(sizes.all { (w, h) -> w in 1..4 && h in 1..2 }, "$kind fits a 4 column grid")
            assertEquals(sizes.first(), WidgetSizes.default(kind))
            assertTrue(WidgetSizes.title(kind).isNotBlank())
        }
    }

    @Test
    fun nearestSnapsToSupported() {
        assertEquals(1 to 1, WidgetSizes.nearest(BuiltinWidget.WEATHER, 1, 1))
        assertEquals(4 to 2, WidgetSizes.nearest(BuiltinWidget.WEATHER, 5, 3))
    }

    @Test
    fun shapesFollowTheSize() {
        assertEquals(WidgetShape.CIRCLE, WidgetSizes.shapeFor(1, 1))
        assertEquals(WidgetShape.CAPSULE, WidgetSizes.shapeFor(2, 1))
        assertEquals(WidgetShape.CAPSULE, WidgetSizes.shapeFor(4, 1))
        assertEquals(WidgetShape.CARD, WidgetSizes.shapeFor(2, 2))
        assertEquals(WidgetShape.CARD, WidgetSizes.shapeFor(4, 2))
    }
}
