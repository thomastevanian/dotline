package com.dotline.launcher.home

import com.dotline.launcher.data.model.BuiltinWidget

/**
 * Sizes (columns x rows) each built-in widget can be placed at. Each size is a different Nothing
 * style layout: 1x1 = circle, 2x1 or 4x1 = capsule, 2x2 / 4x2 = card.
 */
/** Visual family of a widget at a given size (Nothing OS look). */
enum class WidgetShape { CIRCLE, CAPSULE, CARD }

object WidgetSizes {
    /** 1x1 = circle, one row high = capsule, anything taller = rounded card. */
    fun shapeFor(spanX: Int, spanY: Int): WidgetShape = when {
        spanX == 1 && spanY == 1 -> WidgetShape.CIRCLE
        spanY == 1 -> WidgetShape.CAPSULE
        else -> WidgetShape.CARD
    }

    fun supported(kind: BuiltinWidget): List<Pair<Int, Int>> = when (kind) {
        BuiltinWidget.CLOCK -> listOf(4 to 2, 2 to 2, 4 to 1)
        BuiltinWidget.DATE -> listOf(2 to 1, 4 to 1, 2 to 2)
        BuiltinWidget.WEATHER -> listOf(4 to 2, 2 to 1, 1 to 1)
        BuiltinWidget.BATTERY -> listOf(2 to 2, 2 to 1, 1 to 1)
        BuiltinWidget.CALENDAR -> listOf(4 to 2, 4 to 1, 2 to 2)
        BuiltinWidget.WORLD_CLOCK -> listOf(4 to 2, 4 to 1)
        BuiltinWidget.NOTES -> listOf(2 to 2, 4 to 2, 4 to 1)
    }

    fun default(kind: BuiltinWidget): Pair<Int, Int> = supported(kind).first()

    /** Title shown in the widget picker. */
    fun title(kind: BuiltinWidget): String = when (kind) {
        BuiltinWidget.CLOCK -> "Clock"
        BuiltinWidget.DATE -> "Date"
        BuiltinWidget.WEATHER -> "Weather"
        BuiltinWidget.BATTERY -> "Battery"
        BuiltinWidget.CALENDAR -> "Next event"
        BuiltinWidget.WORLD_CLOCK -> "World clock"
        BuiltinWidget.NOTES -> "Quick note"
    }

    /** Clamps a requested size to the closest supported one (used when restoring odd layouts). */
    fun nearest(kind: BuiltinWidget, spanX: Int, spanY: Int): Pair<Int, Int> =
        supported(kind).minByOrNull { (w, h) -> kotlin.math.abs(w - spanX) + kotlin.math.abs(h - spanY) } ?: (spanX to spanY)
}
