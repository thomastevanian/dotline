package com.dotline.launcher.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dotline.launcher.BuildConfig
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.core.DefaultHome
import com.dotline.launcher.ui.components.ChevronIcon
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.components.SettingsCard
import com.dotline.launcher.ui.components.SettingsRow
import com.dotline.launcher.ui.theme.DotlineTheme

/** Version, the crash log, how to switch back to One UI, privacy and licences. */
@Composable
fun AboutPage(onBack: () -> Unit, onOpenCrashLog: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val chevron = DotlineTheme.colors.secondary
    var isDefault by remember { mutableStateOf(DefaultHome.isDefault(context)) }
    OnResumeEffect { isDefault = DefaultHome.isDefault(context) }

    SettingsPage(title = "About", onBack = onBack, modifier = modifier) {
        SectionLabel("Dotline")
        SettingsCard {
            SettingsRow(
                title = "Version",
                subtitle = BuildConfig.VERSION_NAME + " (build " + BuildConfig.VERSION_CODE + ")",
            )
            SettingsRow(
                title = "Home app",
                subtitle = if (isDefault) {
                    "Dotline is your home app. Tap to choose another one, such as One UI Home."
                } else {
                    "Another launcher is your home app. Tap to change it."
                },
                onClick = {
                    try {
                        context.startActivity(DefaultHome.homeSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        CrashLog.record("about: open home settings", e)
                        Toast.makeText(context, "Open Settings > Apps > Choose default apps > Home app", Toast.LENGTH_LONG).show()
                    }
                },
                trailing = { ChevronIcon(color = chevron) },
            )
        }

        SectionLabel("Troubleshooting")
        SettingsCard {
            SettingsRow(
                title = "Crash log",
                subtitle = "The last errors Dotline recorded. Copy and send them if something breaks.",
                onClick = onOpenCrashLog,
                trailing = { ChevronIcon(color = chevron) },
            )
        }

        SectionLabel("Privacy")
        InfoNote(
            "No ads, no analytics, no tracking and no account. The only network request Dotline makes is the " +
                "weather lookup (Open-Meteo), and only when the home screen opens and a city or location is set. " +
                "Every permission is optional and asked for only when a feature needs it.",
        )

        SectionLabel("Credits")
        InfoNote(
            "Dotline is an independent project and is not affiliated with Nothing. It uses no Nothing fonts, " +
                "icons, wallpapers, sounds or logos. Fonts: Doto, Space Mono and Space Grotesk (SIL Open Font License 1.1).",
        )
    }
}
