package com.dotline.launcher.ui.sound

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.service.SystemActions
import com.dotline.launcher.sound.SoundCategory
import com.dotline.launcher.sound.SoundDefaults
import com.dotline.launcher.sound.SoundExporter
import com.dotline.launcher.sound.SoundLibrary
import com.dotline.launcher.sound.SoundPlayer
import com.dotline.launcher.sound.SoundSpec
import com.dotline.launcher.sound.Synth
import com.dotline.launcher.sound.WAVEFORM_BARS
import com.dotline.launcher.sound.amplitudeSummary
import com.dotline.launcher.ui.components.DottedDivider
import com.dotline.launcher.ui.components.PillButton
import com.dotline.launcher.ui.components.ScreenHeader
import com.dotline.launcher.ui.settings.InfoNote
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.flatClickable
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Rows of dots in the waveform strip (odd, so there is a centre row). */
private const val WAVE_ROWS = 7

/** Outer corner radius of the grouped list: the same 24dp as DotlineTheme.shapes.card. */
private val GroupCorner = 24.dp

/** The 24-bar amplitude summary of one sound, computed once off the main thread. */
@Immutable
private class WaveformBars(val values: FloatArray)

/** Corner shape for row [index] of [count]: only the first and last row round the group's outer corners. */
private fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GroupCorner else 0.dp
    val bottom = if (index == count - 1) GroupCorner else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

/** Length in seconds, two decimals for sounds shorter than one second. */
private fun lengthLabel(lengthMs: Int): String {
    val seconds = lengthMs / 1000.0
    return if (lengthMs >= 1000) {
        String.format(Locale.US, "%.1f s", seconds)
    } else {
        String.format(Locale.US, "%.2f s", seconds)
    }
}

private fun defaultLabel(category: SoundCategory): String = when (category) {
    SoundCategory.RINGTONE -> "Default ringtone set"
    SoundCategory.NOTIFICATION -> "Default notification sound set"
    SoundCategory.ALARM -> "Default alarm sound set"
    SoundCategory.UI -> "Default notification sound set"
}

/**
 * Sound Studio: preview the built-in synthesised sounds, save them to the Ringtones, Notifications
 * or Alarms folder and optionally make one the system default. Everything is generated on the
 * device. The only permission involved is "Modify system settings", and only for the default step;
 * it is explained first, and the system screen opens only after the user taps Continue.
 */
@Composable
fun SoundStudioScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember { SoundPlayer(context) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    val playingId by player.playingId.collectAsStateWithLifecycle()

    var categoryName by rememberSaveable { mutableStateOf(SoundCategory.RINGTONE.name) }
    val category = SoundCategory.entries.firstOrNull { it.name == categoryName } ?: SoundCategory.RINGTONE
    val sounds = remember(category) { SoundLibrary.byCategory(category) }
    val counts = remember {
        SoundCategory.entries.map { c -> Pair(c, SoundLibrary.byCategory(c).size) }
    }

    val waveforms = remember { mutableStateMapOf<String, WaveformBars>() }
    val savedUris = remember { mutableStateMapOf<String, Uri>() }
    val statuses = remember { mutableStateMapOf<String, String>() }
    var savingId by remember { mutableStateOf<String?>(null) }
    var permissionFor by remember { mutableStateOf<String?>(null) }
    var awaitingPermission by remember { mutableStateOf<String?>(null) }

    // Waveform summaries of the visible category, rendered one by one off the main thread.
    LaunchedEffect(category) {
        for (spec in sounds) {
            if (!waveforms.containsKey(spec.id)) {
                val summary = withContext(Dispatchers.Default) {
                    amplitudeSummary(Synth.render(spec), WAVEFORM_BARS)
                }
                waveforms[spec.id] = WaveformBars(summary)
            }
        }
    }

    val onTogglePlay: (SoundSpec) -> Unit = { spec ->
        if (player.playingId.value == spec.id) {
            player.stop()
        } else {
            player.play(spec)
        }
    }

    val onSave: (SoundSpec) -> Unit = { spec ->
        if (savingId == null) {
            savingId = spec.id
            permissionFor = null
            statuses[spec.id] = "Saving..."
            scope.launch {
                val result = SoundExporter.save(context, spec)
                val uri = result.getOrNull()
                if (uri != null) {
                    savedUris[spec.id] = uri
                    statuses[spec.id] = "Saved to " + SoundExporter.folderName(spec.category)
                } else {
                    val message = result.exceptionOrNull()?.message
                    statuses[spec.id] = if (message.isNullOrBlank()) "Could not save this sound" else message
                }
                savingId = null
            }
        }
    }

    // Writes the system setting off the main thread and reports the outcome in the row's status line.
    val applyDefault: (SoundSpec, Uri) -> Unit = { spec, uri ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                SoundDefaults.setDefault(context, uri, spec.category)
            }
            statuses[spec.id] = if (ok) defaultLabel(spec.category) else "Could not set the default sound"
        }
    }

    val onSetDefault: (SoundSpec) -> Unit = { spec ->
        val uri = savedUris[spec.id]
        if (uri != null) {
            if (SoundDefaults.canSetDefault(context)) {
                permissionFor = null
                applyDefault(spec, uri)
            } else {
                // Explain first; the system screen is only opened from the panel's Continue button.
                permissionFor = spec.id
            }
        }
    }

    val onContinuePermission: (SoundSpec) -> Unit = { spec ->
        permissionFor = null
        try {
            context.startActivity(SystemActions.writeSettings(context))
            awaitingPermission = spec.id
            statuses[spec.id] = "Allow Dotline on the next screen, then come back."
        } catch (e: Exception) {
            CrashLog.record("SoundStudioScreen: open write settings", e)
            statuses[spec.id] = "Could not open the system settings screen."
        }
    }

    // Back from the system screen: carry on with the default the user asked for, if it is now allowed.
    val onReturnFromSettings: () -> Unit = {
        val id = awaitingPermission
        if (id != null) {
            awaitingPermission = null
            val spec = SoundLibrary.all.firstOrNull { it.id == id }
            val uri = savedUris[id]
            if (spec != null && uri != null) {
                if (SoundDefaults.canSetDefault(context)) {
                    applyDefault(spec, uri)
                } else {
                    statuses[id] = "Permission not granted, so your default sound was not changed."
                }
            }
        }
    }
    val latestOnReturn by rememberUpdatedState(onReturnFromSettings)

    // A preview never keeps playing once the launcher is no longer on screen.
    DisposableEffect(lifecycleOwner, player) {
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_STOP -> player.stop()
                    Lifecycle.Event.ON_RESUME -> latestOnReturn()
                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(DotlineTheme.colors.background)
            // Screens are stacked over the home screen: swallow taps so they never reach it.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        ScreenHeader(title = "Sounds", onBack = onBack)

        CategoryChips(
            selected = category,
            counts = counts,
            onSelect = { picked ->
                if (picked != category) {
                    player.stop()
                    categoryName = picked.name
                }
            },
        )

        // A fresh list state per category, so switching category starts at the top.
        key(category) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(sounds, key = { _, item -> item.id }) { index, spec ->
                    val shape = remember(index, sounds.size) { groupShape(index, sounds.size) }
                    SoundRow(
                        spec = spec,
                        shape = shape,
                        isPlaying = playingId == spec.id,
                        bars = waveforms[spec.id],
                        status = statuses[spec.id],
                        saved = savedUris.containsKey(spec.id),
                        saving = savingId == spec.id,
                        askPermission = permissionFor == spec.id,
                        onTogglePlay = { onTogglePlay(spec) },
                        onSave = { onSave(spec) },
                        onSetDefault = { onSetDefault(spec) },
                        onContinue = { onContinuePermission(spec) },
                        onCancel = { permissionFor = null },
                    )
                }
                item(key = "note") {
                    InfoNote(
                        text = "Previews play at your media volume. UI click sounds are saved as notification " +
                            "sounds, so you can pick them in your phone's sound settings.",
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(
    selected: SoundCategory,
    counts: List<Pair<SoundCategory, Int>>,
    onSelect: (SoundCategory) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (entry in counts) {
            val chipCategory = entry.first
            CategoryChip(
                title = chipCategory.title,
                count = entry.second,
                selected = chipCategory == selected,
                onClick = { onSelect(chipCategory) },
            )
        }
    }
}

@Composable
private fun CategoryChip(title: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val shape = DotlineTheme.shapes.chip
    val fill = if (selected) colors.highlight else colors.card
    val textColor = if (selected) colors.onHighlight else colors.primary
    val countColor = if (selected) colors.onHighlight.copy(alpha = 0.6f) else colors.secondary
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .background(fill, shape)
            .flatClickable(shape, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = title,
            style = type.small.copy(color = textColor),
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = count.toString(),
            style = type.caption.copy(color = countColor),
            maxLines = 1,
        )
    }
}

/**
 * One sound. The row that is playing is the inverted one (light fill, dark text), like the selected
 * row on the Nothing settings pages; everything inside it switches to the matching tokens.
 */
@Composable
private fun SoundRow(
    spec: SoundSpec,
    shape: Shape,
    isPlaying: Boolean,
    bars: WaveformBars?,
    status: String?,
    saved: Boolean,
    saving: Boolean,
    askPermission: Boolean,
    onTogglePlay: () -> Unit,
    onSave: () -> Unit,
    onSetDefault: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val length = remember(spec.lengthMs) { lengthLabel(spec.lengthMs) }

    val rowFill = if (isPlaying) colors.highlight else colors.card
    val nameColor = if (isPlaying) colors.onHighlight else colors.primary
    val subColor = if (isPlaying) colors.onHighlight.copy(alpha = 0.6f) else colors.secondary
    val waveLit = if (isPlaying) colors.onHighlight else colors.secondary
    val waveOff = if (isPlaying) colors.onHighlight.copy(alpha = 0.18f) else colors.outline
    val buttonFill = if (isPlaying) colors.onHighlight else colors.cardRaised
    val buttonGlyph = if (isPlaying) colors.highlight else colors.primary

    Column(
        Modifier
            .fillMaxWidth()
            .background(rowFill, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayButton(
                playing = isPlaying,
                name = spec.name,
                fill = buttonFill,
                glyph = buttonGlyph,
                onClick = onTogglePlay,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = spec.name,
                    style = type.body.copy(color = nameColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    text = length,
                    style = type.caption.copy(color = subColor),
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                WaveformStrip(bars = bars, litColor = waveLit, offColor = waveOff)
            }
            Spacer(Modifier.width(12.dp))
            // The label stays "Save" while saving (the button just dims) so the row never reflows.
            PillButton(
                text = "Save",
                onClick = onSave,
                filled = false,
                enabled = !saving,
            )
        }

        if (status != null || saved) {
            Spacer(Modifier.height(10.dp))
            DottedDivider()
            Spacer(Modifier.height(10.dp))
        }
        if (status != null) {
            BasicText(
                text = status,
                style = type.label.copy(color = subColor),
            )
        }
        if (saved && !askPermission) {
            Spacer(Modifier.height(10.dp))
            // On the inverted (playing) row a light pill would vanish, so it flips to the dark style.
            PillButton(
                text = "Set as default",
                onClick = onSetDefault,
                modifier = Modifier.fillMaxWidth(),
                filled = !isPlaying,
            )
        }
        if (askPermission) {
            Spacer(Modifier.height(10.dp))
            PermissionPanel(onContinue = onContinue, onCancel = onCancel)
        }
    }
}

/**
 * Plain explanation shown BEFORE the user is sent to the system "Modify system settings" screen.
 * The two buttons are stacked so neither label can be cut off on a narrow screen or a large font.
 */
@Composable
private fun PermissionPanel(onContinue: () -> Unit, onCancel: () -> Unit) {
    val colors = DotlineTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background, DotlineTheme.shapes.cardSmall)
            .padding(16.dp),
    ) {
        BasicText(
            text = "To set a default sound, Android needs the Modify system settings permission. " +
                "Dotline only uses it to change your default ringtone, notification or alarm sound.",
            style = DotlineTheme.type.small.copy(color = colors.primary),
        )
        Spacer(Modifier.height(12.dp))
        PillButton(
            text = "Continue",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            filled = true,
        )
        Spacer(Modifier.height(8.dp))
        PillButton(
            text = "Cancel",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            filled = false,
        )
    }
}

/** Circular play / stop button drawn with a Canvas: a triangle, or two bars while playing. */
@Composable
private fun PlayButton(playing: Boolean, name: String, fill: Color, glyph: Color, onClick: () -> Unit) {
    val label = if (playing) "Stop $name" else "Play $name"
    Box(
        Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .background(fill, CircleShape)
            .flatClickable(CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val u = size.minDimension / 24f
            if (playing) {
                val corner = CornerRadius(1.5f * u, 1.5f * u)
                drawRoundRect(
                    color = glyph,
                    topLeft = Offset(6f * u, 5f * u),
                    size = Size(4f * u, 14f * u),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = glyph,
                    topLeft = Offset(14f * u, 5f * u),
                    size = Size(4f * u, 14f * u),
                    cornerRadius = corner,
                )
            } else {
                val path = Path()
                path.moveTo(8f * u, 5f * u)
                path.lineTo(19f * u, 12f * u)
                path.lineTo(8f * u, 19f * u)
                path.close()
                drawPath(path, glyph)
            }
        }
    }
}

/**
 * Dot-matrix waveform: [WAVEFORM_BARS] columns of [WAVE_ROWS] dots, lit symmetrically around the
 * centre row in proportion to the amplitude of that slice of the sound. A static drawing, nothing
 * animates.
 */
@Composable
private fun WaveformStrip(bars: WaveformBars?, litColor: Color, offColor: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(21.dp),
    ) {
        val columns = WAVEFORM_BARS
        val pitchX = size.width / columns
        val pitchY = size.height / WAVE_ROWS
        val radius = minOf(pitchX, pitchY) * 0.34f
        val mid = WAVE_ROWS / 2
        val values = bars?.values
        for (c in 0 until columns) {
            var amp = 0f
            if (values != null && c < values.size) {
                amp = values[c]
            }
            if (amp < 0f) amp = 0f
            if (amp > 1f) amp = 1f
            val half = (amp * mid).roundToInt()
            val x = pitchX * (c + 0.5f)
            for (r in 0 until WAVE_ROWS) {
                val lit = r >= mid - half && r <= mid + half
                drawCircle(
                    color = if (lit) litColor else offColor,
                    radius = radius,
                    center = Offset(x, pitchY * (r + 0.5f)),
                )
            }
        }
    }
}
