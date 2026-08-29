package com.jacob.pokemonscanner

import android.util.DisplayMetrics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jacob.pokemonscanner.model.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class AppSection(val label: String) {
    RUN("Run"),
    SETTINGS("Settings"),
    DIAGNOSTICS("Diagnostics"),
    LOG("Log"),
    HELP("Help"),
}

@Composable
fun GiftAssistantTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFFFFD60A),
        onPrimary = Color(0xFF1A1A1A),
        secondary = Color(0xFF66E3D2),
        onSecondary = Color(0xFF00201C),
        background = Color(0xFF101517),
        surface = Color(0xFF182126),
        surfaceVariant = Color(0xFF253238),
        error = Color(0xFFFFB4AB),
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftAssistantApp(
    workflow: GiftWorkflowSnapshot,
    settings: GiftAssistantSettings,
    onboardingComplete: Boolean,
    sessionLog: List<SessionLogEntry>,
    captureActive: Boolean,
    accessibilityConnected: Boolean,
    notificationGranted: Boolean,
    displayMetrics: DisplayMetrics,
    onEnableAccessibility: () -> Unit,
    onRequestNotifications: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSettingsChange: ((GiftAssistantSettings) -> GiftAssistantSettings) -> Unit,
    onClearLog: () -> Unit,
) {
    if (!onboardingComplete) {
        OnboardingScreen(
            accessibilityConnected,
            notificationGranted,
            onEnableAccessibility,
            onRequestNotifications,
            onCompleteOnboarding,
        )
        return
    }

    var section by rememberSaveable { mutableStateOf(AppSection.RUN) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Gift Access Assistant", fontWeight = FontWeight.Bold)
                            Text("Independent accessibility tool", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                )
                ScrollableTabRow(selectedTabIndex = section.ordinal, edgePadding = 8.dp) {
                    AppSection.entries.forEach { option ->
                        Tab(
                            selected = section == option,
                            onClick = { section = option },
                            modifier = Modifier.heightIn(min = 48.dp),
                            text = { Text(option.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (section) {
            AppSection.RUN -> RunScreen(
                workflow,
                settings,
                captureActive,
                accessibilityConnected,
                notificationGranted,
                onEnableAccessibility,
                onRequestNotifications,
                onStart,
                onPause,
                onResume,
                onStop,
                Modifier.padding(padding),
            )
            AppSection.SETTINGS -> SettingsScreen(settings, onSettingsChange, Modifier.padding(padding))
            AppSection.DIAGNOSTICS -> DiagnosticsScreen(
                workflow,
                captureActive,
                accessibilityConnected,
                displayMetrics,
                Modifier.padding(padding),
            )
            AppSection.LOG -> LogScreen(sessionLog, onClearLog, Modifier.padding(padding))
            AppSection.HELP -> HelpScreen(
                accessibilityConnected,
                notificationGranted,
                onEnableAccessibility,
                onRequestNotifications,
                Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun OnboardingScreen(
    accessibilityConnected: Boolean,
    notificationGranted: Boolean,
    onEnableAccessibility: () -> Unit,
    onRequestNotifications: () -> Unit,
    onComplete: () -> Unit,
) {
    var accepted by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "Welcome to Gift Access Assistant",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text("This app helps people with motor, dexterity, or other disabilities step through Pokémon GO gift management.")
        PermissionCard(
            title = "1. Accessibility service",
            explanation = "Used only after you press Start to inspect supported on-screen labels, perform gestures, show guided targets, and detect manual touch so automation can pause.",
            granted = accessibilityConnected,
            buttonLabel = "Open accessibility settings",
            onClick = onEnableAccessibility,
        )
        PermissionCard(
            title = "2. Screen capture",
            explanation = "Android asks for screen-capture consent at every run. Frames are processed on this device for OCR and are not uploaded or retained.",
            granted = false,
            buttonLabel = null,
            onClick = {},
            status = "Requested when you press Start",
        )
        PermissionCard(
            title = "3. Notifications",
            explanation = "Required for the persistent active-run notification with Pause and Stop controls.",
            granted = notificationGranted,
            buttonLabel = "Allow notifications",
            onClick = onRequestNotifications,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Important use warning", style = MaterialTheme.typography.titleLarge)
                Text("Third-party UI automation may be restricted by Pokémon GO’s current terms. Review the current rules before use. Automation can make mistakes after game updates.")
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text(
                        "I understand the risks, will begin each run deliberately, and will calibrate in guided mode first.",
                        Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        Button(
            onClick = onComplete,
            enabled = accepted,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Finish setup") }
        Text(
            "Not affiliated with or endorsed by Niantic, Pokémon, Nintendo, or The Pokémon Company.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    explanation: String,
    granted: Boolean,
    buttonLabel: String?,
    onClick: () -> Unit,
    status: String = if (granted) "Ready" else "Not enabled",
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(explanation)
            Text(if (granted) "Ready" else status, color = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
            if (!granted && buttonLabel != null) {
                OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 52.dp)) { Text(buttonLabel) }
            }
        }
    }
}

@Composable
private fun RunScreen(
    workflow: GiftWorkflowSnapshot,
    settings: GiftAssistantSettings,
    captureActive: Boolean,
    accessibilityConnected: Boolean,
    notificationGranted: Boolean,
    onEnableAccessibility: () -> Unit,
    onRequestNotifications: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var runAccepted by rememberSaveable { mutableStateOf(false) }
    val running = workflow.state !in setOf(
        GiftAutomationState.IDLE,
        GiftAutomationState.PAUSED,
        GiftAutomationState.COMPLETED,
        GiftAutomationState.ERROR,
    )
    LazyColumn(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            StatusCard(workflow, settings.dryRun)
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Run controls", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text("Start only while Pokémon GO is open on its map/home screen. The app verifies every supported screen before acting.")
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = runAccepted, onCheckedChange = { runAccepted = it })
                        Text("I am ready to start this run.", Modifier.padding(top = 12.dp))
                    }
                    if (!accessibilityConnected) {
                        OutlinedButton(onClick = onEnableAccessibility, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("Enable accessibility service")
                        }
                    }
                    if (!notificationGranted) {
                        OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("Enable active-run notifications")
                        }
                    }
                    Button(
                        onClick = onStart,
                        enabled = runAccepted && accessibilityConnected && notificationGranted && !running,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics {
                            contentDescription = if (settings.dryRun) "Start guided dry run" else "Start gift assistant"
                        },
                    ) { Text(if (settings.dryRun) "Start guided run" else "Start") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onPause, enabled = running, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                            Text("Pause")
                        }
                        OutlinedButton(
                            onClick = onResume,
                            enabled = workflow.state in setOf(GiftAutomationState.PAUSED, GiftAutomationState.ERROR) && captureActive,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) { Text("Resume") }
                        Button(
                            onClick = onStop,
                            enabled = captureActive || workflow.state != GiftAutomationState.IDLE,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) { Text("Stop") }
                    }
                }
            }
        }
        item { StatisticsCard(workflow) }
        workflow.errorMessage?.let { error ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Safe continuation paused", style = MaterialTheme.typography.titleMedium)
                        Text(error)
                        workflow.recoveryAction?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        item {
            Text(
                "Screen capture: ${if (captureActive) "active" else "off"} · Accessibility: ${if (accessibilityConnected) "connected" else "off"}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusCard(workflow: GiftWorkflowSnapshot, dryRun: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp).semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = "${workflow.state.readable()}. ${workflow.currentAction}"
            },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(if (dryRun) "GUIDED / NO TAPS" else "LIVE AUTOMATION", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(workflow.state.readable(), style = MaterialTheme.typography.headlineSmall)
            Text(workflow.currentAction, style = MaterialTheme.typography.bodyLarge)
            workflow.stopReason?.let { Text(it.label, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun StatisticsCard(workflow: GiftWorkflowSnapshot) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This session", style = MaterialTheme.typography.titleLarge)
            StatRow("Gifts opened", workflow.giftsOpened)
            StatRow("Gifts sent", workflow.giftsSent)
            StatRow("Friends skipped", workflow.friendsSkipped)
            Text("Items discarded", fontWeight = FontWeight.SemiBold)
            if (workflow.itemsDiscarded.isEmpty()) Text("None", style = MaterialTheme.typography.bodySmall)
            workflow.itemsDiscarded.toList().sortedBy { it.first.displayName }.forEach { (item, count) ->
                StatRow(item.displayName, count)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value.toString(), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsScreen(
    settings: GiftAssistantSettings,
    onChange: ((GiftAssistantSettings) -> GiftAssistantSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SettingCard("Friend sorting") {
                GiftSortMode.entries.forEach { mode ->
                    RadioSetting(mode.label, settings.sortMode == mode) { onChange { it.copy(sortMode = mode) } }
                }
                HorizontalDivider()
                GiftSortDirection.entries.forEach { direction ->
                    RadioSetting(direction.label, settings.sortDirection == direction) { onChange { it.copy(sortDirection = direction) } }
                }
            }
        }
        item {
            SettingCard("Safety and interaction") {
                SwitchSetting(
                    "Guided dry-run mode",
                    "Highlights the intended target without tapping. Enabled by default.",
                    settings.dryRun,
                ) { checked -> onChange { it.copy(dryRun = checked) } }
                SwitchSetting(
                    "Reduced motion",
                    "Avoids app animations and uses conservative transition waits.",
                    settings.reducedMotion,
                ) { checked -> onChange { it.copy(reducedMotion = checked) } }
                Text("Interaction delay: ${settings.interactionDelayMs} ms")
                Slider(
                    value = settings.interactionDelayMs.toFloat(),
                    onValueChange = { value -> onChange { it.copy(interactionDelayMs = value.toLong().coerceIn(350, 5_000)) } },
                    valueRange = 350f..5_000f,
                )
                Text("Screen timeout: ${settings.screenTimeoutMs / 1_000} seconds")
                Slider(
                    value = settings.screenTimeoutMs.toFloat(),
                    onValueChange = { value -> onChange { it.copy(screenTimeoutMs = value.toLong().coerceIn(3_000, 60_000)) } },
                    valueRange = 3_000f..60_000f,
                )
                Text("Retry limit: ${settings.retryLimit}")
                Slider(
                    value = settings.retryLimit.toFloat(),
                    onValueChange = { value -> onChange { it.copy(retryLimit = value.toInt().coerceIn(1, 8)) } },
                    valueRange = 1f..8f,
                    steps = 6,
                )
            }
        }
        item {
            SettingCard("Bag-full cleanup") {
                Text("Only checked items may be discarded. Protected items are blocked in code even if recognition is wrong.")
                CleanupItem.entries.filter { it.mayEverBeDiscarded }.forEach { item ->
                    val selected = item in settings.cleanupItems
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { role = Role.Checkbox },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { checked ->
                                onChange {
                                    it.copy(cleanupItems = if (checked) it.cleanupItems + item else it.cleanupItems - item)
                                }
                            },
                        )
                        Text(item.displayName)
                    }
                }
                Text(
                    "Nanab Berry is provisionally enabled while awaiting confirmation that “napnap berry” meant Nanab Berry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("Always protected: Max Potion, Max Revive, Golden Razz Berry, Silver Pinap Berry, and every unselected or unknown item.")
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun RadioSetting(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}

@Composable
private fun SwitchSetting(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DiagnosticsScreen(
    workflow: GiftWorkflowSnapshot,
    captureActive: Boolean,
    accessibilityConnected: Boolean,
    metrics: DisplayMetrics,
    modifier: Modifier = Modifier,
) {
    val calibrated = metrics.widthPixels == 1080 && metrics.heightPixels == 2340
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SettingCard("Recognition") {
                Text("Screen: ${workflow.currentScreen.readable()}")
                Text("Confidence: ${"%.1f".format(workflow.recognitionConfidence * 100)}%")
                Text("Source: ${workflow.recognitionSource?.readable() ?: "None"}")
                Text("Action: ${workflow.lastAction.readable()}")
                workflow.lastTarget?.let { target ->
                    Text("Target: ${target.safeLabel}")
                    Text(
                        "Bounds: ${"%.3f".format(target.bounds.left)}, ${"%.3f".format(target.bounds.top)}, " +
                            "${"%.3f".format(target.bounds.right)}, ${"%.3f".format(target.bounds.bottom)}",
                    )
                }
                Text("Capture: ${if (captureActive) "active" else "off"}")
                Text("Accessibility nodes: ${if (accessibilityConnected) "available" else "unavailable"}")
            }
        }
        item {
            SettingCard("Calibration") {
                Text("Device capture: ${metrics.widthPixels} × ${metrics.heightPixels} px at ${metrics.densityDpi} dpi")
                Text(
                    if (calibrated) "Matches the supplied 1080 × 2340 portrait fixture profile."
                    else "Unsupported size: keep guided mode enabled and capture every required state before live use.",
                    color = if (calibrated) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                )
                Text("Calibration flow: start in guided mode, inspect the yellow target overlay, make the selection manually, then press Resume. Record any offset and provide a screenshot with the full system bars visible.")
                Text("Imported profiles use normalized bounds, so matching aspect ratios can scale without raw fixed coordinates.")
            }
        }
    }
}

@Composable
private fun LogScreen(log: List<SessionLogEntry>, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val time = remember { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()) }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Local session log", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear") }
            }
            Text("Stored only on this device. Trainer names and friend fingerprints are not logged.", style = MaterialTheme.typography.bodySmall)
        }
        if (log.isEmpty()) item { Text("No activity logged yet.") }
        items(log.asReversed()) { entry ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("${time.format(entry.timestamp)} · ${entry.state.readable()}", fontWeight = FontWeight.SemiBold)
                    Text(entry.message)
                }
            }
        }
    }
}

@Composable
private fun HelpScreen(
    accessibilityConnected: Boolean,
    notificationGranted: Boolean,
    onEnableAccessibility: () -> Unit,
    onRequestNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            PermissionCard(
                "Accessibility service",
                "Restricted to the Pokémon GO package. Used for supported labels, gestures, guided overlays, and immediate pause on manual touch.",
                accessibilityConnected,
                "Open accessibility settings",
                onEnableAccessibility,
            )
        }
        item {
            PermissionCard(
                "Notifications",
                "Keeps Pause and Stop available from the persistent active-run notification.",
                notificationGranted,
                "Allow notifications",
                onRequestNotifications,
            )
        }
        item {
            SettingCard("Troubleshooting") {
                Text("• Start from the map/home screen and keep portrait orientation.")
                Text("• If recognition confidence drops, stop and capture that exact screen for calibration.")
                Text("• After a Pokémon GO update, validate the full flow in guided mode again.")
                Text("• If a system or network dialog appears, resolve it manually before Resume.")
                Text("• Item deletion remains blocked until item and quantity recognition both verify the dialog.")
            }
        }
        item {
            SettingCard("Privacy and limits") {
                Text("No Pokémon GO credentials are requested. Processing stays on device. The app does not inspect game memory, files, network traffic, or private APIs and does not attempt to hide automation.")
                Text("Not affiliated with or endorsed by Niantic, Pokémon, Nintendo, or The Pokémon Company.")
            }
        }
    }
}

private fun Enum<*>.readable(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
