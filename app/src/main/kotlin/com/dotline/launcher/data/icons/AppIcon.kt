package com.dotline.launcher.data.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.theme.DotlineTheme
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/**
 * Builds the [IconRequest] for the current settings and theme. Remembered: it only changes when the
 * size, a relevant setting or a theme colour changes.
 */
@Composable
fun rememberIconRequest(sizeDp: Dp, glyphOnly: Boolean = false): IconRequest {
    val settings = LocalSettings.current
    val density = LocalDensity.current
    val colors = DotlineTheme.colors
    val sizePx = (sizeDp.value * density.density).roundToInt()
    val style = settings.iconStyle
    val shape = settings.iconShape
    val pack = settings.iconPack
    val tile = colors.iconTile.toArgb()
    val glyph = colors.primary.toArgb()
    val outline = colors.outline.toArgb()
    val accent = colors.accent.toArgb()
    return remember(sizePx, glyphOnly, style, shape, pack, tile, glyph, outline, accent) {
        IconRequest(
            style = style,
            shape = shape,
            sizePx = sizePx,
            tileColor = tile,
            glyphColor = glyph,
            outlineColor = outline,
            accentColor = accent,
            glyphOnly = glyphOnly,
            packPackage = pack,
        )
    }
}

/**
 * The processed icon for [app]: the memory cache is consulted synchronously, otherwise the bitmap is
 * loaded off the main thread and the state flips once. Null while loading.
 */
@Composable
fun rememberAppIcon(app: AppInfo, request: IconRequest): State<ImageBitmap?> {
    val icons = LocalAppGraph.current.icons
    val state = remember(app, request, icons) { mutableStateOf<ImageBitmap?>(icons.peek(app, request)) }
    LaunchedEffect(app, request, icons) {
        if (state.value == null) {
            try {
                state.value = icons.load(app, request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave the slot empty; the repository already substitutes a fallback glyph on failure.
            }
        }
    }
    return state
}

/**
 * Icon image with the 11sp label beneath and an optional small red notification dot. Not clickable.
 * The icon slot keeps its size while the bitmap is still loading so layout never jumps.
 */
@Composable
fun AppIconView(
    app: AppInfo,
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
    showLabel: Boolean = true,
    labelColor: Color = DotlineTheme.colors.primary,
    notificationDot: Boolean = false,
) {
    val settings = LocalSettings.current
    val sizeDp = iconSize * settings.iconSize
    val request = rememberIconRequest(sizeDp)
    val icon = rememberAppIcon(app, request).value
    val dotColor = DotlineTheme.colors.accent
    val dotSize = (sizeDp.value * 0.16f).coerceIn(6f, 10f).dp
    val baseStyle = DotlineTheme.type.iconLabel
    val labelStyle = remember(baseStyle, labelColor) {
        baseStyle.copy(color = labelColor, textAlign = TextAlign.Center)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(sizeDp)) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(sizeDp),
                )
            }
            if (notificationDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(dotSize)
                        .background(dotColor, CircleShape),
                )
            }
        }
        if (showLabel && settings.showLabels) {
            Spacer(modifier = Modifier.height(6.dp))
            BasicText(
                text = app.label,
                modifier = Modifier.padding(horizontal = 2.dp),
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
