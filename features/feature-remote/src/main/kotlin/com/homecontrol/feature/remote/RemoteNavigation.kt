package com.homecontrol.feature.remote

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

private const val DEVICE_ID_ARG = "deviceId"
const val REMOTE_ROUTE = "remote/{$DEVICE_ID_ARG}"

fun remoteRoute(deviceId: String) = "remote/$deviceId"

fun NavGraphBuilder.remoteScreen(onBack: () -> Unit, onSettingsClick: () -> Unit) {
    composable(
        route = REMOTE_ROUTE,
        arguments = listOf(navArgument(DEVICE_ID_ARG) { type = NavType.StringType }),
    ) {
        RemoteScreen(onBack = onBack, onSettingsClick = onSettingsClick)
    }
}
