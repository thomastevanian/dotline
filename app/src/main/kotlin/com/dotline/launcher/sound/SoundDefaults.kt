package com.dotline.launcher.sound

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import com.dotline.launcher.core.CrashLog

/**
 * Makes an exported sound the system default ringtone, notification or alarm sound.
 * Needs the "Modify system settings" special access, which the user grants in system settings;
 * callers explain that first and only then send the user there.
 */
object SoundDefaults {
    /** True when Dotline may change the system default sounds. */
    fun canSetDefault(context: Context): Boolean =
        runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    /** Sets [uri] as the default sound for [category] (UI sounds count as notification sounds). Returns false on failure. */
    fun setDefault(context: Context, uri: Uri, category: SoundCategory): Boolean {
        val type = when (category) {
            SoundCategory.RINGTONE -> RingtoneManager.TYPE_RINGTONE
            SoundCategory.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
            SoundCategory.ALARM -> RingtoneManager.TYPE_ALARM
            SoundCategory.UI -> RingtoneManager.TYPE_NOTIFICATION
        }
        return runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(context, type, uri)
            true
        }.onFailure { CrashLog.record("SoundDefaults.setDefault ${category.name}", it) }
            .getOrDefault(false)
    }
}
