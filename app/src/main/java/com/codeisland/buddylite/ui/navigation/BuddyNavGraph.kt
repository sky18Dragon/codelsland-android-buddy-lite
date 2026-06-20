package com.codeisland.buddylite.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codeisland.buddylite.data.model.BuddyDashboardState
import com.codeisland.buddylite.ui.dashboard.DashboardScreen
import com.codeisland.buddylite.ui.diagnostics.DiagnosticsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val DIAGNOSTICS = "diagnostics"
}

@Composable
fun BuddyNavGraph(
    state: BuddyDashboardState,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
    onEnterDemo: () -> Unit,
    onExitDemo: () -> Unit,
    onCycleDemo: () -> Unit,
    onRescan: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSkip: () -> Unit,
    onFocus: () -> Unit,
    isHyperOS: Boolean
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                state = state,
                onNavigateToDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                onEnterDemo = onEnterDemo,
                onExitDemo = onExitDemo,
                onCycleDemo = onCycleDemo,
                onRescan = onRescan,
                onRequestPermissions = onRequestPermissions,
                onApprove = onApprove,
                onDeny = onDeny,
                onSkip = onSkip,
                onFocus = onFocus
            )
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(
                state = state,
                isHyperOS = isHyperOS,
                onBack = { navController.popBackStack() },
                onOpenAppSettings = onOpenAppSettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenAutostartSettings = onOpenAutostartSettings,
                onRequestPermissions = onRequestPermissions,
                onRescan = onRescan
            )
        }
    }
}
