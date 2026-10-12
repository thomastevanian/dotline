package com.dotline.launcher.core

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Helpers around "is Dotline the default home app". Everything here is defensive: no function
 * throws, so callers can use the results directly from UI code.
 */
object DefaultHome {

    /** True when Dotline currently holds the home role (or resolves as the default HOME activity). */
    fun isDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                }
            } catch (e: Exception) {
                // Fall through to the package manager check below.
            }
        }
        return resolvesToThisApp(context)
    }

    /**
     * Intent that shows the system "set as default home app" dialog (RoleManager ROLE_HOME, API 29+).
     * Returns null when the role is unavailable on this device, the API level is too low, or
     * Dotline already holds the role. Launch it with StartActivityForResult.
     */
    fun roleRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        try {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager == null) return null
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return null
            if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return null
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } catch (e: Exception) {
            return null
        }
    }

    /** The system "Default apps > Home app" settings screen. Fallback when the role dialog is unavailable. */
    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    @Suppress("DEPRECATION")
    private fun resolvesToThisApp(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            info?.activityInfo?.packageName == context.packageName
        } catch (e: Exception) {
            false
        }
    }
}
