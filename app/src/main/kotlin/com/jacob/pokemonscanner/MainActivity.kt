package com.jacob.pokemonscanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.jacob.pokemonscanner.automation.AutomationServiceBridge
import com.jacob.pokemonscanner.capture.CaptureStatus
import com.jacob.pokemonscanner.capture.ScreenCaptureService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var notificationGranted by mutableStateOf(false)

    private val projectionPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.start(this, result.resultCode, data)
            viewModel.start()
            launchPokemonGo()
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationGranted = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshNotificationPermission()
        setContent {
            val workflow by viewModel.workflow.collectAsState()
            val settings by viewModel.settings.collectAsState()
            val onboardingComplete by viewModel.onboardingComplete.collectAsState()
            val savedLog by viewModel.savedLog.collectAsState()
            val liveLog by viewModel.liveLog.collectAsState()
            val captureActive by CaptureStatus.active.collectAsState()
            val accessibilityConnected by AutomationServiceBridge.connected.collectAsState()

            GiftAssistantTheme {
                GiftAssistantApp(
                    workflow = workflow,
                    settings = settings,
                    onboardingComplete = onboardingComplete,
                    sessionLog = if (liveLog.isNotEmpty()) liveLog else savedLog,
                    captureActive = captureActive,
                    accessibilityConnected = accessibilityConnected,
                    notificationGranted = notificationGranted,
                    displayMetrics = resources.displayMetrics,
                    onEnableAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onRequestNotifications = ::requestNotifications,
                    onCompleteOnboarding = viewModel::completeOnboarding,
                    onStart = ::requestStart,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onStop = {
                        viewModel.stop()
                        ScreenCaptureService.stop(this)
                    },
                    onSettingsChange = viewModel::updateSettings,
                    onClearLog = viewModel::clearLog,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationPermission()
    }

    private fun requestStart() {
        if (!AutomationServiceBridge.isConnected) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val manager = getSystemService<MediaProjectionManager>() ?: return
        projectionPermission.launch(manager.createScreenCaptureIntent())
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else notificationGranted = true
    }

    private fun refreshNotificationPermission() {
        notificationGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun launchPokemonGo() {
        packageManager.getLaunchIntentForPackage(POKEMON_GO_PACKAGE)?.let(::startActivity)
    }

    private companion object {
        const val POKEMON_GO_PACKAGE = "com.nianticlabs.pokemongo"
    }
}
