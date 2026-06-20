package com.codeisland.buddylite

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.codeisland.buddylite.data.model.PermissionState
import com.codeisland.buddylite.service.BuddySyncService
import com.codeisland.buddylite.ui.PermissionCoordinator
import com.codeisland.buddylite.ui.navigation.BuddyNavGraph
import com.codeisland.buddylite.ui.theme.BuddyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var permissionCoordinator: PermissionCoordinator

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        updatePermissionState(allGranted)
        if (allGranted) maybeRequestNotificationPermission()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatePermissionState(granted)
        if (granted) startSyncService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionCoordinator = PermissionCoordinator(this)

        val app = application as BuddyLiteApp
        val repo = app.locator.repository

        CoroutineScope(Dispatchers.Main).launch {
            repo.loadInitialState()
        }

        setContent {
            BuddyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by repo.dashboardState.collectAsStateWithLifecycle()
                    BuddyNavGraph(
                        state = state,
                        onRequestPermissions = { requestPermissions() },
                        onOpenAppSettings = { permissionCoordinator.openAppSettings() },
                        onOpenBatterySettings = { permissionCoordinator.openBatteryOptimizationSettings() },
                        onOpenAutostartSettings = { permissionCoordinator.openAutostartSettings() },
                        onEnterDemo = { repo.enterDemoMode() },
                        onExitDemo = { repo.exitDemoMode() },
                        onCycleDemo = { repo.cycleDemoState() },
                        onRescan = {
                            repo.unbindTransport()
                            startSyncService()
                        },
                        isHyperOS = permissionCoordinator.isHyperOS()
                    )
                }
            }
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        val missing = permissionCoordinator.getMissingPermissions()
        val app = application as BuddyLiteApp

        if (missing.isEmpty()) {
            updatePermissionState(true)
            startSyncService()
            return
        }

        // Check if we should skip rationales and go directly to request
        val bluetoothPerms = missing.filter {
            it == Manifest.permission.BLUETOOTH_SCAN ||
                it == Manifest.permission.BLUETOOTH_CONNECT ||
                it == Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (bluetoothPerms.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(bluetoothPerms.toTypedArray())
        } else {
            maybeRequestNotificationPermission()
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            permissionCoordinator.checkAll().notifications.not()
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            updatePermissionState(true)
            startSyncService()
        }
    }

    private fun updatePermissionState(allGranted: Boolean) {
        val app = application as BuddyLiteApp
        app.locator.repository.updatePermissionState(
            if (allGranted) PermissionState.GRANTED else PermissionState.DENIED
        )
    }

    private fun startSyncService() {
        val intent = Intent(this, BuddySyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
