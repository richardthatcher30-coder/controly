package com.homecontrol.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.homecontrol.app.about.AboutScreen
import com.homecontrol.app.settings.SETTINGS_ROUTE
import com.homecontrol.app.settings.settingsScreens
import com.homecontrol.feature.cameras.CAMERAS_ROUTE
import com.homecontrol.feature.cameras.CAMERA_GRID_ROUTE
import com.homecontrol.feature.cameras.camerasScreen
import com.homecontrol.feature.cameras.cameraGridScreen
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

private const val ABOUT_ROUTE = "about"

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
            onAddCameraClick = { navController.navigate(CAMERAS_ROUTE) },
            onDeviceClick = { device -> navController.navigate(remoteRoute(device.id)) },
            onCamerasClick = { navController.navigate(CAMERAS_ROUTE) },
            onAboutClick = { navController.navigate(ABOUT_ROUTE) },
        )
        composable(ABOUT_ROUTE) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onLegalClick = { navController.navigate(SETTINGS_ROUTE) },
            )
        }
        settingsScreens(navController)
        devicesScreen(
            onBack = { navController.popBackStack() },
        )
        remoteScreen(
            onBack = { navController.popBackStack() },
            onSettingsClick = { navController.navigate(SETTINGS_ROUTE) },
        )
        camerasScreen(
            onBack = { navController.popBackStack() },
            onCameraClick = { camera -> navController.navigate(cameraViewRoute(camera.id)) },
            onGridViewClick = { navController.navigate(CAMERA_GRID_ROUTE) },
        )
        cameraViewScreen(
            onBack = { navController.popBackStack() },
        )
        cameraGridScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
