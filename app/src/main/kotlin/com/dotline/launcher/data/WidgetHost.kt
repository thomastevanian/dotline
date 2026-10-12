package com.dotline.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.runtime.Immutable
import com.dotline.launcher.core.CrashLog

/** One installable app widget as listed in the widget picker. */
@Immutable
class WidgetProviderEntry(
    val provider: ComponentName,
    val profile: UserHandle,
    val appLabel: String,
    val label: String,
    val minWidthDp: Int,
    val minHeightDp: Int,
) {
    val packageName: String get() = provider.packageName
}

/**
 * Thin wrapper around [AppWidgetHost] so normal Android app widgets can sit on the Dotline home
 * screen. The host only listens while the home screen is visible (start in onStart, stop in
 * onStop), so it costs nothing in the background.
 */
class WidgetHostManager(context: Context) {
    private val app: Context = context.applicationContext
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(app)
    private val host = AppWidgetHost(app, HOST_ID)
    private var listening = false

    fun start() {
        if (listening) return
        try {
            host.startListening()
            listening = true
        } catch (e: Exception) {
            CrashLog.record("widget host: start", e)
        }
    }

    fun stop() {
        if (!listening) return
        try {
            host.stopListening()
        } catch (e: Exception) {
            CrashLog.record("widget host: stop", e)
        }
        listening = false
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        host.deleteAppWidgetId(id)
    }

    /** True when Android lets us bind without asking (the user already granted bind permission). */
    fun bindNow(id: Int, entry: WidgetProviderEntry): Boolean =
        manager.bindAppWidgetIdIfAllowed(id, entry.profile, entry.provider, null)

    /** The system "allow Dotline to create widgets?" screen. */
    fun bindPermissionIntent(id: Int, entry: WidgetProviderEntry): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, entry.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, entry.profile)

    /** The widget's own set-up screen, or null when it has none. */
    fun configureIntent(id: Int): Intent? {
        val configure = info(id)?.configure ?: return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(configure)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    }

    /** Provider info of a bound widget, or null when the provider has been uninstalled. */
    fun info(id: Int): AppWidgetProviderInfo? = try {
        manager.getAppWidgetInfo(id)
    } catch (e: Exception) {
        CrashLog.record("widget host: info", e)
        null
    }

    fun createView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        host.createView(context, id, info)

    /**
     * Every app widget of every profile except Dotline's own (those have built-in versions).
     * Blocking: call from a background dispatcher.
     */
    fun installedProviders(): List<WidgetProviderEntry> {
        val pm = app.packageManager
        val users = app.getSystemService(UserManager::class.java)
        val profiles: List<UserHandle> = try {
            users?.userProfiles ?: listOf(Process.myUserHandle())
        } catch (e: Exception) {
            listOf(Process.myUserHandle())
        }
        val out = ArrayList<WidgetProviderEntry>()
        for (profile in profiles) {
            val infos = try {
                manager.getInstalledProvidersForProfile(profile)
            } catch (e: Exception) {
                CrashLog.record("widget host: list providers", e)
                emptyList<AppWidgetProviderInfo>()
            }
            for (info in infos) {
                val provider = info.provider ?: continue
                if (provider.packageName == app.packageName) continue
                val label = try {
                    info.loadLabel(pm)
                } catch (e: Exception) {
                    provider.shortClassName
                }
                val appLabel = appLabel(pm, provider.packageName)
                out.add(
                    WidgetProviderEntry(
                        provider = provider,
                        profile = profile,
                        appLabel = appLabel,
                        label = label.ifBlank { appLabel },
                        minWidthDp = dp(info.minWidth),
                        minHeightDp = dp(info.minHeight),
                    ),
                )
            }
        }
        return out.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    /** The picker's preview picture of a provider, or null. Blocking: call from a background dispatcher. */
    fun previewDrawable(entry: WidgetProviderEntry): Drawable? {
        val info = manager.getInstalledProvidersForProfile(entry.profile).firstOrNull { it.provider == entry.provider }
            ?: return null
        return try {
            info.loadPreviewImage(app, 0)
        } catch (e: Exception) {
            null
        }
    }

    private fun dp(px: Int): Int {
        val density = app.resources.displayMetrics.density
        return if (density > 0f) Math.round(px / density) else px
    }

    private fun appLabel(pm: PackageManager, packageName: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
    }

    private companion object {
        const val HOST_ID = 0x646F74 // "dot"
    }
}
