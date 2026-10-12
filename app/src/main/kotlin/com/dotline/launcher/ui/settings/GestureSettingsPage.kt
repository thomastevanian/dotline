package com.dotline.launcher.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.GestureAction
import com.dotline.launcher.service.DotlineLockService
import com.dotline.launcher.service.SystemActions
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.PillButton
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.components.SettingsCard

private val SwipeUpOptions = listOf(GestureAction.OPEN_DRAWER to "Drawer", GestureAction.NONE to "Nothing")
private val SwipeDownOptions = listOf(GestureAction.OPEN_NOTIFICATIONS to "Notifications", GestureAction.NONE to "Nothing")
private val PinchOptions = listOf(GestureAction.EDIT_MODE to "Edit mode", GestureAction.NONE to "Nothing")
private val DoubleTapOptions = listOf(GestureAction.NONE to "Nothing", GestureAction.LOCK_SCREEN to "Lock screen")

/** Swipes, pinch and double tap. Double tap to lock is off by default and explains the accessibility service first. */
@Composable
fun GestureSettingsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    var lockServiceOn by remember { mutableStateOf(DotlineLockService.isEnabled(context)) }
    OnResumeEffect { lockServiceOn = DotlineLockService.isEnabled(context) }

    SettingsPage(title = "Gestures", onBack = onBack, modifier = modifier) {
        SectionLabel("On the home screen")
        SettingsCard {
            ChoiceRow(
                title = "Swipe up",
                options = SwipeUpOptions,
                selected = s.swipeUp,
                onSelect = { value -> graph.settings.update { it.copy(swipeUp = value) } },
            )
            ChoiceRow(
                title = "Swipe down",
                subtitle = "Pulls down the notification shade. Some phones do not allow this; then nothing happens.",
                options = SwipeDownOptions,
                selected = s.swipeDown,
                onSelect = { value -> graph.settings.update { it.copy(swipeDown = value) } },
            )
            ChoiceRow(
                title = "Pinch",
                options = PinchOptions,
                selected = s.pinch,
                onSelect = { value -> graph.settings.update { it.copy(pinch = value) } },
            )
            ChoiceRow(
                title = "Double tap on empty space",
                options = DoubleTapOptions,
                selected = s.doubleTap,
                onSelect = { value -> graph.settings.update { it.copy(doubleTap = value) } },
            )
        }

        if (s.doubleTap == GestureAction.LOCK_SCREEN && !lockServiceOn) {
            InfoNote(
                "Android only lets an app lock the screen through an accessibility service. " +
                    "Dotline's service does exactly one thing: it locks the screen when you double tap. " +
                    "It reads nothing on your screen. Turn on \"Dotline\" in the Accessibility settings to finish.",
            )
            PillButton(
                text = "Open accessibility settings",
                onClick = {
                    try {
                        context.startActivity(SystemActions.accessibilitySettings())
                    } catch (e: Exception) {
                        CrashLog.record("gestures: open accessibility settings", e)
                        Toast.makeText(context, "Could not open the settings", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
        } else if (s.doubleTap == GestureAction.LOCK_SCREEN) {
            InfoNote("Double tap to lock is ready.")
        } else {
            InfoNote("Double tap to lock is off. Turning it on explains what it needs first.")
        }
    }
}
