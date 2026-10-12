package com.dotline.launcher.ui.guide

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.core.DefaultHome
import com.dotline.launcher.service.SystemActions
import com.dotline.launcher.ui.components.DottedDivider
import com.dotline.launcher.ui.components.PillButton
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.settings.SettingsPage
import com.dotline.launcher.ui.theme.DotlineTheme

/** A button under a guide step: its label and the settings screen it opens. */
private class GuideAction(val label: String, val intent: () -> Intent)

private class GuideStep(
    val title: String,
    val body: String,
    val actions: List<GuideAction> = emptyList(),
)

private fun generalSettings(): Intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private val Steps: List<GuideStep> = listOf(
    GuideStep(
        title = "Make Dotline your home app",
        body = "Settings > Apps > Choose default apps > Home app > Dotline. Until then the Home button opens One UI Home.",
        actions = listOf(GuideAction("Open home app settings") { DefaultHome.homeSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }),
    ),
    GuideStep(
        title = "Turn on dark mode",
        body = "Settings > Display > Dark. The notification shade, Settings and most apps then match Dotline's black look.",
        actions = listOf(GuideAction("Open display settings") { SystemActions.displaySettings() }),
    ),
    GuideStep(
        title = "Turn off colour palette",
        body = "Settings > Wallpaper and style > Colour palette > turn it off. One UI otherwise tints system surfaces with colours taken from your wallpaper.",
        actions = listOf(GuideAction("Open settings") { generalSettings() }),
    ),
    GuideStep(
        title = "Lock screen clock",
        body = "Settings > Lock screen and AOD > Clock style. Choose a plain digital style and set its colour to white. One UI has no dot-matrix clock, so this is the closest match.",
        actions = listOf(GuideAction("Open settings") { generalSettings() }),
    ),
    GuideStep(
        title = "Always On Display",
        body = "Settings > Lock screen and AOD > Always On Display. Pick a minimal clock style and a white colour, and turn off the extra widgets you do not need.",
        actions = listOf(GuideAction("Open settings") { generalSettings() }),
    ),
    GuideStep(
        title = "Turn off Edge panels and Bixby",
        body = "Settings > Display > Edge panels > off. Then Settings > Advanced features > Side button, and choose something other than Bixby for press and hold. This removes the extra handles and shortcuts that do not belong to the look.",
        actions = listOf(GuideAction("Open display settings") { SystemActions.displaySettings() }),
    ),
    GuideStep(
        title = "Quicker animations",
        body = "Turn on Developer options first: Settings > About phone > Software information, then tap Build number seven times. Then open Developer options and set Window animation scale, Transition animation scale and Animator duration scale to 0.5x.",
        actions = listOf(
            GuideAction("Open About phone") { SystemActions.deviceInfoSettings() },
            GuideAction("Open Developer options") { SystemActions.developerSettings() },
        ),
    ),
    GuideStep(
        title = "System font",
        body = "Settings > Display > Font size and style > Font style. A clean, geometric, light font sits best next to Dotline's dot-matrix headings.",
        actions = listOf(GuideAction("Open display settings") { SystemActions.displaySettings() }),
    ),
)

private val AlwaysOneUi: List<String> = listOf(
    "The notification shade and quick settings",
    "The Settings app",
    "System animations and transitions",
    "The lock screen and Always On Display layouts",
    "The status bar, navigation gestures and the recent apps screen",
    "The Glyph lights: they are hardware on Nothing phones and do not exist on Samsung",
)

/** "Finish the look": step by step, with shortcuts into the matching Samsung settings screens. */
@Composable
fun SetupGuideScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val shape = DotlineTheme.shapes.card

    SettingsPage(title = "Finish the look", onBack = onBack, modifier = modifier) {
        BasicText(
            text = "Dotline restyles your home screen, drawer, icons, widgets, wallpapers and sounds. " +
                "These last steps are in Android itself, so they cannot be done by an app. " +
                "Each button opens the matching settings screen.",
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            style = type.small.copy(color = colors.secondary),
        )

        SectionLabel("Steps")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Steps.forEachIndexed { index, step ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.card, shape)
                        .padding(20.dp),
                ) {
                    BasicText(
                        text = (index + 1).toString().padStart(2, '0'),
                        style = type.label.copy(color = colors.accent),
                    )
                    Spacer(Modifier.height(6.dp))
                    BasicText(text = step.title, style = type.bodyMedium.copy(color = colors.primary))
                    Spacer(Modifier.height(6.dp))
                    BasicText(text = step.body, style = type.small.copy(color = colors.secondary))
                    for (action in step.actions) {
                        Spacer(Modifier.height(12.dp))
                        PillButton(
                            text = action.label,
                            onClick = { open(context, action.intent) },
                            modifier = Modifier.fillMaxWidth(),
                            filled = false,
                        )
                    }
                }
            }
        }

        SectionLabel("What One UI always controls")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card, shape)
                .padding(20.dp),
        ) {
            BasicText(
                text = "Some parts of the phone belong to Samsung and no launcher can change them:",
                style = type.small.copy(color = colors.secondary),
            )
            for (line in AlwaysOneUi) {
                Spacer(Modifier.height(10.dp))
                DottedDivider()
                Spacer(Modifier.height(10.dp))
                BasicText(text = line, style = type.body.copy(color = colors.primary))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Opens a settings screen; falls back to the main Settings app, then to a short message. */
private fun open(context: Context, intent: () -> Intent) {
    try {
        context.startActivity(intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try {
            context.startActivity(generalSettings())
        } catch (e2: Exception) {
            CrashLog.record("guide: open settings", e2)
            Toast.makeText(context, "Could not open Settings", Toast.LENGTH_SHORT).show()
        }
    }
}
