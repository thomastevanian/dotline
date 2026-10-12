package com.dotline.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.dotline.launcher.R

/** Dot-matrix clock. Uses TextClock so the host keeps it ticking: no updates, no alarms, no service. */
class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setOnClickPendingIntent(R.id.widget_root, WidgetSupport.clockIntent(context))
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

/** Weekday and date in dot-matrix type, also self-updating TextClocks. */
class DateWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_date)
            views.setOnClickPendingIntent(R.id.widget_root, WidgetSupport.openDotline(context))
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
