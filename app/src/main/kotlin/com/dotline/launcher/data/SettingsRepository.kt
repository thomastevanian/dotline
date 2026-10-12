package com.dotline.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dotline.launcher.core.CrashLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "dotline_settings")

/**
 * Settings persisted in DataStore as one JSON string. [settings] always has a value
 * (defaults until the first read completes), so UI never has to handle a loading state.
 */
class SettingsRepository(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.settingsStore
    private val key = stringPreferencesKey("settings_v1")
    private val writeLock = Mutex()

    private val _loaded = MutableStateFlow(false)
    /** True once the persisted settings have been read (used to avoid flashing onboarding). */
    val loaded: StateFlow<Boolean> = _loaded

    val settings: StateFlow<Settings> = store.data
        .catch { e ->
            CrashLog.record("settings read", e)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { prefs ->
            val decoded = prefs[key]?.let { runCatching { SettingsJson.decode(it) }.getOrNull() } ?: Settings()
            _loaded.value = true
            decoded
        }
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    /** Atomically transforms the current settings and persists the result. */
    fun update(transform: (Settings) -> Settings) {
        scope.launch {
            writeLock.withLock {
                runCatching {
                    store.edit { prefs ->
                        val current = prefs[key]?.let { runCatching { SettingsJson.decode(it) }.getOrNull() } ?: Settings()
                        prefs[key] = SettingsJson.encode(transform(current)).toString()
                    }
                }.onFailure { CrashLog.record("settings write", it) }
            }
        }
    }

    /** Replaces everything (used by restore). */
    fun replace(new: Settings) = update { new }
}
