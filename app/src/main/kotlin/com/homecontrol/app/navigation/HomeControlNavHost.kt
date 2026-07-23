package com.homecontrol.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.homecontrol.feature.cameras.CAMERAS_ROUTE
import com.homecontrol.feature.cameras.camerasScreen
import com.homecontrol.feature.cameras.cameraViewRoute
import com.homecontrol.feature.cameras.cameraViewScreen
import com.homecontrol.feature.dashboard.DASHBOARD_ROUTE
import com.homecontrol.feature.dashboard.dashboardScreen
import com.homecontrol.feature.devices.DEVICES_ROUTE
import com.homecontrol.feature.devices.devicesScreen
import com.homecontrol.feature.remote.remoteRoute
import com.homecontrol.feature.remote.remoteScreen
import com.homecontrol.feature.splash.SPLASH_ROUTE
import com.homecontrol.feature.splash.splashScreen

@Composable
fun HomeControlNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = SPLASH_ROUTE,
    ) {
        splashScreen(
            onFinished = {
                navController.navigate(DASHBOARD_ROUTE) {
                    popUpTo(SPLASH_ROUTE) { inclusive = true }
                }
            },
        )
        dashboardScreen(
            onAddDeviceClick = { navController.navigate(DEVICES_ROUTE) },
            onDeviceClick = { device -> navController.navigate(remoteRoute(device.id)) },
            onCamerasClick = { navController.navigate(CAMERAS_ROUTE) },
        )
        devicesScreen(
            onBack = { navController.popBackStack() },
        )
        remoteScreen(
            onBack = { navController.popBackStack() },
        )
        camerasScreen(
            onBack = { navController.popBackStack() },
            onCameraClick = { camera -> navController.navigate(cameraViewRoute(camera.id)) },
        )
        cameraViewScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
