package com.dotline.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.RemoteViews
import com.dotline.launcher.R

/**
 * Battery ring. The sticky ACTION_BATTERY_CHANGED intent is read once per update (no receiver is
 * registered). Updates follow the system schedule (updatePeriodMillis) and are also pushed whenever
 * the launcher comes to the foreground.
 */
class BatteryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val bitmap = WidgetBitmaps.batteryRing(
                percent = percent, charging = charging, sizePx = 360,
                lit = 0xFFFFFFFF.toInt(), unlit = 0xFF3A3A3A.toInt(), accent = 0xFFD71921.toInt(),
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_image)
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setOnClickPendingIntent(R.id.widget_root, WidgetSupport.openDotline(context))
                manager.updateAppWidget(id, views)
            }
        }
    }
}
