package com.dotline.launcher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.dotline.launcher.MainActivity

object WidgetSupport {
    /** Opens the clock app's alarm list (works on Samsung and Google clocks). */
    fun clockIntent(context: Context): PendingIntent {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun openDotline(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Pushes fresh content into the widgets that show data (battery, weather). No-op when none are placed. */
    fun refreshDataWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        BatteryWidgetProvider.update(context, mgr, ids(context, mgr, BatteryWidgetProvider::class.java))
        WeatherWidgetProvider.update(context, mgr, ids(context, mgr, WeatherWidgetProvider::class.java))
    }

    private fun ids(context: Context, mgr: AppWidgetManager, cls: Class<*>): IntArray =
        mgr.getAppWidgetIds(ComponentName(context, cls))
}
