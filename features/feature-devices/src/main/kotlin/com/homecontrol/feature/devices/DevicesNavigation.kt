package com.homecontrol.feature.devices

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val DEVICES_ROUTE = "devices"

fun NavGraphBuilder.devicesScreen(onBack: () -> Unit) {
    composable(DEVICES_ROUTE) {
        DevicesScreen(onBack = onBack)
    }
}
