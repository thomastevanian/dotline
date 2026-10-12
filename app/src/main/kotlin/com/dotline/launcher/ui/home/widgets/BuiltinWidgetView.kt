package com.dotline.launcher.ui.home.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dotline.launcher.data.model.BuiltinWidget
import com.dotline.launcher.data.model.WidgetItem
import com.dotline.launcher.home.WidgetSizes

/**
 * Draws one of Dotline's own widgets. The widget fills the box its parent gives it; the span of the
 * item only decides which layout (circle, capsule, card) is drawn. A span that is not one of the
 * supported sizes of the widget (for example from an old backup) is snapped to the nearest one.
 */
@Composable
fun BuiltinWidgetView(item: WidgetItem, modifier: Modifier = Modifier) {
    val kind = item.kind
    val requestedX = item.placement.spanX
    val requestedY = item.placement.spanY
    val span = remember(kind, requestedX, requestedY) { WidgetSizes.nearest(kind, requestedX, requestedY) }
    val spanX = span.first
    val spanY = span.second
    when (kind) {
        BuiltinWidget.CLOCK -> ClockWidget(spanX, spanY, modifier)
        BuiltinWidget.DATE -> DateWidget(spanX, spanY, modifier)
        BuiltinWidget.WEATHER -> WeatherWidget(spanX, spanY, modifier)
        BuiltinWidget.BATTERY -> BatteryWidget(spanX, spanY, modifier)
        BuiltinWidget.CALENDAR -> CalendarWidget(spanX, spanY, modifier)
        BuiltinWidget.WORLD_CLOCK -> WorldClockWidget(spanX, spanY, modifier)
        BuiltinWidget.NOTES -> NotesWidget(spanX, spanY, modifier)
    }
}
