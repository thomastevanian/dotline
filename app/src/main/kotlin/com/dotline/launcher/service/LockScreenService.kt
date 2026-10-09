package com.dotline.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Exists ONLY to lock the screen on "double tap" (GLOBAL_ACTION_LOCK_SCREEN, Android 9+).
 * It listens to no events and reads no screen content. It is never requested unless the user
 * turns the double-tap-to-lock gesture on, and the settings screen explains why first.
 */
class DotlineLockService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile
        private var instance: DotlineLockService? = null

        /** Locks the screen if the service is enabled. Returns false when it is not available. */
        fun lockScreen(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val me = context.packageName + "/" + DotlineLockService::class.java.name
            return flat.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
