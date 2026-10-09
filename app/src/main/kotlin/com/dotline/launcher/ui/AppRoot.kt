package com.dotline.launcher.ui

import android.app.Activity
import android.content.Context
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotline.launcher.data.ThemeMode
import com.dotline.launcher.graph
import com.dotline.launcher.ui.crash.CrashLogScreen
import com.dotline.launcher.ui.guide.SetupGuideScreen
import com.dotline.launcher.ui.home.HomeRoot
import com.dotline.launcher.ui.onboarding.OnboardingScreen
import com.dotline.launcher.ui.settings.SettingsScreen
import com.dotline.launcher.ui.sound.SoundStudioScreen
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.wallpaper.WallpaperStudioScreen

/** Top-level screens. The stack is stored as a comma separated string so it survives process death. */
private enum class Screen { HOME, SETTINGS, CRASH_LOG, WALLPAPER, SOUND, GUIDE }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val graph = remember { context.graph }
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val loaded by graph.settings.loaded.collectAsStateWithLifecycle()

    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val reduceMotion = rememberReduceMotion()

    CompositionLocalProvider(
        LocalAppGraph provides graph,
        LocalSettings provides settings,
    ) {
        DotlineTheme(darkTheme = dark, reduceMotion = reduceMotion) {
            SystemBarIcons(dark)
            if (loaded) {
                if (!settings.onboardingDone) {
                    OnboardingScreen(onFinished = { graph.settings.update { it.copy(onboardingDone = true) } })
                } else {
                    Navigator()
                }
            }
        }
    }
}

@Composable
private fun Navigator() {
    var stack by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val screens = stack.split(',').mapNotNull { n -> Screen.entries.firstOrNull { it.name == n } }.ifEmpty { listOf(Screen.HOME) }
    val current = screens.last()

    fun push(s: Screen) { stack = (screens + s).joinToString(",") { it.name } }
    fun pop() { if (screens.size > 1) stack = screens.dropLast(1).joinToString(",") { it.name } }

    // The Home button always goes back to the home screen, whatever is open on top of it.
    val graph = LocalContext.current.graph
    LaunchedEffect(graph) {
        graph.homePressed.collect { stack = Screen.HOME.name }
    }

    // Home stays composed underneath so its state (page, scroll) is kept.
    HomeRoot(
        onOpenSettings = { push(Screen.SETTINGS) },
        onOpenWallpaperStudio = { push(Screen.WALLPAPER) },
    )
    when (current) {
        Screen.HOME -> Unit
        Screen.SETTINGS -> SettingsScreen(
            onBack = { pop() },
            onOpenCrashLog = { push(Screen.CRASH_LOG) },
            onOpenWallpaperStudio = { push(Screen.WALLPAPER) },
            onOpenSoundStudio = { push(Screen.SOUND) },
            onOpenSetupGuide = { push(Screen.GUIDE) },
        )
        Screen.CRASH_LOG -> CrashLogScreen(onBack = { pop() })
        Screen.WALLPAPER -> WallpaperStudioScreen(onBack = { pop() })
        Screen.SOUND -> SoundStudioScreen(onBack = { pop() })
        Screen.GUIDE -> SetupGuideScreen(onBack = { pop() })
    }
}

/** Status/navigation bar icon contrast follows the app theme. */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
}

/** True when the user set the system animator duration scale to 0 ("remove animations"). */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var reduce by remember { mutableStateOf(readReduceMotion(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reduce = readReduceMotion(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return reduce
}

private fun readReduceMotion(context: Context): Boolean = runCatching {
    AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)
