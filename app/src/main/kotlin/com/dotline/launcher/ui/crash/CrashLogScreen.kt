package com.dotline.launcher.ui.crash

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.ui.components.DotText
import com.dotline.launcher.ui.components.DottedDivider
import com.dotline.launcher.ui.components.PillButton
import com.dotline.launcher.ui.components.ScreenHeader
import com.dotline.launcher.ui.theme.DotlineTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Same separator [CrashLog.read] puts between entries. Only used to lay entries out as separate cards. */
private const val ENTRY_SEPARATOR = "\n\n--------------------\n\n"

/** One recorded crash: the first line (time and place) and the rest (device line and stack trace). */
@Immutable
private class LogEntry(val header: String, val body: String)

/** The whole log, parsed off the main thread. [full] is what Copy and Share send. */
@Immutable
private class LogSnapshot(val full: String, val entries: List<LogEntry>)

/** Reads and parses the crash log. Blocking file I/O: call only from Dispatchers.IO. */
private fun loadSnapshot(context: Context): LogSnapshot? {
    val full = CrashLog.read(context) ?: return null
    val entries = ArrayList<LogEntry>()
    for (raw in full.split(ENTRY_SEPARATOR)) {
        val text = raw.trim()
        if (text.isEmpty()) continue
        val newline = text.indexOf('\n')
        if (newline < 0) {
            entries.add(LogEntry(text, ""))
        } else {
            entries.add(LogEntry(text.substring(0, newline), text.substring(newline + 1).trim()))
        }
    }
    if (entries.isEmpty()) return null
    return LogSnapshot(full, entries)
}

private fun copyToClipboard(context: Context, text: String): Boolean {
    return try {
        val manager = context.getSystemService(ClipboardManager::class.java)
        if (manager == null) {
            false
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("Dotline crash log", text))
            true
        }
    } catch (e: Exception) {
        false
    }
}

private fun shareText(context: Context, text: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Dotline crash log")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share crash log"))
    } catch (e: ActivityNotFoundException) {
        // No app can handle the share intent: nothing to do.
    } catch (e: Exception) {
        CrashLog.record("CrashLogScreen: share", e)
    }
}

/**
 * Shows the on-device crash log in mono text, with Copy, Share and Clear. The file is read once
 * on Dispatchers.IO when the screen opens.
 */
@Composable
fun CrashLogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<LogSnapshot?>(null) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // An unreadable log must show the empty state, never crash the crash viewer itself.
        snapshot = withContext(Dispatchers.IO) {
            try {
                loadSnapshot(context)
            } catch (e: Exception) {
                null
            }
        }
        loaded = true
    }

    // "Copied" label on the button for a moment, then back to "Copy". One-shot, not a loop.
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500L)
            copied = false
        }
    }

    val current = snapshot

    Column(
        modifier
            .fillMaxSize()
            .background(DotlineTheme.colors.background)
            // Screens are stacked over the home screen: swallow taps so they never reach it.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        ScreenHeader(title = "Crash log", onBack = onBack)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (loaded) {
                if (current == null) {
                    EmptyState()
                } else {
                    LogList(current)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                text = if (copied) "Copied" else "Copy",
                onClick = {
                    val full = snapshot?.full
                    if (full != null && copyToClipboard(context, full)) {
                        copied = true
                    }
                },
                modifier = Modifier.weight(1f),
                filled = true,
                enabled = current != null,
            )
            PillButton(
                text = "Share",
                onClick = {
                    val full = snapshot?.full
                    if (full != null) {
                        shareText(context, full)
                    }
                },
                modifier = Modifier.weight(1f),
                filled = false,
                enabled = current != null,
            )
            PillButton(
                text = "Clear",
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { CrashLog.clear(context) }
                        snapshot = null
                    }
                },
                modifier = Modifier.weight(1f),
                filled = false,
                enabled = current != null,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DotText(
            text = "0",
            dot = 6.dp,
            gap = 2.dp,
            color = DotlineTheme.colors.tertiary,
        )
        Spacer(Modifier.height(20.dp))
        BasicText(
            text = "No crashes recorded.",
            style = DotlineTheme.type.body.copy(color = DotlineTheme.colors.secondary),
        )
    }
}

@Composable
private fun LogList(snapshot: LogSnapshot) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        for (index in snapshot.entries.indices) {
            if (index > 0) {
                Spacer(Modifier.height(12.dp))
            }
            LogEntryCard(snapshot.entries[index])
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    val colors = DotlineTheme.colors
    val mono = DotlineTheme.type.label.copy(fontSize = 11.sp, lineHeight = 16.sp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.card, DotlineTheme.shapes.card)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        BasicText(
            text = entry.header,
            style = mono.copy(fontWeight = FontWeight.Bold, color = colors.primary),
        )
        if (entry.body.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            DottedDivider()
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = entry.body,
                style = mono.copy(color = colors.primary),
            )
        }
    }
}
