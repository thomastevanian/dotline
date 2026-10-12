package com.dotline.launcher.service

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Opens system screens and performs the few system actions a launcher is allowed to trigger. */
object SystemActions {
    /**
     * Pulls down the notification shade through the framework StatusBarManager (needs the normal
     * EXPAND_STATUS_BAR permission). Returns false where the platform refuses, so the UI can hide
     * the option.
     */
    fun expandNotifications(context: Context): Boolean = runCatching {
        val service = context.getSystemService("statusbar") ?: return false
        val method = Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel")
        method.invoke(service)
        true
    }.getOrDefault(false)

    /** Quick-settings variant of the above. */
    fun expandQuickSettings(context: Context): Boolean = runCatching {
        val service = context.getSystemService("statusbar") ?: return false
        val method = Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel")
        method.invoke(service)
        true
    }.getOrDefault(false)

    fun notificationAccessSettings(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun accessibilitySettings(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun writeSettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun displaySettings(): Intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun developerSettings(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun deviceInfoSettings(): Intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun soundSettings(): Intent = Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
