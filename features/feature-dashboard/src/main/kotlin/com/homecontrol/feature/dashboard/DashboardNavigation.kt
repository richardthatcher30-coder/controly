package com.homecontrol.feature.dashboard

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.homecontrol.core.model.PairedDevice

const val DASHBOARD_ROUTE = "dashboard"

fun NavGraphBuilder.dashboardScreen(
    onAddDeviceClick: () -> Unit,
    onAddCameraClick: () -> Unit,
    onDeviceClick: (PairedDevice) -> Unit,
    onCamerasClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    composable(DASHBOARD_ROUTE) {
        DashboardScreen(
            onAddDeviceClick = onAddDeviceClick,
            onAddCameraClick = onAddCameraClick,
            onDeviceClick = onDeviceClick,
            onCamerasClick = onCamerasClick,
            onAboutClick = onAboutClick,
        )
    }
}
