package com.dotline.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotline.launcher.core.BiometricGate
import com.dotline.launcher.core.findFragmentActivity
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.components.SettingsCard
import com.dotline.launcher.ui.components.SettingsRow

/** A hidden app as listed in settings: the stored entry (flat key or package) and a readable name. */
private class HiddenEntry(val stored: String, val label: String)

/** App drawer options: search keyboard, recent apps and hidden apps (optionally behind the screen lock). */
@Composable
fun DrawerSettingsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val apps by graph.apps.apps.collectAsStateWithLifecycle()
    var unlocked by remember { mutableStateOf(false) }
    val canLock = remember { BiometricGate.available(context) }

    val hidden = remember(s.hiddenApps, apps) {
        s.hiddenApps.map { stored ->
            val match = apps.firstOrNull { it.key.flat == stored } ?: apps.firstOrNull { it.packageName == stored }
            HiddenEntry(stored, match?.label ?: stored)
        }.sortedBy { it.label.lowercase() }
    }

    SettingsPage(title = "Drawer", onBack = onBack, modifier = modifier) {
        SectionLabel("Search")
        SettingsCard {
            SwitchRow(
                title = "Open keyboard automatically",
                subtitle = "Start typing as soon as the drawer opens",
                checked = s.drawerAutoKeyboard,
                onCheckedChange = { value -> graph.settings.update { it.copy(drawerAutoKeyboard = value) } },
            )
        }

        SectionLabel("Recent apps")
        SettingsCard {
            SwitchRow(
                title = "Show recent apps",
                subtitle = "A row of the apps you opened last",
                checked = s.drawerShowRecents,
                onCheckedChange = { value -> graph.settings.update { it.copy(drawerShowRecents = value) } },
            )
            if (s.drawerShowRecents) {
                SettingsRow(
                    title = "Clear recent apps",
                    onClick = { graph.recents.clear() },
                )
            }
        }
        InfoNote("The list is kept on this phone only and is never sent anywhere.")

        SectionLabel("Hidden apps")
        SettingsCard {
            SwitchRow(
                title = "Lock the hidden apps list",
                subtitle = if (canLock) {
                    "Ask for your fingerprint, face or screen lock before the list is shown"
                } else {
                    "Set up a screen lock in Android settings to use this"
                },
                checked = s.hiddenAppsBiometric && canLock,
                onCheckedChange = { value ->
                    if (canLock) graph.settings.update { it.copy(hiddenAppsBiometric = value) }
                },
            )
            val locked = s.hiddenAppsBiometric && canLock && !unlocked
            if (hidden.isEmpty()) {
                SettingsRow(
                    title = "No hidden apps",
                    subtitle = "Long-press an app in the drawer and choose Hide app",
                )
            } else if (locked) {
                SettingsRow(
                    title = "Show hidden apps (" + hidden.size + ")",
                    subtitle = "Unlock to see the list",
                    onClick = {
                        val activity = context.findFragmentActivity()
                        if (activity != null) {
                            BiometricGate.prompt(activity, "Hidden apps", "Unlock to see the list") { unlocked = true }
                        }
                    },
                )
            } else {
                for (entry in hidden) {
                    SettingsRow(
                        title = entry.label,
                        subtitle = "Tap to show it in the drawer again",
                        onClick = {
                            graph.settings.update { it.copy(hiddenApps = it.hiddenApps - entry.stored) }
                        },
                    )
                }
            }
        }
    }
}
