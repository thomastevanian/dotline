package com.dotline.launcher.service

import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Context
import android.app.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification dots, strictly opt-in: this service only runs after the user grants Notification
 * Access to Dotline in system settings. It records WHICH apps have active notifications (package +
 * user) and nothing else: no titles, no text, nothing is stored or sent anywhere.
 */
class DotlineNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        rebuild()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationDots.update(emptySet())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = rebuild()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = rebuild()

    private fun rebuild() {
        val users = getSystemService(UserManager::class.java)
        val keys = HashSet<String>()
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        for (sbn in active) {
            if (!countsForDot(sbn)) continue
            val serial = runCatching { users.getSerialNumberForUser(sbn.user) }.getOrDefault(0L)
            keys += NotificationDots.key(sbn.packageName, serial)
        }
        NotificationDots.update(keys)
    }

    /** Ongoing (media, navigation, foreground services) and group summaries do not make a dot. */
    private fun countsForDot(sbn: StatusBarNotification): Boolean {
        if (sbn.isOngoing) return false
        val flags = sbn.notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        return true
    }
}

/** Process-wide set of "package#userSerial" that currently have a notification. */
object NotificationDots {
    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    fun key(packageName: String, userSerial: Long): String = "$packageName#$userSerial"

    internal fun update(keys: Set<String>) {
        if (_active.value != keys) _active.value = keys
    }

    fun clear() = update(emptySet())

    fun hasDot(packageName: String, userSerial: Long): Boolean = key(packageName, userSerial) in _active.value

    /** True when Notification Access has been granted to this app. */
    fun isEnabled(context: Context): Boolean {
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        return flat.split(':').any { it.startsWith(context.packageName + "/") }
    }
}
