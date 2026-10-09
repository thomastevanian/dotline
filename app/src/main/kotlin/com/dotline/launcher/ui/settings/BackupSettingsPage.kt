package com.dotline.launcher.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotline.launcher.BuildConfig
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.BackupData
import com.dotline.launcher.data.BackupManager
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.components.PillButton
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.components.SettingsCard
import com.dotline.launcher.ui.components.SettingsRow
import com.dotline.launcher.ui.theme.DotlineTheme
import kotlinx.coroutines.launch

/**
 * Export and import of the whole layout and settings as one JSON file through the system file
 * picker (no storage permission). An import is shown first and only applied after "Restore".
 */
@Composable
fun BackupSettingsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<BackupData?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                status = try {
                    val json = BackupManager.encode(
                        settings = graph.settings.settings.value,
                        layout = graph.layout.layout.value,
                        notes = graph.notes.text.value,
                        wallpaperPresets = graph.wallpapers.exportJson(),
                        appVersion = BuildConfig.VERSION_NAME,
                        now = System.currentTimeMillis(),
                    )
                    BackupManager.writeTo(context, uri, json)
                    "Backup saved."
                } catch (e: Exception) {
                    CrashLog.record("backup: export", e)
                    "Could not save the backup file."
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = BackupManager.readFrom(context, uri)
                    val data = BackupManager.decode(text)
                    if (data == null) {
                        status = "That file is not a Dotline backup."
                    } else {
                        pending = data
                        status = null
                    }
                } catch (e: Exception) {
                    CrashLog.record("backup: import", e)
                    status = "Could not read that file."
                }
            }
        }
    }

    SettingsPage(title = "Backup", onBack = onBack, modifier = modifier) {
        SectionLabel("Backup")
        SettingsCard {
            SettingsRow(
                title = "Save a backup",
                subtitle = "Your layout, settings, note and wallpaper presets in one file",
                onClick = { exportLauncher.launch(BackupManager.suggestedFileName()) },
            )
            SettingsRow(
                title = "Restore from a backup",
                subtitle = "Pick a Dotline backup file",
                onClick = {
                    importLauncher.launch(arrayOf(BackupManager.MIME, "text/plain", "application/octet-stream"))
                },
            )
        }

        val ready = pending
        if (ready != null) {
            SectionLabel("Restore")
            SettingsCard {
                SettingsRow(
                    title = "Restore this backup?",
                    subtitle = "It replaces your current layout and settings" +
                        if (ready.appVersion.isNotEmpty()) " (saved by version " + ready.appVersion + ")." else ".",
                    highlighted = true,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillButton(
                    text = "Cancel",
                    onClick = { pending = null },
                    modifier = Modifier.weight(1f),
                    filled = false,
                )
                PillButton(
                    text = "Restore",
                    onClick = {
                        try {
                            // Keep the intro dismissed: restoring must never send the user back to onboarding.
                            graph.settings.replace(ready.settings.copy(onboardingDone = true))
                            ready.layout?.let { graph.layout.replace(it) }
                            ready.notes?.let { graph.notes.set(it) }
                            ready.wallpaperPresets?.let { graph.wallpapers.importJson(it) }
                            status = "Backup restored."
                        } catch (e: Exception) {
                            CrashLog.record("backup: restore", e)
                            status = "Could not restore the backup."
                        }
                        pending = null
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val message = status
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            BasicText(
                text = message,
                modifier = Modifier.padding(horizontal = 20.dp),
                style = DotlineTheme.type.label.copy(color = DotlineTheme.colors.primary),
            )
        }
        InfoNote("Backups are plain text files that stay wherever you save them. Dotline never uploads anything.")
    }
}
