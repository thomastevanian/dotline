package com.dotline.launcher.ui.wallpaper

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.components.CloseIcon
import com.dotline.launcher.ui.components.DottedDivider
import com.dotline.launcher.ui.components.PillButton
import com.dotline.launcher.ui.components.ScreenHeader
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.components.SettingsCard
import com.dotline.launcher.ui.components.SettingsRow
import com.dotline.launcher.ui.settings.InfoNote
import com.dotline.launcher.ui.settings.SliderRow
import com.dotline.launcher.ui.settings.SwitchRow
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.flatClickable
import com.dotline.launcher.wallpaper.PixelSize
import com.dotline.launcher.wallpaper.WallpaperApplier
import com.dotline.launcher.wallpaper.WallpaperPattern
import com.dotline.launcher.wallpaper.WallpaperPreset
import com.dotline.launcher.wallpaper.WallpaperPresets
import com.dotline.launcher.wallpaper.WallpaperRenderer
import com.dotline.launcher.wallpaper.WallpaperSizing
import com.dotline.launcher.wallpaper.WallpaperSpec
import com.dotline.launcher.wallpaper.WallpaperSpecJson
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Width in pixels of the live preview bitmap; the height follows the real screen's aspect ratio. */
private const val PREVIEW_WIDTH_PX = 360

/** Size in pixels of the preset thumbnails (9:19). */
private const val THUMB_WIDTH_PX = 90
private const val THUMB_HEIGHT_PX = 190

/** Slider drags re-render the preview only after the value has been still for this long. */
private const val DEBOUNCE_MS = 60L

/** How long the status line ("Applied", "Saved") stays visible. */
private const val STATUS_MS = 4000L

private const val MAX_WORD = 24
private const val MAX_NAME = 32

private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** A colour choice in a swatch row: ARGB value and a spoken label. */
@Immutable
private class Swatch(val color: Int, val label: String)

private val DotSwatches: List<Swatch> = listOf(
    Swatch(0xFFFFFFFF.toInt(), "White"),
    Swatch(0xFFD4D4D4.toInt(), "Light grey"),
    Swatch(0xFF8A8A8A.toInt(), "Grey"),
    Swatch(0xFF000000.toInt(), "Black"),
    Swatch(0xFFD71921.toInt(), "Red"),
)

private val BackgroundSwatches: List<Swatch> = listOf(
    Swatch(0xFF000000.toInt(), "Black"),
    Swatch(0xFF0D0D0D.toInt(), "Near black"),
    Swatch(0xFF2A2A2A.toInt(), "Dark grey"),
    Swatch(0xFFE3E3E3.toInt(), "Light"),
)

private fun currentTimeText(): String = LocalTime.now().format(ClockFormat)

/** Cache key of a thumbnail: the id plus the spec, so a re-imported preset with the same id redraws. */
private fun thumbKey(preset: WallpaperPreset): String = preset.id + ":" + preset.spec.hashCode()

private fun targetLabel(target: WallpaperApplier.Target): String = when (target) {
    WallpaperApplier.Target.HOME -> "Home screen"
    WallpaperApplier.Target.LOCK -> "Lock screen"
    WallpaperApplier.Target.BOTH -> "Both"
}

private fun targetHint(target: WallpaperApplier.Target): String = when (target) {
    WallpaperApplier.Target.HOME -> "Behind your apps and widgets"
    WallpaperApplier.Target.LOCK -> "Shown while the phone is locked"
    WallpaperApplier.Target.BOTH -> "The same wallpaper on home and lock screens"
}

/** Status line after a successful apply. */
private fun targetDone(target: WallpaperApplier.Target): String = when (target) {
    WallpaperApplier.Target.HOME -> "Applied to the home screen"
    WallpaperApplier.Target.LOCK -> "Applied to the lock screen"
    WallpaperApplier.Target.BOTH -> "Applied to home and lock screens"
}

/** "55%" for the dot size slider. */
private fun percentLabel(fraction: Float): String = (fraction * 100f).roundToInt().toString() + "%"

/** "4.0%" (one decimal) for the spacing slider, which works in small steps. */
private fun tenthsLabel(fraction: Float): String {
    val tenths = (fraction * 1000f).roundToInt()
    return (tenths / 10).toString() + "." + (tenths % 10).toString() + "%"
}

/** Renders [spec] on a background thread and wraps it for Compose; null when drawing failed. */
private suspend fun renderImage(spec: WallpaperSpec, width: Int, height: Int, timeText: String): ImageBitmap? {
    return withContext(Dispatchers.Default) {
        try {
            WallpaperRenderer.render(spec, width, height, timeText).asImageBitmap()
        } catch (e: Exception) {
            CrashLog.record("WallpaperStudio render", e)
            null
        }
    }
}

/**
 * Wallpaper Studio: procedural dot wallpapers drawn on the device at the phone's exact resolution.
 * The preview and the preset thumbnails are rendered off the main thread; nothing is loaded from
 * the network and no permission is needed to set the wallpaper.
 */
@Composable
fun WallpaperStudioScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = LocalAppGraph.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val colors = DotlineTheme.colors
    val userPresets by graph.wallpapers.presets.collectAsStateWithLifecycle()

    // The working spec lives as JSON text so it survives rotation and process death.
    var specJson by rememberSaveable { mutableStateOf(WallpaperSpecJson.toJsonString(WallpaperSpec())) }
    val spec: WallpaperSpec = remember(specJson) { WallpaperSpecJson.fromJsonString(specJson) ?: WallpaperSpec() }

    var saveOpen by rememberSaveable { mutableStateOf(false) }
    var saveName by rememberSaveable { mutableStateOf("") }
    var applyOpen by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<WallpaperPreset?>(null) }

    fun update(transform: (WallpaperSpec) -> WallpaperSpec) {
        val base = WallpaperSpecJson.fromJsonString(specJson) ?: WallpaperSpec()
        specJson = WallpaperSpecJson.toJsonString(transform(base))
    }

    // Real screen size in pixels (portrait, capped), used for the final bitmap and the preview shape.
    val screenSize: PixelSize = remember(configuration) {
        val dm = context.resources.displayMetrics
        WallpaperSizing.exportSize(dm.widthPixels, dm.heightPixels)
    }
    val previewHeightPx = remember(screenSize) { WallpaperSizing.heightFor(PREVIEW_WIDTH_PX, screenSize) }

    // Live preview: debounced, drawn on Dispatchers.Default. A newer spec cancels an older render.
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(spec, previewHeightPx) {
        delay(DEBOUNCE_MS)
        val image = renderImage(spec, PREVIEW_WIDTH_PX, previewHeightPx, currentTimeText())
        if (image != null) preview = image
    }

    // Thumbnails of every preset, drawn once each, one after the other, off the main thread.
    val thumbs = remember { mutableStateMapOf<String, ImageBitmap>() }
    val allPresets = remember(userPresets) { userPresets + WallpaperPresets.all }
    LaunchedEffect(allPresets) {
        val timeText = currentTimeText()
        for (preset in allPresets) {
            val key = thumbKey(preset)
            if (!thumbs.containsKey(key)) {
                val image = renderImage(preset.spec, THUMB_WIDTH_PX, THUMB_HEIGHT_PX, timeText)
                if (image != null) thumbs[key] = image
            }
        }
    }

    // The status line clears itself after a few seconds (one-shot delay, not a loop).
    LaunchedEffect(status) {
        if (status.isNotEmpty()) {
            delay(STATUS_MS)
            status = ""
        }
    }

    fun applyWallpaper(target: WallpaperApplier.Target) {
        if (busy) return
        busy = true
        val working = spec
        val size = screenSize
        scope.launch {
            var failure: Throwable? = null
            try {
                val timeText = currentTimeText()
                val bitmap = withContext(Dispatchers.Default) {
                    WallpaperRenderer.render(working, size.width, size.height, timeText)
                }
                try {
                    failure = WallpaperApplier.apply(context, bitmap, target).exceptionOrNull()
                } finally {
                    bitmap.recycle()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                failure = e
            } catch (e: Exception) {
                failure = e
            }
            val error = failure
            busy = false
            applyOpen = false
            status = if (error == null) {
                targetDone(target)
            } else {
                "Could not apply: " + (error.message ?: error.javaClass.simpleName).take(80)
            }
        }
    }

    fun saveCurrent() {
        val name = saveName.trim().ifEmpty { "My wallpaper" }
        graph.wallpapers.save(name, spec)
        saveOpen = false
        focusManager.clearFocus()
        status = "Saved preset " + name
    }

    BackHandler(
        onBack = {
            if (deleteTarget != null) {
                deleteTarget = null
            } else if (saveOpen) {
                saveOpen = false
            } else if (applyOpen) {
                if (!busy) {
                    applyOpen = false
                }
            } else {
                onBack()
            }
        },
    )

    // Preview frame: as tall as fits comfortably, with the real screen's shape.
    val aspect = screenSize.width.toFloat() / screenSize.height.toFloat()
    var previewH = (configuration.screenHeightDp * 0.42f).coerceIn(200f, 460f)
    var previewW = previewH * aspect
    val maxW = (configuration.screenWidthDp - 64).toFloat().coerceAtLeast(120f)
    if (previewW > maxW) {
        previewW = maxW
        previewH = previewW / aspect
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            // Screens are stacked over the home screen: swallow taps so they never reach it.
            .pointerInput(Unit) { detectTapGestures(onTap = { }) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            ScreenHeader(title = "Wallpapers", onBack = onBack)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PhonePreview(
                        image = preview,
                        fallback = Color(spec.effectiveBackground),
                        width = previewW.dp,
                        height = previewH.dp,
                    )
                }
                BasicText(
                    text = screenSize.width.toString() + " x " + screenSize.height.toString() + " px",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    style = DotlineTheme.type.label.copy(color = colors.secondary, textAlign = TextAlign.Center),
                )

                SectionLabel("Pattern")
                PatternChips(
                    selected = spec.pattern,
                    onSelect = { pattern -> update { it.copy(pattern = pattern) } },
                )

                if (spec.pattern == WallpaperPattern.DOT_TEXT) {
                    SectionLabel("Text")
                    SettingsCard {
                        WordBlock(
                            value = spec.text,
                            onValueChange = { word -> update { it.copy(text = word.take(MAX_WORD)) } },
                            onDone = { focusManager.clearFocus() },
                        )
                    }
                }

                SectionLabel("Dots")
                SettingsCard {
                    SliderRow(
                        title = "Dot size",
                        value = spec.dotSize,
                        onValueChange = { v -> update { it.copy(dotSize = v) } },
                        valueRange = 0.15f..1f,
                        valueLabel = percentLabel(spec.dotSize),
                    )
                    SliderRow(
                        title = "Spacing",
                        value = spec.spacing,
                        onValueChange = { v -> update { it.copy(spacing = v) } },
                        valueRange = 0.012f..0.09f,
                        valueLabel = tenthsLabel(spec.spacing),
                    )
                    // Dot text has no random part, so the seed row would do nothing there.
                    if (spec.pattern != WallpaperPattern.DOT_TEXT) {
                        SettingsRow(
                            title = "Variation",
                            subtitle = "Pattern number " + (((spec.seed % 100000L) + 100000L) % 100000L).toString(),
                            onClick = { update { it.copy(seed = Random.nextLong()) } },
                            trailing = {
                                PillButton(
                                    text = "Shuffle",
                                    onClick = { update { it.copy(seed = Random.nextLong()) } },
                                    filled = false,
                                )
                            },
                        )
                    }
                }

                SectionLabel("Colours")
                SettingsCard {
                    SwatchBlock(
                        title = "Dots",
                        swatches = DotSwatches,
                        selected = spec.color,
                        onSelect = { c -> update { it.copy(color = c) } },
                    )
                    SwatchBlock(
                        title = "Background",
                        swatches = BackgroundSwatches,
                        selected = spec.background,
                        onSelect = { c -> update { it.copy(background = c) } },
                    )
                    SwitchRow(
                        title = "Invert",
                        checked = spec.invert,
                        onCheckedChange = { on -> update { it.copy(invert = on) } },
                        subtitle = "Swap dot and background colours",
                    )
                }

                if (userPresets.isNotEmpty()) {
                    SectionLabel("Your presets")
                    PresetStrip(
                        presets = userPresets,
                        thumbs = thumbs,
                        current = spec,
                        onPick = { preset -> update { preset.spec } },
                        onLongPress = { preset -> deleteTarget = preset },
                    )
                    InfoNote("Press and hold one of your presets to delete it.")
                }

                SectionLabel("Presets")
                PresetStrip(
                    presets = WallpaperPresets.all,
                    thumbs = thumbs,
                    current = spec,
                    onPick = { preset -> update { preset.spec } },
                    onLongPress = null,
                )
                Spacer(Modifier.height(24.dp))
            }

            ActionBar(
                status = status,
                onSave = {
                    saveName = "Wallpaper " + (userPresets.size + 1).toString()
                    saveOpen = true
                },
                onApply = { applyOpen = true },
            )
        }

        if (applyOpen) {
            ApplyPanel(
                size = screenSize,
                busy = busy,
                onChoose = { target -> applyWallpaper(target) },
                onDismiss = { applyOpen = false },
            )
        }
        if (saveOpen) {
            SaveDialog(
                name = saveName,
                onNameChange = { text -> saveName = text.take(MAX_NAME) },
                onSave = { saveCurrent() },
                onCancel = { saveOpen = false },
            )
        }
        val pendingDelete = deleteTarget
        if (pendingDelete != null) {
            DeleteDialog(
                name = pendingDelete.name,
                onConfirm = {
                    thumbs.remove(thumbKey(pendingDelete))
                    graph.wallpapers.delete(pendingDelete.id)
                    deleteTarget = null
                },
                onCancel = { deleteTarget = null },
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------
 * Preview and controls
 * ------------------------------------------------------------------------------------------ */

/** Phone-shaped frame (24dp corners, 1dp outline) around the preview bitmap. */
@Composable
private fun PhonePreview(image: ImageBitmap?, fallback: Color, width: Dp, height: Dp) {
    val shape = DotlineTheme.shapes.card
    Box(
        Modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(fallback)
            .border(1.dp, DotlineTheme.colors.outline, shape),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

/** Horizontally scrolling row of pattern chips (12dp corners); the selected one is inverted. */
@Composable
private fun PatternChips(selected: WallpaperPattern, onSelect: (WallpaperPattern) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (pattern in WallpaperPattern.entries) {
            ChoiceChip(
                label = pattern.title,
                selected = pattern == selected,
                onClick = { onSelect(pattern) },
            )
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DotlineTheme.colors
    val shape = DotlineTheme.shapes.chip
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .background(if (selected) colors.highlight else colors.card, shape)
            .semantics { this.selected = selected }
            .flatClickable(shape, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = DotlineTheme.type.small.copy(color = if (selected) colors.onHighlight else colors.primary),
            maxLines = 1,
        )
    }
}

/** Title, hint and a pill text field for the custom word of the dot text pattern. */
@Composable
private fun WordBlock(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        BasicText(text = "Custom word", style = type.body.copy(color = colors.primary))
        Spacer(Modifier.height(2.dp))
        BasicText(
            text = "Leave it empty to show the current time.",
            style = type.small.copy(color = colors.secondary),
        )
        Spacer(Modifier.height(12.dp))
        PillField(
            value = value,
            onValueChange = onValueChange,
            hint = "Type a word",
            onDone = onDone,
        )
    }
}

/** Single line text field in an outlined pill. */
@Composable
private fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DotlineTheme.colors
    val pill = DotlineTheme.shapes.pill
    val textStyle = DotlineTheme.type.body.copy(color = colors.primary)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        cursorBrush = SolidColor(colors.primary),
        decorationBox = { innerTextField ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .border(1.dp, colors.tertiary, pill)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    BasicText(text = hint, style = textStyle.copy(color = colors.tertiary))
                }
                innerTextField()
            }
        },
    )
}

/** A row inside a settings card: a title and a row of round colour swatches. */
@Composable
private fun SwatchBlock(title: String, swatches: List<Swatch>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = DotlineTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        BasicText(text = title, style = DotlineTheme.type.body.copy(color = colors.primary))
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (swatch in swatches) {
                SwatchDot(
                    swatch = swatch,
                    selected = swatch.color == selected,
                    onSelect = onSelect,
                )
            }
        }
    }
}

/** One round swatch; the selected one gets a ring in the primary colour. */
@Composable
private fun SwatchDot(swatch: Swatch, selected: Boolean, onSelect: (Int) -> Unit) {
    val colors = DotlineTheme.colors
    Box(
        Modifier
            .size(48.dp)
            .semantics {
                contentDescription = swatch.label
                this.selected = selected
            }
            .flatClickable(CircleShape, onClick = { onSelect(swatch.color) }),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(46.dp)
                    .border(2.dp, colors.primary, CircleShape),
            )
        }
        Box(
            Modifier
                .size(34.dp)
                .background(Color(swatch.color), CircleShape)
                .border(1.dp, colors.outline, CircleShape),
        )
    }
}

/**
 * Horizontally scrolling strip of preset thumbnails. Tapping one applies its spec to the working
 * wallpaper; [onLongPress] (user presets only) is called on press and hold.
 */
@Composable
private fun PresetStrip(
    presets: List<WallpaperPreset>,
    thumbs: Map<String, ImageBitmap>,
    current: WallpaperSpec,
    onPick: (WallpaperPreset) -> Unit,
    onLongPress: ((WallpaperPreset) -> Unit)?,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = presets, key = { preset -> preset.id }) { preset ->
            PresetThumb(
                preset = preset,
                image = thumbs[thumbKey(preset)],
                selected = preset.spec == current,
                onPick = onPick,
                onLongPress = onLongPress,
            )
        }
    }
}

@Composable
private fun PresetThumb(
    preset: WallpaperPreset,
    image: ImageBitmap?,
    selected: Boolean,
    onPick: (WallpaperPreset) -> Unit,
    onLongPress: ((WallpaperPreset) -> Unit)?,
) {
    val colors = DotlineTheme.colors
    val shape = DotlineTheme.shapes.chip
    val longPress = onLongPress
    val longClick: (() -> Unit)? = if (longPress == null) null else ({ longPress(preset) })
    Column(
        Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 118.dp)
                .clip(shape)
                .background(Color(preset.spec.effectiveBackground))
                .semantics {
                    contentDescription = preset.name
                    this.selected = selected
                }
                .flatClickable(shape, onLongClick = longClick, onClick = { onPick(preset) })
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) colors.primary else colors.outline,
                    shape = shape,
                ),
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = preset.name,
            modifier = Modifier.fillMaxWidth(),
            style = DotlineTheme.type.caption.copy(
                color = if (selected) colors.primary else colors.secondary,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Status line plus the two main buttons, pinned below the scrolling page. */
@Composable
private fun ActionBar(status: String, onSave: () -> Unit, onApply: () -> Unit) {
    val colors = DotlineTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (status.isNotEmpty()) {
                BasicText(
                    text = status,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = DotlineTheme.type.label.copy(color = colors.secondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                text = "Save preset",
                onClick = onSave,
                modifier = Modifier.weight(1f),
                filled = false,
            )
            PillButton(
                text = "Apply",
                onClick = onApply,
                modifier = Modifier.weight(1f),
                filled = true,
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------
 * In-tree overlays (no Dialog window): a scrim, a bottom panel and centred dialogs.
 * ------------------------------------------------------------------------------------------ */

/** Full-screen translucent scrim; a tap outside the content calls [onDismiss]. */
@Composable
private fun ScrimLayer(
    onDismiss: () -> Unit,
    contentAlignment: Alignment,
    content: @Composable BoxScope.() -> Unit,
) {
    val latestDismiss by rememberUpdatedState(onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DotlineTheme.colors.scrim)
            .pointerInput(Unit) { detectTapGestures(onTap = { latestDismiss() }) }
            .imePadding(),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Bottom panel with the three places a wallpaper can be set. */
@Composable
private fun ApplyPanel(
    size: PixelSize,
    busy: Boolean,
    onChoose: (WallpaperApplier.Target) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DotlineTheme.colors
    val panelShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 0.dp, bottomStart = 0.dp)
    ScrimLayer(
        onDismiss = { if (!busy) onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.cardRaised, panelShape)
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "Set wallpaper",
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    style = DotlineTheme.type.heading.copy(color = colors.primary),
                )
                Box(
                    Modifier
                        .size(44.dp)
                        .flatClickable(CircleShape, enabled = !busy, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    CloseIcon()
                }
            }
            DottedDivider()
            InfoNote(
                "Drawn at " + size.width.toString() + " x " + size.height.toString() +
                    " px, the size of your screen. Nothing leaves your phone.",
            )
            SettingsCard {
                for (target in WallpaperApplier.Target.entries) {
                    SettingsRow(
                        title = targetLabel(target),
                        subtitle = if (busy) "Applying..." else targetHint(target),
                        onClick = if (busy) null else ({ onChoose(target) }),
                    )
                }
            }
        }
    }
}

/** Centred card on the scrim with a Doto title and a dotted divider. */
@Composable
private fun DialogCard(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DotlineTheme.colors
    val shape = DotlineTheme.shapes.card
    ScrimLayer(onDismiss = onDismiss, contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(colors.card, shape)
                .border(1.dp, colors.outline, shape)
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                .padding(20.dp),
        ) {
            BasicText(text = title, style = DotlineTheme.type.heading.copy(color = colors.primary))
            Spacer(Modifier.height(10.dp))
            DottedDivider()
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SaveDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // The field is not attached yet; the user can tap it to focus.
        }
    }
    DialogCard(title = "Save preset", onDismiss = onCancel) {
        BasicText(
            text = "Give this wallpaper a name. It is kept on this phone only.",
            style = DotlineTheme.type.small.copy(color = DotlineTheme.colors.secondary),
        )
        Spacer(Modifier.height(14.dp))
        PillField(
            value = name,
            onValueChange = onNameChange,
            hint = "Name",
            onDone = onSave,
            modifier = Modifier.focusRequester(focusRequester),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                filled = false,
            )
            PillButton(
                text = "Save",
                onClick = onSave,
                modifier = Modifier.weight(1f),
                filled = true,
            )
        }
    }
}

@Composable
private fun DeleteDialog(name: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    DialogCard(title = "Delete preset", onDismiss = onCancel) {
        BasicText(
            text = "Delete \"" + name + "\" from your presets?",
            style = DotlineTheme.type.body.copy(color = DotlineTheme.colors.primary),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                filled = false,
            )
            PillButton(
                text = "Delete",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                filled = true,
            )
        }
    }
}
