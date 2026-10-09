package com.dotline.launcher.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dotline.launcher.AppGraph
import com.dotline.launcher.data.ClockStyle
import com.dotline.launcher.data.Settings
import com.dotline.launcher.data.TimeFormat
import com.dotline.launcher.data.WeatherUnit
import com.dotline.launcher.data.WorldClock
import com.dotline.launcher.data.weather.GeoPlace
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.ChevronIcon
import com.dotline.launcher.ui.components.CloseIcon
import com.dotline.launcher.ui.components.DotClock
import com.dotline.launcher.ui.components.DotDate
import com.dotline.launcher.ui.components.PillButton
import com.dotline.launcher.ui.components.PlusIcon
import com.dotline.launcher.ui.components.SectionLabel
import com.dotline.launcher.ui.components.SettingsCard
import com.dotline.launcher.ui.components.SettingsRow
import com.dotline.launcher.ui.components.rememberNow
import com.dotline.launcher.ui.theme.DotlineTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private val ClockStyleOptions: List<Pair<ClockStyle, String>> = listOf(
    ClockStyle.DOT to "Dot matrix",
    ClockStyle.NORMAL to "Normal",
)

private val TimeFormatOptions: List<Pair<TimeFormat, String>> = listOf(
    TimeFormat.SYSTEM to "System",
    TimeFormat.H12 to "12 hour",
    TimeFormat.H24 to "24 hour",
)

private val WeatherUnitOptions: List<Pair<WeatherUnit, String>> = listOf(
    WeatherUnit.CELSIUS to "Celsius",
    WeatherUnit.FAHRENHEIT to "Fahrenheit",
)

/** Zones offered by the world clock "Add city" list. */
private val PopularZones: List<String> = listOf(
    "America/New_York",
    "America/Los_Angeles",
    "Europe/London",
    "Europe/Paris",
    "Europe/Berlin",
    "Asia/Dubai",
    "Asia/Kolkata",
    "Asia/Singapore",
    "Asia/Tokyo",
    "Australia/Sydney",
    "Pacific/Auckland",
    "America/Sao_Paulo",
)

/** The world clock widget shows two cities; adding a third drops the oldest. */
private const val MAX_WORLD_ZONES = 2

/** [current] with [zone] appended, keeping only the newest [MAX_WORLD_ZONES]. A zone already listed is kept as is. */
private fun worldZonesWith(current: List<String>, zone: String): List<String> {
    if (current.contains(zone)) return current
    val combined = current + zone
    return if (combined.size > MAX_WORLD_ZONES) combined.takeLast(MAX_WORLD_ZONES) else combined
}

private fun hasCoarseLocation(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Saves a settings change and then refreshes the weather. [WeatherRepository] reads the settings
 * flow, which only catches up with a write a moment later, so the refresh waits (at most three
 * seconds) until [applied] is true for the published settings.
 */
private fun updateThenRefreshWeather(
    graph: AppGraph,
    transform: (Settings) -> Settings,
    applied: (Settings) -> Boolean,
) {
    graph.settings.update(transform)
    graph.scope.launch {
        withTimeoutOrNull(3000L) { graph.settings.settings.first { applied(it) } }
        graph.weather.refreshIfStale(force = true)
    }
}

/** Clock look, time format, weather source and the world clock cities. */
@Composable
fun ClockWidgetsSettingsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secondary = DotlineTheme.colors.secondary

    var cityOpen by remember { mutableStateOf(false) }
    var cityInput by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<GeoPlace?>(null) }
    var cityMessage by remember { mutableStateOf<String?>(null) }

    var locationExplain by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var locationGranted by remember { mutableStateOf(hasCoarseLocation(context)) }

    var addZoneOpen by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationGranted = granted
        locationExplain = false
        if (granted) {
            locationMessage = null
            updateThenRefreshWeather(
                graph,
                { current -> current.copy(weatherUseLocation = true) },
                { current -> current.weatherUseLocation },
            )
        } else {
            locationMessage = "Location permission was not granted, so Dotline keeps using the typed city."
        }
    }

    val onSearch: () -> Unit = {
        val query = cityInput.trim()
        if (query.isNotEmpty() && !searching) {
            searching = true
            found = null
            cityMessage = null
            scope.launch {
                val place = graph.weather.lookupCity(query)
                found = place
                cityMessage = if (place == null) "No match found. Check the spelling and your connection." else null
                searching = false
            }
        }
    }

    val onUseCity: () -> Unit = {
        val place = found
        if (place != null) {
            updateThenRefreshWeather(
                graph,
                { current -> current.copy(weatherCity = place.name, weatherLat = place.lat, weatherLon = place.lon) },
                { current -> current.weatherCity == place.name && abs(current.weatherLat - place.lat) < 0.000001 },
            )
            cityOpen = false
            cityInput = ""
            found = null
            cityMessage = null
        }
    }

    val citySubtitle: String = if (s.weatherCity.isBlank()) {
        "Not set"
    } else if (s.weatherUseLocation) {
        s.weatherCity + " (used if location is unavailable)"
    } else {
        s.weatherCity
    }

    val locationNote: String? = locationMessage
    val availableZones: List<String> = PopularZones.filter { zone -> !s.worldClockZones.contains(zone) }

    SettingsPage(title = "Clock", onBack = onBack, modifier = modifier.imePadding()) {
        SectionLabel("Preview")
        ClockSettingsPreview()

        SectionLabel("Clock")
        SettingsCard {
            ChoiceRow(
                title = "Clock style",
                options = ClockStyleOptions,
                selected = s.clockStyle,
                onSelect = { value -> graph.settings.update { it.copy(clockStyle = value) } },
            )
            ChoiceRow(
                title = "Time format",
                options = TimeFormatOptions,
                selected = s.timeFormat,
                onSelect = { value -> graph.settings.update { it.copy(timeFormat = value) } },
            )
            SwitchRow(
                title = "Hide clock",
                subtitle = "Removes the big clock from the home screen",
                checked = s.hideClock,
                onCheckedChange = { value -> graph.settings.update { it.copy(hideClock = value) } },
            )
            SwitchRow(
                title = "Show date",
                checked = s.showDate,
                onCheckedChange = { value -> graph.settings.update { it.copy(showDate = value) } },
            )
        }

        SectionLabel("Weather")
        SettingsCard {
            SettingsRow(
                title = "City",
                subtitle = citySubtitle,
                onClick = { cityOpen = !cityOpen },
                trailing = { ChevronIcon(color = secondary) },
            )
            if (cityOpen) {
                WeatherCityPanel(
                    input = cityInput,
                    onInputChange = { value -> cityInput = value },
                    searching = searching,
                    found = found,
                    message = cityMessage,
                    onSearch = onSearch,
                    onUse = onUseCity,
                    onCancel = {
                        cityOpen = false
                        cityInput = ""
                        found = null
                        cityMessage = null
                    },
                )
            }
            ChoiceRow(
                title = "Temperature unit",
                options = WeatherUnitOptions,
                selected = s.weatherUnit,
                onSelect = { value -> graph.settings.update { it.copy(weatherUnit = value) } },
            )
            SwitchRow(
                title = "Use my approximate location",
                subtitle = "Coarse location, read only when the home screen opens",
                checked = s.weatherUseLocation,
                onCheckedChange = { wantOn ->
                    if (!wantOn) {
                        locationExplain = false
                        locationMessage = null
                        graph.settings.update { it.copy(weatherUseLocation = false) }
                    } else if (locationGranted) {
                        locationExplain = false
                        locationMessage = null
                        updateThenRefreshWeather(
                            graph,
                            { current -> current.copy(weatherUseLocation = true) },
                            { current -> current.weatherUseLocation },
                        )
                    } else {
                        locationExplain = true
                    }
                },
            )
            if (locationExplain) {
                CoreExplainPanel(
                    text = "Dotline reads your last known coarse location, only when the home screen opens, " +
                        "to show local weather. The typed city is used if you say no.",
                    confirmLabel = "Continue",
                    onConfirm = {
                        locationExplain = false
                        try {
                            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        } catch (e: Exception) {
                            locationMessage = "The permission screen could not be opened."
                        }
                    },
                    onCancel = { locationExplain = false },
                )
            }
        }
        if (locationNote != null) {
            InfoNote(locationNote)
        }
        if (s.weatherUseLocation && !locationGranted) {
            InfoNote("Location permission is off, so the typed city is used.")
        }
        InfoNote("Weather comes from Open-Meteo. It is the only network request Dotline makes, and it never runs in the background.")

        SectionLabel("World clock")
        SettingsCard {
            if (s.worldClockZones.isEmpty()) {
                SettingsRow(
                    title = "No cities",
                    subtitle = "Add up to two cities for the world clock widget",
                )
            }
            for (zone in s.worldClockZones) {
                key(zone) {
                    SettingsRow(
                        title = WorldClock.cityLabel(zone),
                        subtitle = zone + " - tap to remove",
                        onClick = {
                            graph.settings.update { current ->
                                current.copy(worldClockZones = current.worldClockZones.filter { it != zone })
                            }
                        },
                        trailing = { CloseIcon(color = secondary) },
                    )
                }
            }
            SettingsRow(
                title = "Add city",
                subtitle = "Two cities are kept, the oldest is replaced",
                onClick = { addZoneOpen = !addZoneOpen },
                trailing = { PlusIcon(color = secondary) },
            )
            if (addZoneOpen) {
                for (zone in availableZones) {
                    key(zone) {
                        SettingsRow(
                            title = WorldClock.cityLabel(zone),
                            subtitle = zone,
                            onClick = {
                                graph.settings.update { current ->
                                    current.copy(worldClockZones = worldZonesWith(current.worldClockZones, zone))
                                }
                                addZoneOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Live preview of the home screen clock and date with the current settings, on a flat card. */
@Composable
private fun ClockSettingsPreview() {
    val s = LocalSettings.current
    val colors = DotlineTheme.colors
    val now by rememberNow()

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.card, DotlineTheme.shapes.card)
            .padding(24.dp),
    ) {
        if (s.hideClock && !s.showDate) {
            BasicText(
                text = "The clock and the date are hidden on the home screen.",
                style = DotlineTheme.type.small.copy(color = colors.secondary),
            )
        } else {
            DotClock(now = now, dot = 5.dp, gap = 2.dp)
            if (s.showDate) {
                if (!s.hideClock) {
                    Spacer(Modifier.height(16.dp))
                }
                DotDate(now = now)
            }
        }
    }
}

/**
 * In-tree city entry: a text field, a Search button that looks the city up, and (once found) the
 * "Name, Country" result with a Use button. Sits inside a [SettingsCard] like a row.
 */
@Composable
private fun WeatherCityPanel(
    input: String,
    onInputChange: (String) -> Unit,
    searching: Boolean,
    found: GeoPlace?,
    message: String?,
    onSearch: () -> Unit,
    onUse: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val primary = colors.primary
    val focusRequester = remember { FocusRequester() }
    val selectionColors = remember(primary) {
        TextSelectionColors(handleColor = primary, backgroundColor = primary.copy(alpha = 0.3f))
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // The field is not attached yet; the user can tap it to focus.
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        BasicText(
            text = "Type the name of a city, then tap Search.",
            style = type.small.copy(color = colors.secondary),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(colors.cardRaised, DotlineTheme.shapes.pill)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = type.body.copy(color = primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    singleLine = true,
                    cursorBrush = SolidColor(primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (input.isEmpty()) {
                                BasicText(
                                    text = "City name",
                                    style = type.body.copy(color = colors.tertiary),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = if (searching) "Searching" else "Search",
                onClick = onSearch,
                modifier = Modifier.weight(1f),
                filled = true,
                enabled = input.isNotBlank() && !searching,
            )
            PillButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                filled = false,
            )
        }
        if (found != null) {
            Spacer(Modifier.height(16.dp))
            val country = found.country
            val label = if (country.isBlank()) found.name else found.name + ", " + country
            BasicText(
                text = label,
                style = type.bodyMedium.copy(color = colors.primary),
            )
            Spacer(Modifier.height(8.dp))
            PillButton(
                text = "Use",
                onClick = onUse,
                modifier = Modifier.fillMaxWidth(),
                filled = true,
            )
        }
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = message,
                style = type.small.copy(color = colors.secondary),
            )
        }
    }
}
