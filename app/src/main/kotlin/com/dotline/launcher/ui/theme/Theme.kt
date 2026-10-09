package com.dotline.launcher.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import com.dotline.launcher.R

/** Nothing red. Used ONLY for tiny highlights: notification dots, active page dot, switches, one-off marks. */
val NothingRed = Color(0xFFD71921)

/**
 * Semantic colour tokens. Dark values follow the Nothing OS 5.0 screenshots
 * (pure black canvas, #141414 rows, #212121 raised rows, #D4D4D4 inverted highlight row).
 * Light values come from the light-mode screenshot (#E3E3E3 canvas, white cards).
 */
@Immutable
class DotlineColors(
    val isDark: Boolean,
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val outline: Color,
    val card: Color,
    val cardRaised: Color,
    /** Inverted row (e.g. the selected "Use Dark theme" row): light fill + dark text on dark theme. */
    val highlight: Color,
    val onHighlight: Color,
    val accent: Color,
    /** Fill of dark circular icon tiles on the home screen. */
    val iconTile: Color,
    val pressOverlay: Color,
    val scrim: Color,
)

val DarkColors = DotlineColors(
    isDark = true,
    background = Color(0xFF000000),
    primary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8A8A8A),
    tertiary = Color(0xFF5A5A5A),
    outline = Color(0xFF2A2A2A),
    card = Color(0xFF141414),
    cardRaised = Color(0xFF212121),
    highlight = Color(0xFFD4D4D4),
    onHighlight = Color(0xFF000000),
    accent = NothingRed,
    iconTile = Color(0xFF000000),
    pressOverlay = Color(0x14FFFFFF),
    scrim = Color(0x99000000),
)

val LightColors = DotlineColors(
    isDark = false,
    background = Color(0xFFE3E3E3),
    primary = Color(0xFF000000),
    secondary = Color(0xFF6B6B6B),
    tertiary = Color(0xFF9A9A9A),
    outline = Color(0xFFCFCFCF),
    card = Color(0xFFFFFFFF),
    cardRaised = Color(0xFFF2F2F2),
    highlight = Color(0xFF000000),
    onHighlight = Color(0xFFFFFFFF),
    accent = NothingRed,
    iconTile = Color(0xFFFFFFFF),
    pressOverlay = Color(0x14000000),
    scrim = Color(0x66FFFFFF),
)

object DotlineFonts {
    /** Dot-matrix display face (Doto, OFL) - nearest open match to Nothing's Ndot. */
    val Doto = FontFamily(
        Font(R.font.doto_regular, FontWeight.Normal),
        Font(R.font.doto_medium, FontWeight.Medium),
        Font(R.font.doto_bold, FontWeight.Bold),
        Font(R.font.doto_black, FontWeight.Black),
    )
    val Mono = FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )
    val Body = FontFamily(
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
        Font(R.font.space_grotesk_bold, FontWeight.Bold),
    )
}

/** Text styles carry no colour; pick one with `color = DotlineTheme.colors.primary`. */
@Immutable
class DotlineTypography(
    /** Screen titles, e.g. "Dark theme", "About phone". */
    val title: TextStyle = TextStyle(fontFamily = DotlineFonts.Doto, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
    /** Section headings. */
    val heading: TextStyle = TextStyle(fontFamily = DotlineFonts.Doto, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp),
    val body: TextStyle = TextStyle(fontFamily = DotlineFonts.Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    val bodyMedium: TextStyle = TextStyle(fontFamily = DotlineFonts.Body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    val small: TextStyle = TextStyle(fontFamily = DotlineFonts.Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    /** Small mono labels and captions. */
    val label: TextStyle = TextStyle(fontFamily = DotlineFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    val caption: TextStyle = TextStyle(fontFamily = DotlineFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
    /** App label under home-screen icons. */
    val iconLabel: TextStyle = TextStyle(fontFamily = DotlineFonts.Body, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp),
    /** Large Doto numerals (weather temperature etc.) when a font rendering is preferred over the canvas. */
    val display: TextStyle = TextStyle(fontFamily = DotlineFonts.Doto, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 60.sp),
)

@Immutable
class DotlineShapes(
    val card: Shape = RoundedCornerShape(24.dp),
    val cardSmall: Shape = RoundedCornerShape(8.dp),
    val chip: Shape = RoundedCornerShape(12.dp),
    val pill: Shape = RoundedCornerShape(percent = 50),
)

private val LocalColors = staticCompositionLocalOf { DarkColors }
private val LocalTypography = staticCompositionLocalOf { DotlineTypography() }
private val LocalShapes = staticCompositionLocalOf { DotlineShapes() }
/** True when the user enabled "remove animations" (Settings > animation scale 0). */
val LocalReduceMotion = staticCompositionLocalOf { false }

object DotlineTheme {
    val colors: DotlineColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: DotlineTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
    val shapes: DotlineShapes
        @Composable @ReadOnlyComposable get() = LocalShapes.current
}

@Composable
fun DotlineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val typography = remember { DotlineTypography() }
    val shapes = remember { DotlineShapes() }
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
        LocalShapes provides shapes,
        LocalReduceMotion provides reduceMotion,
        content = content,
    )
}

/**
 * Flat press state: no ripple, a faint overlay (white 8% on dark, black 8% on light) is drawn
 * over the element while pressed. [shape] is used to clip the overlay.
 */
@Composable
fun Modifier.flatClickable(
    shape: Shape,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val overlay = DotlineTheme.colors.pressOverlay
    val density = LocalDensity.current
    val base = this.drawWithContent {
        drawContent()
        if (pressed) {
            val outline = shape.createOutline(Size(size.width, size.height), LayoutDirection.Ltr, density)
            drawOutline(outline, overlay)
        }
    }
    return if (onLongClick != null) {
        base.combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    } else {
        base.clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
    }
}
