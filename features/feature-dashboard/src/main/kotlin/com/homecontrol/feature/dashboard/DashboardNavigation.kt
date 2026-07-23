package com.homecontrol.feature.dashboard

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.homecontrol.core.model.PairedDevice

const val DASHBOARD_ROUTE = "dashboard"

fun NavGraphBuilder.dashboardScreen(
    onAddDeviceClick: () -> Unit,
    onDeviceClick: (PairedDevice) -> Unit,
    onCamerasClick: () -> Unit,
) {
    composable(DASHBOARD_ROUTE) {
        DashboardScreen(onAddDeviceClick = onAddDeviceClick, onDeviceClick = onDeviceClick, onCamerasClick = onCamerasClick)
    }
}
