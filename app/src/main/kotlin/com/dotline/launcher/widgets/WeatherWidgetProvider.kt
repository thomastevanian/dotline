package com.dotline.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import com.dotline.launcher.R
import com.dotline.launcher.data.WeatherUnit
import com.dotline.launcher.data.weather.WeatherFormat
import com.dotline.launcher.data.weather.WeatherParser
import com.dotline.launcher.graph
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Weather from the on-disk cache only: this widget never touches the network. The cache is
 * refreshed (at most every 30 minutes) when the launcher is in the foreground, which then
 * pushes an update here.
 */
class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                graph.settings.loaded.first { it }
                update(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val unit = context.graph.settings.settings.value.weatherUnit
            val snapshot = runCatching {
                WeatherParser.decode(JSONObject(File(context.filesDir, "weather.json").readText()))
            }.getOrNull()
            val bitmap = render(snapshot?.let { WeatherFormat.degrees(it.tempC, unit) },
                snapshot?.kind?.label,
                snapshot?.let { WeatherFormat.degrees(it.highC, unit) + " " + WeatherFormat.degrees(it.lowC, unit) },
                unit)
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_image)
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setOnClickPendingIntent(R.id.widget_root, WidgetSupport.openDotline(context))
                manager.updateAppWidget(id, views)
            }
        }

        @Suppress("UNUSED_PARAMETER")
        private fun render(temp: String?, condition: String?, range: String?, unit: WeatherUnit): Bitmap {
            val w = 560
            val h = 280
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
            val grey = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A8A8A.toInt() }
            if (temp == null) {
                WidgetBitmaps.drawText(canvas, "OPEN", 12f, 70f, 14f, grey)
                WidgetBitmaps.drawText(canvas, "DOTLINE", 12f, 175f, 14f, grey)
                return bmp
            }
            WidgetBitmaps.drawText(canvas, temp, 8f, 8f, 17f, white, 0.7f)
            WidgetBitmaps.drawText(canvas, condition.orEmpty(), 8f, 175f, 7f, grey)
            if (range != null) WidgetBitmaps.drawText(canvas, range, 8f, 225f, 5.5f, grey)
            return bmp
        }
    }
}
