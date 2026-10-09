package com.dotline.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.PillButton
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.components.SettingsCard
import com.dotline.launcher.ui.theme.DotlineTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Grid sizes the home screen supports (see SettingsJson.decode: columns 4..6, rows 5..7). */
private val HomeColumnOptions: List<Pair<Int, String>> = listOf(4 to "4", 5 to "5", 6 to "6")
private val HomeRowOptions: List<Pair<Int, String>> = listOf(5 to "5", 6 to "6", 7 to "7")

/** How long a slider has to rest before its value is written to DataStore. */
private const val SLIDER_SAVE_DELAY_MS = 200L

/** Home screen options: grid, dock, layout lock, infinite scroll and the wallpaper dim. */
@Composable
fun HomeSettingsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val graph = LocalAppGraph.current

    SettingsPage(title = "Home", onBack = onBack, modifier = modifier) {
        SectionLabel("Grid")
        SettingsCard {
            ChoiceRow(
                title = "Columns",
                options = HomeColumnOptions,
                selected = s.gridColumns,
                onSelect = { value -> graph.settings.update { it.copy(gridColumns = value) } },
            )
            ChoiceRow(
                title = "Rows",
                options = HomeRowOptions,
                selected = s.gridRows,
                onSelect = { value -> graph.settings.update { it.copy(gridRows = value) } },
            )
        }
        InfoNote("When the grid gets smaller, items that no longer fit move to a free spot and a page is added if needed.")

        SectionLabel("Layout")
        SettingsCard {
            SwitchRow(
                title = "Show dock",
                checked = s.showDock,
                onCheckedChange = { value -> graph.settings.update { it.copy(showDock = value) } },
            )
            SwitchRow(
                title = "Show search bar",
                subtitle = "The Search pill under the dock",
                checked = s.showSearchBar,
                onCheckedChange = { value -> graph.settings.update { it.copy(showSearchBar = value) } },
            )
            SwitchRow(
                title = "Lock layout",
                subtitle = "Prevents moving and removing items",
                checked = s.lockLayout,
                onCheckedChange = { value -> graph.settings.update { it.copy(lockLayout = value) } },
            )
            SwitchRow(
                title = "Infinite scroll",
                subtitle = "Swipe past the last page to come back to the first",
                checked = s.infiniteScroll,
                onCheckedChange = { value -> graph.settings.update { it.copy(infiniteScroll = value) } },
            )
        }

        SectionLabel("Wallpaper")
        SettingsCard {
            CoreSliderRow(
                title = "Wallpaper dim",
                saved = s.wallpaperDim,
                onSave = { value ->
                    val rounded = (value * 100f).roundToInt() / 100f
                    graph.settings.update { it.copy(wallpaperDim = rounded) }
                },
                valueRange = 0f..0.6f,
                formatLabel = { value -> (value * 100f).roundToInt().toString() + "%" },
            )
        }
        InfoNote("A black layer over the wallpaper, so labels stay easy to read on bright pictures.")
    }
}

/**
 * Slider row that keeps the dragged value locally and writes it once the finger has rested for a
 * moment (and when the page closes), so a drag does not cause one DataStore write per touch event.
 * [saved] is only read when the row first appears.
 */
@Composable
internal fun CoreSliderRow(
    title: String,
    saved: Float,
    onSave: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    formatLabel: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf(saved) }
    var pending by remember { mutableStateOf(false) }
    val latestSave by rememberUpdatedState(onSave)

    LaunchedEffect(current) {
        if (pending) {
            delay(SLIDER_SAVE_DELAY_MS)
            latestSave(current)
            pending = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (pending) {
                latestSave(current)
            }
        }
    }

    SliderRow(
        title = title,
        value = current,
        onValueChange = { value ->
            current = value
            pending = true
        },
        valueRange = valueRange,
        valueLabel = formatLabel(current),
        modifier = modifier,
    )
}

/**
 * Plain-language explanation shown before a permission or a system screen is opened: the text,
 * a filled confirm button and a Cancel button. Sits inside a [SettingsCard] like a row.
 */
@Composable
internal fun CoreExplainPanel(
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DotlineTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(vertical = 8.dp),
    ) {
        InfoNote(text)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                text = confirmLabel,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                filled = true,
            )
            PillButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                filled = false,
            )
        }
    }
}
