package com.homecontrol.ios.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.homecontrol.ios.screens.about.AboutScreen
import com.homecontrol.ios.screens.dashboard.DashboardScreen
import com.homecontrol.ios.screens.devices.AddCameraScreen
import com.homecontrol.ios.screens.devices.AddDeviceCategoryScreen
import com.homecontrol.ios.screens.devices.AddDeviceManuallyScreen
import com.homecontrol.ios.screens.devices.DeviceDiscoveryScreen
import com.homecontrol.ios.screens.remote.RemoteScreen
import com.homecontrol.ios.screens.settings.LicensesScreen
import com.homecontrol.ios.screens.settings.PrivacyOptionsScreen
import com.homecontrol.ios.screens.settings.PrivacyPolicyScreen
import com.homecontrol.ios.screens.settings.SettingsScreen
import com.homecontrol.ios.screens.settings.TermsScreen
import com.homecontrol.ios.theme.ControlyTheme

/** Composition root — replaces `MainViewController`'s Phase 1 placeholder body. */
@Composable
fun ControlyApp() {
    var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Dashboard)) }
    val currentScreen = backStack.last()

    fun push(screen: Screen) {
        backStack = backStack + screen
    }

    fun pop() {
        if (backStack.size > 1) backStack = backStack.dropLast(1)
    }

    // Pairing can succeed from either DeviceDiscovery or (one level deeper)
    // AddDeviceManually -- either way, land back on the Dashboard rather
    // than leaving stale discovery/category screens on the back stack.
    fun popToDashboard() {
        backStack = listOf(Screen.Dashboard)
    }

    ControlyTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val screen = currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    onAddDevice = { push(Screen.AddDeviceCategory) },
                    onAbout = { push(Screen.About) },
                    onDeviceClick = { device ->
                        push(Screen.Remote(device.id, device.name, device.ipAddress, device.deviceType))
                    },
                )
                Screen.About -> AboutScreen(onBack = { pop() }, onSettings = { push(Screen.Settings) })
                Screen.AddDeviceCategory -> AddDeviceCategoryScreen(
                    onBack = { pop() },
                    onDevicesClick = { push(Screen.DeviceDiscovery) },
                    onCamerasClick = { push(Screen.AddCamera) },
                )
                Screen.DeviceDiscovery -> DeviceDiscoveryScreen(
                    onBack = { pop() },
                    onAddManually = { push(Screen.AddDeviceManually) },
                    onPaired = { popToDashboard() },
                )
                Screen.AddDeviceManually -> AddDeviceManuallyScreen(
                    onBack = { pop() },
                    onPaired = { popToDashboard() },
                )
                Screen.AddCamera -> AddCameraScreen(onBack = { pop() })
                is Screen.Remote -> RemoteScreen(
                    deviceName = screen.deviceName,
                    ipAddress = screen.ipAddress,
                    deviceType = screen.deviceType,
                    onBack = { pop() },
                )
                Screen.Settings -> SettingsScreen(
                    onBack = { pop() },
                    onTermsClick = { push(Screen.Terms) },
                    onPrivacyPolicyClick = { push(Screen.PrivacyPolicy) },
                    onPrivacyOptionsClick = { push(Screen.PrivacyOptions) },
                    onLicensesClick = { push(Screen.Licenses) },
                )
                Screen.Terms -> TermsScreen(onBack = { pop() })
                Screen.PrivacyPolicy -> PrivacyPolicyScreen(onBack = { pop() })
                Screen.PrivacyOptions -> PrivacyOptionsScreen(onBack = { pop() })
                Screen.Licenses -> LicensesScreen(onBack = { pop() })
            }
        }
    }
}
