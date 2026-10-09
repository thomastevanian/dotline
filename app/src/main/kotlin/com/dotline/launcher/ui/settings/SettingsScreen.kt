package com.dotline.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotline.launcher.core.DefaultHome
import com.dotline.launcher.data.ClockStyle
import com.dotline.launcher.data.IconShape
import com.dotline.launcher.data.IconStyle
import com.dotline.launcher.data.Settings
import com.dotline.launcher.data.ThemeMode
import com.dotline.launcher.data.TimeFormat
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.FlatSwitch
import com.dotline.launcher.ui.components.ScreenHeader
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.components.SettingsCard
import com.dotline.launcher.ui.components.SettingsRow
import com.dotline.launcher.ui.theme.DotlineTheme

/**
 * Settings v0 (Stage 1): the options that matter for first-run testing. Stage 4 replaces this
 * with the full Nothing-style settings tree.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCrashLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val graph = LocalAppGraph.current
    val s = LocalSettings.current
    val context = LocalContext.current
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun update(transform: (Settings) -> Settings) = graph.settings.update(transform)

    Column(
        modifier
            .fillMaxSize()
            .background(DotlineTheme.colors.background),
    ) {
        ScreenHeader(title = "Settings", onBack = onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            SectionLabel("Theme")
            SettingsCard {
                SettingsRow(
                    title = "Appearance",
                    subtitle = when (s.themeMode) {
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.SYSTEM -> "Follow system"
                    },
                    onClick = { update { it.copy(themeMode = ThemeMode.entries[(it.themeMode.ordinal + 1) % ThemeMode.entries.size]) } },
                )
            }

            SectionLabel("Clock")
            SettingsCard {
                SettingsRow(
                    title = "Clock style",
                    subtitle = if (s.clockStyle == ClockStyle.DOT) "Dot matrix" else "Normal",
                    onClick = { update { it.copy(clockStyle = ClockStyle.entries[(it.clockStyle.ordinal + 1) % ClockStyle.entries.size]) } },
                )
                SettingsRow(
                    title = "Time format",
                    subtitle = when (s.timeFormat) {
                        TimeFormat.SYSTEM -> "Follow system"
                        TimeFormat.H12 -> "12 hour"
                        TimeFormat.H24 -> "24 hour"
                    },
                    onClick = { update { it.copy(timeFormat = TimeFormat.entries[(it.timeFormat.ordinal + 1) % TimeFormat.entries.size]) } },
                )
                SettingsRow(
                    title = "Show date",
                    trailing = { FlatSwitch(checked = s.showDate, onCheckedChange = { v -> update { it.copy(showDate = v) } }) },
                    onClick = { update { it.copy(showDate = !it.showDate) } },
                )
                SettingsRow(
                    title = "Hide clock",
                    trailing = { FlatSwitch(checked = s.hideClock, onCheckedChange = { v -> update { it.copy(hideClock = v) } }) },
                    onClick = { update { it.copy(hideClock = !it.hideClock) } },
                )
            }

            SectionLabel("Icons")
            SettingsCard {
                SettingsRow(
                    title = "Icon style",
                    subtitle = when (s.iconStyle) {
                        IconStyle.MONOCHROME -> "Monochrome"
                        IconStyle.ORIGINAL -> "Original"
                        IconStyle.MONOCHROME_ACCENT -> "Monochrome with accent"
                    },
                    onClick = { update { it.copy(iconStyle = IconStyle.entries[(it.iconStyle.ordinal + 1) % IconStyle.entries.size]) } },
                )
                SettingsRow(
                    title = "Icon shape",
                    subtitle = if (s.iconShape == IconShape.CIRCLE) "Circle" else "Rounded square",
                    onClick = { update { it.copy(iconShape = IconShape.entries[(it.iconShape.ordinal + 1) % IconShape.entries.size]) } },
                )
                SettingsRow(
                    title = "Show labels",
                    trailing = { FlatSwitch(checked = s.showLabels, onCheckedChange = { v -> update { it.copy(showLabels = v) } }) },
                    onClick = { update { it.copy(showLabels = !it.showLabels) } },
                )
            }

            SectionLabel("Home grid")
            SettingsCard {
                SettingsRow(
                    title = "Columns",
                    subtitle = s.gridColumns.toString(),
                    onClick = { update { it.copy(gridColumns = if (it.gridColumns >= 6) 4 else it.gridColumns + 1) } },
                )
                SettingsRow(
                    title = "Rows",
                    subtitle = s.gridRows.toString(),
                    onClick = { update { it.copy(gridRows = if (it.gridRows >= 7) 5 else it.gridRows + 1) } },
                )
            }

            SectionLabel("About")
            SettingsCard {
                SettingsRow(
                    title = "Default home app",
                    subtitle = if (DefaultHome.isDefault(context)) "Dotline is your home app" else "Tap to choose Dotline",
                    onClick = {
                        val role = DefaultHome.roleRequestIntent(context)
                        if (role != null) {
                            runCatching { roleLauncher.launch(role) }
                        } else {
                            runCatching { context.startActivity(DefaultHome.homeSettingsIntent()) }
                        }
                    },
                )
                SettingsRow(title = "Crash log", subtitle = "Last errors, for troubleshooting", onClick = onOpenCrashLog)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
