package com.dotline.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.dotline.launcher.AppGraph
import com.dotline.launcher.data.Settings

/** Current user settings, provided once at the root; read in any composable. */
val LocalSettings = staticCompositionLocalOf { Settings() }

/** Process-wide singletons (repositories). Provided once at the root. */
val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }
