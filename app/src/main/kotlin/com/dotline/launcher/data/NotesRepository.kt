package com.dotline.launcher.data

import android.content.Context
import com.dotline.launcher.core.CrashLog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The quick local note shown by the notes widget. One plain-text file, never leaves the device. */
class NotesRepository(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.applicationContext.filesDir, "quick_note.txt")
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()
    private var saveJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            val saved = runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")
            if (_text.value.isEmpty()) _text.value = saved
        }
    }

    fun set(new: String) {
        val trimmed = new.take(MAX_CHARS)
        if (trimmed == _text.value) return
        _text.value = trimmed
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            withContext(Dispatchers.IO) {
                runCatching { file.writeText(trimmed) }.onFailure { CrashLog.record("notes write", it) }
            }
        }
    }

    companion object {
        const val MAX_CHARS = 2_000
    }
}
