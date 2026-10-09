package com.dotline.launcher.ui.home

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.WidgetProviderEntry
import com.dotline.launcher.data.model.BuiltinWidget
import com.dotline.launcher.home.WidgetHostMath
import com.dotline.launcher.home.WidgetSizes
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.components.ScreenHeader
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.home.widgets.widgetShapeFor
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.flatClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Biggest side, in pixels, of a provider preview picture kept in memory. */
private const val PREVIEW_MAX_PX = 320

private val PreviewShape = RoundedCornerShape(12.dp)
private val ChipShape = RoundedCornerShape(12.dp)

/**
 * Flat full-screen widget picker: Dotline's own widgets first (one chip per size), then every
 * Android app widget grouped by app. App widget previews and the provider list are loaded off the
 * main thread and only while this screen is open.
 */
@Composable
internal fun WidgetPicker(
    onPickBuiltin: (BuiltinWidget, Int, Int) -> Unit,
    onPickProvider: (WidgetProviderEntry) -> Unit,
    onDismiss: () -> Unit,
    cellWidthDp: Float,
    cellHeightDp: Float,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val colors = DotlineTheme.colors
    BackHandler(onBack = onDismiss)

    val providers: List<WidgetProviderEntry>? by produceState<List<WidgetProviderEntry>?>(initialValue = null, graph) {
        value = withContext(Dispatchers.Default) {
            try {
                graph.widgetHost.installedProviders()
            } catch (e: Exception) {
                CrashLog.record("widget picker: list providers", e)
                emptyList<WidgetProviderEntry>()
            }
        }
    }
    val groups: List<Pair<String, List<WidgetProviderEntry>>>? = remember(providers) {
        providers?.groupBy { it.packageName }?.values?.map { list -> list.first().appLabel to list }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            // Swallow taps so they never reach the home screen underneath.
            .pointerInput(Unit) { detectTapGestures(onTap = { }) },
    ) {
        ScreenHeader(title = "Widgets", onBack = onDismiss)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "label-dotline") { SectionLabel("Dotline") }
            items(BuiltinWidget.entries, key = { kind -> "builtin-" + kind.name }) { kind ->
                BuiltinWidgetCard(kind = kind, onPick = onPickBuiltin)
            }
            item(key = "label-apps") { SectionLabel("App widgets") }
            val loaded = groups
            if (loaded == null) {
                item(key = "loading") { PickerNote("Looking for widgets...") }
            } else if (loaded.isEmpty()) {
                item(key = "none") { PickerNote("No other widgets are installed.") }
            } else {
                for (group in loaded) {
                    val appLabel = group.first
                    val entries = group.second
                    item(key = "app-" + entries.first().packageName) {
                        BasicText(
                            text = appLabel,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                            style = DotlineTheme.type.bodyMedium.copy(color = colors.primary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    items(entries, key = { entry -> entry.provider.flattenToString() + "#" + entry.profile.hashCode() }) { entry ->
                        ProviderRow(
                            entry = entry,
                            cellWidthDp = cellWidthDp,
                            cellHeightDp = cellHeightDp,
                            onPick = onPickProvider,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerNote(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        style = DotlineTheme.type.small.copy(color = DotlineTheme.colors.secondary),
    )
}

@Composable
private fun BuiltinWidgetCard(kind: BuiltinWidget, onPick: (BuiltinWidget, Int, Int) -> Unit) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, DotlineTheme.shapes.card)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        BasicText(text = WidgetSizes.title(kind), style = type.bodyMedium.copy(color = colors.primary))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (size in WidgetSizes.supported(kind)) {
                SizeChip(spanX = size.first, spanY = size.second, onClick = { onPick(kind, size.first, size.second) })
            }
        }
    }
}

/** A tappable size option: a tiny outline of the widget shape above "4 x 2". */
@Composable
private fun SizeChip(spanX: Int, spanY: Int, onClick: () -> Unit) {
    val colors = DotlineTheme.colors
    val shape = widgetShapeFor(WidgetSizes.shapeFor(spanX, spanY))
    Column(
        modifier = Modifier
            .background(colors.cardRaised, ChipShape)
            .flatClickable(ChipShape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = (spanX * 11).dp, height = (spanY * 13).dp)
                .border(1.dp, colors.secondary, shape),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = "$spanX x $spanY",
            style = DotlineTheme.type.caption.copy(color = colors.secondary),
            maxLines = 1,
        )
    }
}

@Composable
private fun ProviderRow(
    entry: WidgetProviderEntry,
    cellWidthDp: Float,
    cellHeightDp: Float,
    onPick: (WidgetProviderEntry) -> Unit,
) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val settings = com.dotline.launcher.ui.LocalSettings.current
    val span = remember(entry, cellWidthDp, cellHeightDp, settings.gridColumns, settings.gridRows) {
        WidgetHostMath.spanFor(
            entry.minWidthDp, entry.minHeightDp, cellWidthDp, cellHeightDp,
            settings.gridColumns, settings.gridRows,
        )
    }
    val shape = DotlineTheme.shapes.card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, shape)
            .flatClickable(shape, onClick = { onPick(entry) })
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderPreview(entry = entry, modifier = Modifier.size(width = 84.dp, height = 64.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                text = entry.label,
                style = type.body.copy(color = colors.primary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = "${span.first} x ${span.second}",
                style = type.caption.copy(color = colors.secondary),
            )
        }
    }
}

/** The provider's own preview picture, loaded once and scaled down. A flat empty tile while loading or missing. */
@Composable
private fun ProviderPreview(entry: WidgetProviderEntry, modifier: Modifier) {
    val graph = LocalAppGraph.current
    val colors = DotlineTheme.colors
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, entry.provider, entry.profile) {
        value = withContext(Dispatchers.Default) {
            try {
                graph.widgetHost.previewDrawable(entry)?.toPreviewBitmap(PREVIEW_MAX_PX)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        modifier = modifier
            .background(colors.cardRaised, PreviewShape),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun Drawable.toPreviewBitmap(maxPx: Int): Bitmap? {
    val w = intrinsicWidth
    val h = intrinsicHeight
    if (w <= 0 || h <= 0) return null
    val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h))
    return toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
}
