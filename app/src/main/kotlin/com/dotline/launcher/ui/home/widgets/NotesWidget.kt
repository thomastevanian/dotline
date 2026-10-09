package com.dotline.launcher.ui.home.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotline.launcher.home.InfoWidgetMath
import com.dotline.launcher.home.WidgetShape
import com.dotline.launcher.home.WidgetSizes
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.theme.DotlineTheme

/* ------------------------------------------------------------------------------------------
 * Notes widget: the quick note (graph.notes) on the home screen.
 *
 *  - 4x2 and 2x2 card: caption NOTE and the start of the note in Space Grotesk 14 sp, as many
 *    lines as the card is high.
 *  - 4x1 capsule: caption NOTE over one line of the note.
 *
 * An empty note shows "Tap to write a note". A tap opens the editor (NoteEditorPanel) through
 * LocalWidgetActions.editNote. The note is read from a StateFlow, so the widget recomposes only
 * when the text changes.
 * ------------------------------------------------------------------------------------------ */

/** The widget never lays out more than this many characters of a note. */
private const val NOTE_PREVIEW_CHARS = 400

private const val NOTE_SIZE = 14f
private const val NOTE_LINE = 19f

@Composable
fun NotesWidget(spanX: Int, spanY: Int, modifier: Modifier = Modifier) {
    val shape = WidgetSizes.shapeFor(spanX, spanY)
    val actions = LocalWidgetActions.current
    val graph = LocalAppGraph.current
    val note by graph.notes.text.collectAsStateWithLifecycle()
    val preview = remember(note) { InfoWidgetMath.notePreview(note, NOTE_PREVIEW_CHARS) }
    val spoken = if (preview.isEmpty()) "Note, empty. Tap to write a note." else "Note, " + preview
    WidgetSurface(shape = shape, modifier = modifier) {
        InfoWidgetTapArea(onClick = actions.editNote) {
            Box(Modifier.fillMaxSize().infoDescription(spoken)) {
                when (shape) {
                    WidgetShape.CARD -> NotesCard(preview)
                    WidgetShape.CAPSULE -> NotesCapsule(preview)
                    WidgetShape.CIRCLE -> InfoNotice(shape = shape, caption = "NOTE", message = "")
                }
            }
        }
    }
}

/** 4x2 and 2x2. */
@Composable
private fun NotesCard(preview: String) {
    val colors = DotlineTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().padding(InfoCardPadding)) {
        val gap = 8f
        val lines = InfoWidgetMath.fitLines(maxHeight.value - INFO_CAPTION_HEIGHT - gap, NOTE_LINE, 12)
        Column(Modifier.fillMaxSize()) {
            InfoCaption("NOTE", colors.secondary)
            Spacer(Modifier.height(gap.dp))
            if (preview.isEmpty()) {
                InfoBodyText("Tap to write a note", NOTE_SIZE, NOTE_LINE, colors.secondary, 2)
            } else {
                InfoBodyText(preview, NOTE_SIZE, NOTE_LINE, colors.primary, lines)
            }
        }
    }
}

/** 4x1. */
@Composable
private fun NotesCapsule(preview: String) {
    val colors = DotlineTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(InfoCapsulePadding),
        verticalArrangement = Arrangement.Center,
    ) {
        InfoCaption("NOTE", colors.secondary)
        Spacer(Modifier.height(2.dp))
        if (preview.isEmpty()) {
            InfoBodyText("Tap to write a note", NOTE_SIZE, NOTE_LINE, colors.secondary, 1)
        } else {
            InfoBodyText(preview, NOTE_SIZE, NOTE_LINE, colors.primary, 1)
        }
    }
}
