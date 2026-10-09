package com.dotline.launcher.ui.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.dotline.launcher.data.NotesRepository
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.components.PillButton
import com.dotline.launcher.ui.theme.DotlineTheme

/**
 * Full-screen editor for the quick note: a flat card on a scrim with a plain text field. Every
 * change is saved as it is typed (the repository writes the file after a short pause), so closing
 * it by any route keeps the text.
 */
@Composable
fun NoteEditorPanel(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val shape = DotlineTheme.shapes.card
    var value by remember {
        val saved = graph.notes.text.value
        mutableStateOf(TextFieldValue(saved, TextRange(saved.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // The field is not attached yet on a very slow frame; the user can tap it.
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
            .systemBarsPadding()
            .imePadding()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(colors.card, shape)
                .border(1.dp, colors.outline, shape)
                // Taps inside the card must not close the panel.
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                .padding(24.dp),
        ) {
            BasicText(text = "Quick note", style = type.heading.copy(color = colors.primary))
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = value,
                onValueChange = { new ->
                    val limited = if (new.text.length > NotesRepository.MAX_CHARS) {
                        new.copy(text = new.text.take(NotesRepository.MAX_CHARS))
                    } else {
                        new
                    }
                    value = limited
                    graph.notes.set(limited.text)
                },
                textStyle = type.body.copy(color = colors.primary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 320.dp)
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (value.text.isEmpty()) {
                            BasicText(
                                text = "Write something...",
                                style = type.body.copy(color = colors.tertiary),
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            PillButton(text = "Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
