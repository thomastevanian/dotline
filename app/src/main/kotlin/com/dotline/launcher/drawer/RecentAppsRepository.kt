package com.dotline.launcher.drawer

import android.content.Context
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.model.AppKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

/** The last apps launched from Dotline. Local only (one small file), shown only if the user enables it. */
class RecentAppsRepository(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.applicationContext.filesDir, "recent_apps.json")
    private val _recent = MutableStateFlow<List<AppKey>>(emptyList())
    val recent: StateFlow<List<AppKey>> = _recent.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = runCatching {
                if (!file.exists()) emptyList() else {
                    val arr = JSONArray(file.readText())
                    List(arr.length()) { AppKey.parse(arr.optString(it, "")) }.filterNotNull()
                }
            }.getOrDefault(emptyList())
            if (_recent.value.isEmpty()) _recent.value = loaded
        }
    }

    fun record(key: AppKey) {
        val next = promote(_recent.value, key)
        if (next == _recent.value) return
        _recent.value = next
        scope.launch(Dispatchers.IO) {
            runCatching { file.writeText(JSONArray(next.map { it.flat }).toString()) }
                .onFailure { CrashLog.record("recent apps write", it) }
        }
    }

    fun clear() {
        _recent.value = emptyList()
        scope.launch(Dispatchers.IO) { runCatching { file.delete() } }
    }

    companion object {
        const val MAX = 8

        /** Moves [key] to the front, drops duplicates, keeps at most [MAX]. */
        fun promote(current: List<AppKey>, key: AppKey): List<AppKey> =
            (listOf(key) + current.filterNot { it == key }).take(MAX)
    }
}
