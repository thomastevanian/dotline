package com.dotline.launcher.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.IconShape
import com.dotline.launcher.data.IconStyle
import com.dotline.launcher.data.icons.AppIconView
import com.dotline.launcher.service.NotificationDots
import com.dotline.launcher.service.SystemActions
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.ChevronIcon
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.components.SettingsCard
import com.dotline.launcher.ui.components.SettingsRow
import com.dotline.launcher.ui.theme.DotlineTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val IconStyleOptions: List<Pair<IconStyle, String>> = listOf(
    IconStyle.MONOCHROME to "Monochrome",
    IconStyle.ORIGINAL to "Original",
    IconStyle.MONOCHROME_ACCENT to "Accent",
)

private val IconShapeOptions: List<Pair<IconShape, String>> = listOf(
    IconShape.CIRCLE to "Circle",
    IconShape.ROUNDED_SQUARE to "Rounded square",
)

/** The launcher intents an icon pack answers to (ADW, Nova and Apex style packs). */
private val IconPackActions: List<String> = listOf(
    "org.adw.launcher.THEMES",
    "com.novalauncher.THEME",
    "com.anddoes.launcher.THEME",
)

/** One installed icon pack: its package name and the label shown to the user. */
@Immutable
private class IconSettingsPackOption(val packageName: String, val label: String)

/**
 * Finds installed icon packs through PackageManager (the three actions are declared in the
 * manifest's queries block). Blocking binder calls: call only from Dispatchers.IO.
 */
@Suppress("DEPRECATION")
private fun queryIconPacks(context: Context): List<IconSettingsPackOption> {
    val pm = context.packageManager
    val found = LinkedHashMap<String, IconSettingsPackOption>()
    for (action in IconPackActions) {
        try {
            val matches = pm.queryIntentActivities(Intent(action), 0)
            for (info in matches) {
                val packageName: String = info.activityInfo?.packageName ?: continue
                if (found.containsKey(packageName)) continue
                val label: String = info.loadLabel(pm).toString()
                found[packageName] = IconSettingsPackOption(packageName, if (label.isBlank()) packageName else label)
            }
        } catch (e: Exception) {
            CrashLog.record("IconSettings: query icon packs", e)
        }
    }
    return found.values.sortedBy { it.label.lowercase() }
}

/** Icon options: style, shape, size, labels, icon pack and notification dots, with a live preview. */
@Composable
fun IconSettingsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val chevronColor = DotlineTheme.colors.secondary

    var packs by remember { mutableStateOf<List<IconSettingsPackOption>>(emptyList()) }
    var packsLoaded by remember { mutableStateOf(false) }
    var packPanelOpen by remember { mutableStateOf(false) }
    var dotsExplain by remember { mutableStateOf(false) }
    var accessEnabled by remember { mutableStateOf(NotificationDots.isEnabled(context)) }

    // One-shot lookup off the main thread.
    LaunchedEffect(Unit) {
        packs = withContext(Dispatchers.IO) { queryIconPacks(context) }
        packsLoaded = true
    }

    // Notification Access is granted on a system screen: look again whenever we come back.
    DisposableEffect(lifecycleOwner, context) {
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_RESUME) {
                    accessEnabled = NotificationDots.isEnabled(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val packSubtitle: String = if (s.iconPack.isEmpty()) {
        "None"
    } else {
        val match = packs.firstOrNull { it.packageName == s.iconPack }
        if (match != null) match.label else s.iconPack
    }

    SettingsPage(title = "Icons", onBack = onBack, modifier = modifier) {
        SectionLabel("Preview")
        IconSettingsPreview()

        SectionLabel("Look")
        SettingsCard {
            ChoiceRow(
                title = "Style",
                subtitle = "Accent is monochrome with a red glyph",
                options = IconStyleOptions,
                selected = s.iconStyle,
                onSelect = { value -> graph.settings.update { it.copy(iconStyle = value) } },
            )
            ChoiceRow(
                title = "Shape",
                options = IconShapeOptions,
                selected = s.iconShape,
                onSelect = { value -> graph.settings.update { it.copy(iconShape = value) } },
            )
            CoreSliderRow(
                title = "Size",
                saved = s.iconSize,
                onSave = { value ->
                    val rounded = (value * 100f).roundToInt() / 100f
                    graph.settings.update { it.copy(iconSize = rounded) }
                },
                valueRange = 0.7f..1.3f,
                formatLabel = { value -> (value * 100f).roundToInt().toString() + "%" },
            )
            SwitchRow(
                title = "Show labels",
                checked = s.showLabels,
                onCheckedChange = { value -> graph.settings.update { it.copy(showLabels = value) } },
            )
        }

        SectionLabel("Icon pack")
        SettingsCard {
            SettingsRow(
                title = "Icon pack",
                subtitle = packSubtitle,
                onClick = { packPanelOpen = !packPanelOpen },
                trailing = { ChevronIcon(color = chevronColor) },
            )
            if (packPanelOpen) {
                SettingsRow(
                    title = "None",
                    subtitle = "Use Dotline's own icon style",
                    highlighted = s.iconPack.isEmpty(),
                    onClick = {
                        graph.settings.update { it.copy(iconPack = "") }
                        packPanelOpen = false
                    },
                )
                for (pack in packs) {
                    key(pack.packageName) {
                        SettingsRow(
                            title = pack.label,
                            subtitle = pack.packageName,
                            highlighted = pack.packageName == s.iconPack,
                            onClick = {
                                graph.settings.update { it.copy(iconPack = pack.packageName) }
                                packPanelOpen = false
                            },
                        )
                    }
                }
                if (packsLoaded && packs.isEmpty()) {
                    SettingsRow(
                        title = "No icon packs found",
                        subtitle = "Install an icon pack made for ADW, Nova or Apex launchers.",
                    )
                }
            }
        }
        InfoNote("Icons the pack does not cover keep the style chosen above.")

        SectionLabel("Notifications")
        SettingsCard {
            SwitchRow(
                title = "Notification dots",
                subtitle = "A red dot on apps that have notifications",
                checked = s.notificationDots,
                onCheckedChange = { wantOn ->
                    if (!wantOn) {
                        dotsExplain = false
                        graph.settings.update { it.copy(notificationDots = false) }
                    } else if (NotificationDots.isEnabled(context)) {
                        dotsExplain = false
                        graph.settings.update { it.copy(notificationDots = true) }
                    } else {
                        dotsExplain = true
                    }
                },
            )
            if (dotsExplain) {
                CoreExplainPanel(
                    text = "Dotline can show a red dot on apps that have notifications. " +
                        "This needs Notification Access. Dotline only sees which apps have a notification, " +
                        "never their content.",
                    confirmLabel = "Continue",
                    onConfirm = {
                        dotsExplain = false
                        graph.settings.update { it.copy(notificationDots = true) }
                        try {
                            context.startActivity(SystemActions.notificationAccessSettings())
                        } catch (e: Exception) {
                            CrashLog.record("IconSettings: open notification access", e)
                        }
                    },
                    onCancel = { dotsExplain = false },
                )
            }
            if (s.notificationDots && !accessEnabled && !dotsExplain) {
                SettingsRow(
                    title = "Notification Access is off",
                    subtitle = "Dots show up once Dotline is allowed in system settings. Tap to open them.",
                    onClick = {
                        try {
                            context.startActivity(SystemActions.notificationAccessSettings())
                        } catch (e: Exception) {
                            CrashLog.record("IconSettings: open notification access", e)
                        }
                    },
                    trailing = { ChevronIcon(color = chevronColor) },
                )
            }
        }
    }
}

/**
 * Four sample tiles drawn by the real icon pipeline ([AppIconView]) with the current settings, on
 * the same canvas colour the home screen uses.
 */
@Composable
private fun IconSettingsPreview() {
    val graph = LocalAppGraph.current
    val colors = DotlineTheme.colors
    val shape = DotlineTheme.shapes.card
    val apps by graph.apps.apps.collectAsStateWithLifecycle()
    val sample = remember(apps) { apps.take(4) }

    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.background, shape)
            .border(1.dp, colors.outline, shape)
            .padding(horizontal = 8.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (sample.isEmpty()) {
            BasicText(
                text = "No apps to preview yet",
                style = DotlineTheme.type.small.copy(color = colors.secondary),
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                for (app in sample) {
                    key(app.key) {
                        AppIconView(app = app, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
