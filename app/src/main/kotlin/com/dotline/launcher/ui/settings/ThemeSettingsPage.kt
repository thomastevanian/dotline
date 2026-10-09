package com.dotline.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dotline.launcher.data.ThemeMode
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.DotText
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.components.SettingsCard
import com.dotline.launcher.ui.theme.DarkColors
import com.dotline.launcher.ui.theme.DotlineColors
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.LightColors
import com.dotline.launcher.ui.theme.flatClickable

private val ThemeModeOptions: List<Pair<ThemeMode, String>> = listOf(
    ThemeMode.DARK to "Dark",
    ThemeMode.LIGHT to "Light",
    ThemeMode.SYSTEM to "System",
)

/** Dark, light or follow the system, with a preview of both palettes. */
@Composable
fun ThemeSettingsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val graph = LocalAppGraph.current
    val systemDark = isSystemInDarkTheme()
    val effectiveDark: Boolean = when (s.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    SettingsPage(title = "Theme", onBack = onBack, modifier = modifier) {
        SectionLabel("Appearance")
        SettingsCard {
            ChoiceRow(
                title = "Mode",
                subtitle = "System follows the dark mode setting of your phone",
                options = ThemeModeOptions,
                selected = s.themeMode,
                onSelect = { value -> graph.settings.update { it.copy(themeMode = value) } },
            )
        }

        SectionLabel("Preview")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemePalettePreview(
                palette = DarkColors,
                name = "Dark",
                active = effectiveDark,
                onClick = { graph.settings.update { it.copy(themeMode = ThemeMode.DARK) } },
                modifier = Modifier.weight(1f),
            )
            ThemePalettePreview(
                palette = LightColors,
                name = "Light",
                active = !effectiveDark,
                onClick = { graph.settings.update { it.copy(themeMode = ThemeMode.LIGHT) } },
                modifier = Modifier.weight(1f),
            )
        }
        InfoNote("The outlined preview is the look in use right now. Tap a preview to switch to it.")
        InfoNote("The system look, such as the notification shade, quick settings and the navigation bar, is controlled by Samsung One UI. Dotline themes its own screens only.")
    }
}

/**
 * One palette drawn in its own colours: name in dot-matrix, a sample card and a row of swatches.
 * The border uses the colours of the theme currently in use, thicker when this palette is active.
 */
@Composable
private fun ThemePalettePreview(
    palette: DotlineColors,
    name: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = DotlineTheme.colors
    val shape = DotlineTheme.shapes.card
    val borderWidth = if (active) 2.dp else 1.dp
    val borderColor = if (active) current.primary else current.outline

    Column(
        modifier
            .background(palette.background, shape)
            .border(borderWidth, borderColor, shape)
            .flatClickable(shape, onClick = onClick)
            .padding(16.dp),
    ) {
        DotText(
            text = name.uppercase(),
            dot = 2.5.dp,
            gap = 1.2.dp,
            color = palette.primary,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(palette.card, DotlineTheme.shapes.chip)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicText(
                text = "Sample",
                style = DotlineTheme.type.small.copy(color = palette.primary),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(palette.highlight, DotlineTheme.shapes.chip)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicText(
                text = "Selected",
                style = DotlineTheme.type.small.copy(color = palette.onHighlight),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PaletteSwatch(color = palette.card, outline = palette.outline)
            PaletteSwatch(color = palette.cardRaised, outline = palette.outline)
            PaletteSwatch(color = palette.secondary, outline = palette.outline)
            PaletteSwatch(color = palette.primary, outline = palette.outline)
            PaletteSwatch(color = palette.accent, outline = palette.outline)
        }
    }
}

/** A small round colour sample with a hairline outline so pale and dark colours both show. */
@Composable
private fun PaletteSwatch(color: Color, outline: Color) {
    Box(
        Modifier
            .size(16.dp)
            .background(color, CircleShape)
            .border(1.dp, outline, CircleShape),
    )
}
