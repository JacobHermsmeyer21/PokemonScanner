package com.jacob.pokemonscanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.jacob.pokemonscanner.automation.AutomationServiceBridge
import com.jacob.pokemonscanner.capture.CaptureStatus
import com.jacob.pokemonscanner.capture.ScreenCaptureService
import com.jacob.pokemonscanner.database.PokemonRecordEntity
import com.jacob.pokemonscanner.model.AutomationProfile
import com.jacob.pokemonscanner.model.ScanPhase
import com.jacob.pokemonscanner.model.ScannerSnapshot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val projectionPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.start(this, result.resultCode, data)
            viewModel.startAutomatic()
            launchPokemonGo()
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = MaterialTheme.colorScheme.tertiary)) {
                val scanner by viewModel.scannerState.collectAsState()
                val profile by viewModel.profile.collectAsState()
                val inventory by viewModel.inventory.collectAsState()
                val captureActive by CaptureStatus.active.collectAsState()
                val automationConnected by AutomationServiceBridge.connected.collectAsState()
                ScannerHome(
                    scanner = scanner,
                    profile = profile,
                    inventory = inventory,
                    captureActive = captureActive,
                    accessibilityConnected = automationConnected,
                    onEnableAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onStart = ::requestAutomaticStart,
                    onPause = viewModel::pause,
                    onStop = {
                        viewModel.stop()
                        ScreenCaptureService.stop(this)
                    },
                    onAppraiseX = { viewModel.updateAppraisePoint(x = it) },
                    onAppraiseY = { viewModel.updateAppraisePoint(y = it) },
                    onInventorySearch = viewModel::updateInventorySearch,
                    onInventorySort = viewModel::updateInventorySort,
                )
            }
        }
    }

    private fun requestAutomaticStart() {
        if (!AutomationServiceBridge.isConnected) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val manager = getSystemService<MediaProjectionManager>() ?: return
        projectionPermission.launch(manager.createScreenCaptureIntent())
    }

    private fun launchPokemonGo() {
        packageManager.getLaunchIntentForPackage(POKEMON_GO_PACKAGE)?.let(::startActivity)
    }

    companion object { private const val POKEMON_GO_PACKAGE = "com.nianticlabs.pokemongo" }
}

@Composable
private fun ScannerHome(
    scanner: ScannerSnapshot,
    profile: AutomationProfile,
    inventory: InventoryUiState,
    captureActive: Boolean,
    accessibilityConnected: Boolean,
    onEnableAccessibility: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onAppraiseX: (Float) -> Unit,
    onAppraiseY: (Float) -> Unit,
    onInventorySearch: (String) -> Unit,
    onInventorySort: (InventorySort) -> Unit,
) {
    var accepted by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Pokemon Scanner", style = MaterialTheme.typography.headlineMedium)
            Text("Private internal build - capture and gestures remain on this device")
        }

        item {
            StatusCard(scanner, captureActive, accessibilityConnected)
        }

        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Automatic scanning", style = MaterialTheme.typography.titleLarge)
                    Text("Open a Pokemon detail screen before starting. The scanner opens Appraise, reads the bars, saves the result, and selects the next appraisal until a stop condition is reached.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                        Text("I understand this uses screen capture and accessibility gestures.")
                    }
                    if (!accessibilityConnected) {
                        Button(onClick = onEnableAccessibility) { Text("Enable automation service") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = accepted && scanner.phase !in ACTIVE_PHASES,
                            onClick = onStart,
                        ) { Text("Start automatic scan") }
                        OutlinedButton(enabled = scanner.phase in ACTIVE_PHASES, onClick = onPause) { Text("Pause") }
                        OutlinedButton(enabled = captureActive || scanner.phase in ACTIVE_PHASES, onClick = onStop) { Text("Stop") }
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Gesture calibration", style = MaterialTheme.typography.titleMedium)
                    Text("Appraise menu X: ${"%.3f".format(profile.appraiseMenuItem.x)}")
                    Slider(value = profile.appraiseMenuItem.x, onValueChange = onAppraiseX, valueRange = 0.45f..0.95f)
                    Text("Appraise menu Y: ${"%.3f".format(profile.appraiseMenuItem.y)}")
                    Slider(value = profile.appraiseMenuItem.y, onValueChange = onAppraiseY, valueRange = 0.45f..0.95f)
                    Text("Defaults are provisional until a screenshot of the opened Pokemon menu is added.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            InventoryBrowser(
                state = inventory,
                onQueryChange = onInventorySearch,
                onSortChange = onInventorySort,
            )
        }
        if (inventory.records.isEmpty()) {
            item {
                Card {
                    Text(
                        text = if (inventory.query.isBlank()) "No saved Pokemon yet." else "No Pokemon match this search.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        items(inventory.records, key = { it.id }) { record ->
            InventoryRecordRow(record)
            HorizontalDivider()
        }
    }
}

@Composable
private fun InventoryBrowser(
    state: InventoryUiState,
    onQueryChange: (String) -> Unit,
    onSortChange: (InventorySort) -> Unit,
) {
    var sortMenuOpen by rememberSaveable { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Saved inventory", style = MaterialTheme.typography.titleLarge)
                Text("${state.filteredCount} / ${state.totalCount}", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search name, CP, IVs, notes") },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    OutlinedButton(onClick = { sortMenuOpen = true }) {
                        Text("Sort: ${state.sort.label}")
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        InventorySort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onSortChange(option)
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                }
                if (state.query.isNotBlank()) {
                    TextButton(onClick = { onQueryChange("") }) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
private fun InventoryRecordRow(record: PokemonRecordEntity) {
    val displayName = record.nickname?.takeIf { it.isNotBlank() } ?: record.speciesName
    ListItem(
        headlineContent = { Text(displayName) },
        overlineContent = {
            if (displayName != record.speciesName) Text(record.speciesName)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("CP ${record.cp} - IV ${record.attackIv}/${record.defenseIv}/${record.staminaIv} - ${"%.1f".format(record.ivPercentage)}%")
                Text("Atk ${record.attackIv} - Def ${record.defenseIv} - HP ${record.staminaIv} - Total ${record.totalIv}/45")
                if (!record.form.isNullOrBlank()) Text("Form: ${record.form}")
                if (record.notes.isNotBlank()) Text(record.notes)
            }
        },
        trailingContent = {
            if (record.verified) Text("Verified", style = MaterialTheme.typography.labelSmall)
        },
    )
}

@Composable
private fun StatusCard(scanner: ScannerSnapshot, captureActive: Boolean, accessibilityConnected: Boolean) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(scanner.phase.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleMedium)
            Text(scanner.message)
            Text("Capture: ${if (captureActive) "active" else "off"} - Accessibility: ${if (accessibilityConnected) "connected" else "off"}")
            Text("Saved this run: ${scanner.scanCount} - Failures: ${scanner.failureCount}")
            scanner.current?.let { scan -> Text("${scan.speciesName.value ?: "Unknown"} - CP ${scan.cp.value ?: "?"}") }
        }
    }
}

private val ACTIVE_PHASES = setOf(
    ScanPhase.REQUESTING_PERMISSION,
    ScanPhase.WAITING_FOR_SCREEN,
    ScanPhase.WAITING_FOR_STABLE_FRAME,
    ScanPhase.DETECTING_SCREEN_TYPE,
    ScanPhase.READING_POKEMON_DETAILS,
    ScanPhase.OPENING_APPRAISAL,
    ScanPhase.WAITING_FOR_APPRAISAL,
    ScanPhase.READING_APPRAISAL,
    ScanPhase.VALIDATING_RESULT,
    ScanPhase.SAVING_POKEMON,
    ScanPhase.MOVING_TO_NEXT_POKEMON,
)
