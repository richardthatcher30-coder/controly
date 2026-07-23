package com.homecontrol.feature.cameras

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val CAMERAS_ROUTE = "cameras"
const val CAMERA_GRID_ROUTE = "camera_grid"
private const val CAMERA_VIEW_ROUTE = "camera_view"
internal const val CAMERA_ID_ARG = "cameraId"

fun cameraViewRoute(cameraId: String) = "$CAMERA_VIEW_ROUTE/$cameraId"

fun NavGraphBuilder.camerasScreen(
    onBack: () -> Unit,
    onCameraClick: (CameraConfig) -> Unit,
    onGridViewClick: () -> Unit,
) {
    composable(CAMERAS_ROUTE) {
        CamerasScreen(onBack = onBack, onCameraClick = onCameraClick, onGridViewClick = onGridViewClick)
    }
}

fun NavGraphBuilder.cameraViewScreen(onBack: () -> Unit) {
    composable(
        route = "$CAMERA_VIEW_ROUTE/{$CAMERA_ID_ARG}",
        arguments = listOf(navArgument(CAMERA_ID_ARG) { type = NavType.StringType }),
    ) {
        CameraViewScreen(onBack = onBack)
    }
}

fun NavGraphBuilder.cameraGridScreen(onBack: () -> Unit) {
    composable(CAMERA_GRID_ROUTE) {
        CameraGridScreen(onBack = onBack)
    }
}
