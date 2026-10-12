package com.dotline.launcher.ui.theme

import android.app.WallpaperManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Colour for text and indicators that sit straight on the wallpaper (icon labels, page dots): white on a
 * dark wallpaper, near-black on a light one. Null means "use the theme's primary colour".
 */
val LocalOnWallpaper = staticCompositionLocalOf<Color?> { null }

@Composable
fun onWallpaperColor(): Color = LocalOnWallpaper.current ?: DotlineTheme.colors.primary

private val DarkInk = Color(0xFF111111)

/** The text colour that reads best on a wallpaper that is [light] or dark. */
fun inkOnWallpaper(light: Boolean): Color = if (light) DarkInk else Color.White

/**
 * True when the wallpaper is light enough that dark text reads better on it. Needs no permission. It is
 * read again every time the app comes to the foreground, so a new wallpaper is picked up. Live wallpapers
 * and phones that do not report colours count as dark.
 */
@Composable
fun rememberWallpaperIsLight(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var light by remember { mutableStateOf(readWallpaperIsLight(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) light = readWallpaperIsLight(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return light
}

private fun readWallpaperIsLight(context: android.content.Context): Boolean = try {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
        false
    } else {
        val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        if (colors == null) {
            false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (colors.colorHints and HINT_SUPPORTS_DARK_TEXT) != 0
        } else {
            // Older phones: average the brightness of the wallpaper's main colours.
            val swatches = listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor)
            val luminance = swatches.map { ColorUtils.calculateLuminance(it.toArgb()) }.average()
            luminance > 0.55
        }
    }
} catch (e: Exception) {
    false
}

/** WallpaperColors.HINT_SUPPORTS_DARK_TEXT (public since API 31). */
private const val HINT_SUPPORTS_DARK_TEXT = 1
