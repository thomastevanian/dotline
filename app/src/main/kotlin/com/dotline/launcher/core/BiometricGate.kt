package com.dotline.launcher.core

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** The activity behind a (possibly wrapped) Compose context, or null. */
tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * Optional fingerprint / face / screen-lock check, used only to show the hidden apps list. Nothing is
 * stored; if the phone has no screen lock the option is simply unavailable.
 */
object BiometricGate {
    private const val AUTH =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun available(context: Context): Boolean = try {
        BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (e: Exception) {
        false
    }

    /** Shows the system prompt; [onSuccess] runs only when the user passes it. */
    fun prompt(activity: FragmentActivity, title: String, subtitle: String?, onSuccess: () -> Unit) {
        try {
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTH)
            if (subtitle != null) builder.setSubtitle(subtitle)
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            CrashLog.record("biometric prompt", e)
        }
    }
}
