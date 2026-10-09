package com.dotline.launcher.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect as AndroidRect
import android.provider.AlarmClock
import android.widget.Toast
import androidx.compose.runtime.Stable
import com.dotline.launcher.AppGraph
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.GestureAction
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.home.DropOutcome
import com.dotline.launcher.home.HomeController
import com.dotline.launcher.service.DotlineLockService
import com.dotline.launcher.service.SystemActions

/**
 * Side effects of the home screen: launching apps, the system dialogs behind a drop on "Uninstall" /
 * "App info", gesture actions, and the two intents of the built-in widgets. No state of its own, so it
 * is safe to hand to any composable. Every call is a cheap binder call made from a user action; nothing
 * here runs during composition.
 */
@Stable
internal class HomeActions(
    private val context: Context,
    private val graph: AppGraph,
    private val controller: HomeController,
) {
    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** Starts [app] (with the icon bounds as the launch animation source) and remembers it as recent. */
    fun launch(app: AppInfo, bounds: AndroidRect?) {
        graph.apps.launch(app.key, bounds)
        graph.recents.record(app.key)
    }

    /** Reacts to what a drop did: only the two system dialogs need work, the layout is already updated. */
    fun applyDrop(outcome: DropOutcome) {
        when (outcome) {
            is DropOutcome.UninstallRequested -> graph.apps.requestUninstall(outcome.app)
            is DropOutcome.AppInfoRequested -> graph.apps.openAppInfo(outcome.app)
            else -> Unit
        }
    }

    /**
     * Runs the action configured for a gesture. Opening the drawer only flips the flag (the drawer
     * animates itself from there). Edit mode is not offered while the layout is locked.
     */
    fun perform(action: GestureAction, layoutLocked: Boolean) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.OPEN_DRAWER -> controller.setDrawerOpen(true)
            GestureAction.OPEN_NOTIFICATIONS -> SystemActions.expandNotifications(context)
            GestureAction.EDIT_MODE -> {
                if (!layoutLocked) controller.enterEdit()
            }
            GestureAction.LOCK_SCREEN -> {
                if (!DotlineLockService.lockScreen()) {
                    toast("Turn on Dotline double-tap to lock in Settings")
                }
            }
        }
    }

    /** The clock widget: the alarm list of the user's clock app. */
    fun openClockApp() {
        startSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    }

    /** The calendar widget: the user's calendar app. */
    fun openCalendarApp() {
        startSafely(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
    }

    private fun startSafely(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast("No app found for this")
        } catch (e: Exception) {
            CrashLog.record("home: start ${intent.action}", e)
        }
    }
}
