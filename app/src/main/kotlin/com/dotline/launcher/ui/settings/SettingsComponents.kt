package com.dotline.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dotline.launcher.ui.components.FlatSlider
import com.dotline.launcher.ui.components.FlatSwitch
import com.dotline.launcher.ui.components.ScreenHeader
import com.dotline.launcher.ui.components.SettingsRow
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.flatClickable

/**
 * Shared building blocks of every settings page, so they all look and behave the same:
 * a full-screen scaffold with the big Doto title, plus choice / switch / slider rows.
 */

/** Full-screen settings page: theme background, header with back arrow, scrolling content. */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier
            .fillMaxSize()
            .background(DotlineTheme.colors.background),
    ) {
        ScreenHeader(title = title, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** A row with a switch at the end; the whole row toggles. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    SettingsRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = { FlatSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/** Title above a segmented pill control; the selected segment is inverted. */
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val pill = DotlineTheme.shapes.pill
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        BasicText(text = title, style = type.body.copy(color = colors.primary))
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            BasicText(text = subtitle, style = type.small.copy(color = colors.secondary))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.cardRaised, pill)
                .padding(4.dp),
        ) {
            for (option in options) {
                val value = option.first
                val label = option.second
                val isSelected = value == selected
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .background(if (isSelected) colors.highlight else Color.Transparent, pill)
                        .flatClickable(pill, onClick = { onSelect(value) }),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = label,
                        style = type.small.copy(color = if (isSelected) colors.onHighlight else colors.secondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

/** Title with its current value on the right and a pill slider underneath. */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = title,
                modifier = Modifier.weight(1f),
                style = type.body.copy(color = colors.primary),
            )
            BasicText(text = valueLabel, style = type.label.copy(color = colors.secondary))
        }
        Spacer(Modifier.height(10.dp))
        FlatSlider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

/** Small explanatory paragraph under a card (permissions, caveats). */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        style = DotlineTheme.type.small.copy(color = DotlineTheme.colors.secondary),
    )
}
