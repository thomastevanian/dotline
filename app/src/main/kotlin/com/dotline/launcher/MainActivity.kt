package com.dotline.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.dotline.launcher.ui.AppRoot

/**
 * The one and only activity. Registered as MAIN + HOME + DEFAULT (+ LAUNCHER so it can also be
 * opened from another launcher), launchMode singleTask. A Home press while we are already in
 * front arrives as [onNewIntent] and is forwarded to the UI through [AppGraph.homePressed].
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Registered before any Compose BackHandler, so it has the LOWEST priority: back does
        // nothing on the root home screen, while overlays/screens still get their own back handling.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            graph.homePressed.tryEmit(Unit)
        }
    }
}
