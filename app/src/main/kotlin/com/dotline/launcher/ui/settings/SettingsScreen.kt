package com.dotline.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.core.DefaultHome
import com.dotline.launcher.data.ClockStyle
import com.dotline.launcher.data.IconShape
import com.dotline.launcher.data.IconStyle
import com.dotline.launcher.data.ThemeMode
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.ChevronIcon
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.components.SettingsCard
import com.dotline.launcher.ui.components.SettingsRow
import com.dotline.launcher.ui.theme.DotlineTheme

private const val PAGE_ROOT = "root"
private const val PAGE_HOME = "home"
private const val PAGE_ICONS = "icons"
private const val PAGE_CLOCK = "clock"
private const val PAGE_DRAWER = "drawer"
private const val PAGE_GESTURES = "gestures"
private const val PAGE_THEME = "theme"
private const val PAGE_BACKUP = "backup"
private const val PAGE_ABOUT = "about"

/**
 * The settings host. The root page lists every group; each group opens as a full page in place.
 * The open page is kept in rememberSaveable and the sub pages handle their own back press
 * (SettingsPage installs a BackHandler), so back from a sub page returns here and back from the
 * root page calls [onBack].
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCrashLog: () -> Unit,
    onOpenWallpaperStudio: () -> Unit,
    onOpenSoundStudio: () -> Unit,
    onOpenSetupGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableStateOf(PAGE_ROOT) }
    val backToRoot: () -> Unit = { page = PAGE_ROOT }

    Box(
        modifier
            .fillMaxSize()
            // Screens are stacked over the home screen: swallow taps so they never reach it.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        when (page) {
            PAGE_HOME -> HomeSettingsPage(onBack = backToRoot)
            PAGE_ICONS -> IconSettingsPage(onBack = backToRoot)
            PAGE_CLOCK -> ClockWidgetsSettingsPage(onBack = backToRoot)
            PAGE_DRAWER -> DrawerSettingsPage(onBack = backToRoot)
            PAGE_GESTURES -> GestureSettingsPage(onBack = backToRoot)
            PAGE_THEME -> ThemeSettingsPage(onBack = backToRoot)
            PAGE_BACKUP -> BackupSettingsPage(onBack = backToRoot)
            PAGE_ABOUT -> AboutPage(onBack = backToRoot, onOpenCrashLog = onOpenCrashLog)
            else -> SettingsRootPage(
                onBack = onBack,
                onOpenPage = { target -> page = target },
                onOpenWallpaperStudio = onOpenWallpaperStudio,
                onOpenSoundStudio = onOpenSoundStudio,
                onOpenSetupGuide = onOpenSetupGuide,
            )
        }
    }
}

@Composable
private fun SettingsRootPage(
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit,
    onOpenWallpaperStudio: () -> Unit,
    onOpenSoundStudio: () -> Unit,
    onOpenSetupGuide: () -> Unit,
) {
    val s = LocalSettings.current
    val context = LocalContext.current
    val colors = DotlineTheme.colors

    var isDefault by remember { mutableStateOf(DefaultHome.isDefault(context)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        isDefault = DefaultHome.isDefault(context)
    }

    val requestHomeRole: () -> Unit = {
        val roleIntent = DefaultHome.roleRequestIntent(context)
        var launched = false
        if (roleIntent != null) {
            launched = try {
                roleLauncher.launch(roleIntent)
                true
            } catch (e: Exception) {
                false
            }
        }
        if (!launched) {
            try {
                roleLauncher.launch(DefaultHome.homeSettingsIntent())
            } catch (e: Exception) {
                CrashLog.record("Settings: open home app settings", e)
            }
        }
    }

    SettingsPage(title = "Settings", onBack = onBack) {
        if (!isDefault) {
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsRow(
                    title = "Make Dotline your home app",
                    subtitle = "Another launcher is your home app right now. Tap to switch to Dotline.",
                    onClick = requestHomeRole,
                    highlighted = true,
                    trailing = { ChevronIcon(color = colors.onHighlight) },
                )
            }
        }

        SectionLabel("Home screen")
        SettingsCard {
            RootNavRow(
                title = "Home",
                subtitle = s.gridColumns.toString() + " x " + s.gridRows.toString() + " grid",
                onClick = { onOpenPage(PAGE_HOME) },
            )
            RootNavRow(
                title = "Icons",
                subtitle = iconSummary(s.iconStyle, s.iconShape),
                onClick = { onOpenPage(PAGE_ICONS) },
            )
            RootNavRow(
                title = "Clock and widgets",
                subtitle = clockSummary(s.hideClock, s.clockStyle),
                onClick = { onOpenPage(PAGE_CLOCK) },
            )
            RootNavRow(
                title = "Drawer",
                subtitle = "Search, recent apps and hidden apps",
                onClick = { onOpenPage(PAGE_DRAWER) },
            )
        }

        SectionLabel("Behaviour")
        SettingsCard {
            RootNavRow(
                title = "Gestures",
                subtitle = "Swipes, pinch and double tap",
                onClick = { onOpenPage(PAGE_GESTURES) },
            )
            RootNavRow(
                title = "Theme",
                subtitle = themeSummary(s.themeMode),
                onClick = { onOpenPage(PAGE_THEME) },
            )
        }

        SectionLabel("Personalise")
        SettingsCard {
            RootNavRow(
                title = "Wallpapers",
                subtitle = "Make a dotted wallpaper",
                onClick = onOpenWallpaperStudio,
            )
            RootNavRow(
                title = "Sounds",
                subtitle = "Make ringtones and notification sounds",
                onClick = onOpenSoundStudio,
            )
            RootNavRow(
                title = "Finish the look",
                subtitle = "A short guide to the last few steps in Android",
                onClick = onOpenSetupGuide,
            )
        }

        SectionLabel("Dotline")
        SettingsCard {
            RootNavRow(
                title = "Backup and restore",
                subtitle = "Save your settings and layout to a file",
                onClick = { onOpenPage(PAGE_BACKUP) },
            )
            RootNavRow(
                title = "About",
                subtitle = "Version, privacy and troubleshooting",
                onClick = { onOpenPage(PAGE_ABOUT) },
            )
        }
    }
}

/** A settings row that opens another page: title, short summary and a chevron. */
@Composable
private fun RootNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    val chevronColor = DotlineTheme.colors.secondary
    SettingsRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = { ChevronIcon(color = chevronColor) },
    )
}

private fun iconSummary(style: IconStyle, shape: IconShape): String {
    val styleText = when (style) {
        IconStyle.MONOCHROME -> "Monochrome"
        IconStyle.ORIGINAL -> "Original"
        IconStyle.MONOCHROME_ACCENT -> "Mono with accent"
    }
    val shapeText = when (shape) {
        IconShape.CIRCLE -> "circle"
        IconShape.ROUNDED_SQUARE -> "rounded square"
    }
    return styleText + ", " + shapeText
}

private fun clockSummary(hideClock: Boolean, style: ClockStyle): String {
    if (hideClock) return "Clock hidden"
    return when (style) {
        ClockStyle.DOT -> "Dot matrix clock"
        ClockStyle.NORMAL -> "Normal clock"
    }
}

private fun themeSummary(mode: ThemeMode): String = when (mode) {
    ThemeMode.DARK -> "Dark"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.SYSTEM -> "Follow system"
}
