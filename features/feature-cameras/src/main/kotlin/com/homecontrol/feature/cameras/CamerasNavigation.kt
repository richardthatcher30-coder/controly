package com.homecontrol.feature.cameras

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val CAMERAS_ROUTE = "cameras"
private const val CAMERA_VIEW_ROUTE = "camera_view"
internal const val CAMERA_ID_ARG = "cameraId"

fun cameraViewRoute(cameraId: String) = "$CAMERA_VIEW_ROUTE/$cameraId"

fun NavGraphBuilder.camerasScreen(
    onBack: () -> Unit,
    onCameraClick: (CameraConfig) -> Unit,
) {
    composable(CAMERAS_ROUTE) {
        CamerasScreen(onBack = onBack, onCameraClick = onCameraClick)
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
