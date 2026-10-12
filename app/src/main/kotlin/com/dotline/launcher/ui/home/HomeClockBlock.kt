package com.dotline.launcher.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.DotClock
import com.dotline.launcher.ui.components.DotDate
import com.dotline.launcher.ui.components.rememberNow

/**
 * The Nothing style clock block of home page 0: the dot-matrix time with the dot-matrix date beneath
 * it, left aligned below the status bar. Draws nothing when the "hide clock" setting is on.
 *
 * The time is read here (and only here), so the once-a-minute tick recomposes just this block and
 * never the app grid next to it.
 */
@Composable
fun HomeClockBlock(modifier: Modifier = Modifier) {
    val settings = LocalSettings.current
    if (!settings.hideClock) {
        val now by rememberNow()
        Column(
            modifier = modifier
                .statusBarsPadding()
                .padding(start = 24.dp, top = 12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            DotClock(now = now, dot = 6.dp, gap = 2.dp)
            if (settings.showDate) {
                Spacer(Modifier.height(14.dp))
                DotDate(now = now)
            }
        }
    }
}
