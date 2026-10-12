package com.dotline.launcher.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the home screen wants from the status and navigation bar icons: dark ones over a light wallpaper,
 * light ones otherwise (null = no opinion). Only the home screen uses it; every other screen has an
 * opaque theme background and follows the theme.
 */
@Stable
class SystemBarStyle {
    var homeDarkIcons: Boolean? by mutableStateOf(null)
}

val LocalSystemBarStyle = staticCompositionLocalOf { SystemBarStyle() }
