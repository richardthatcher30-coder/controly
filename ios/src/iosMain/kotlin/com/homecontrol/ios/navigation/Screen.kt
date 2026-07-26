package com.homecontrol.ios.navigation

import com.homecontrol.core.model.DeviceType

/**
 * Hand-rolled screen enum + back-stack instead of a navigation library.
 * `androidx.navigation:navigation-compose` (used on Android) has uncertain
 * iOS/KMP target support to verify without a Mac to compile against, and
 * this app's flows are simple enough (a handful of linear pushes) that a
 * plain `List<Screen>` back-stack in Compose state is enough — see
 * [ControlyApp].
 */
sealed interface Screen {
    data object Dashboard : Screen
    data object About : Screen
    data object AddDeviceCategory : Screen
    data object DeviceDiscovery : Screen
    data object AddDeviceManually : Screen
    data object AddCamera : Screen
    data class Remote(val deviceId: String, val deviceName: String, val ipAddress: String, val deviceType: DeviceType) : Screen
}
