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
import com.homecontrol.ios.screens.devices.AddDeviceScreen
import com.homecontrol.ios.screens.remote.RemoteScreen
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

    ControlyTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val screen = currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    onAddDevice = { push(Screen.AddDevice) },
                    onAbout = { push(Screen.About) },
                    onDeviceClick = { device ->
                        push(Screen.Remote(device.id, device.name, device.ipAddress))
                    },
                )
                Screen.About -> AboutScreen(onBack = { pop() })
                Screen.AddDevice -> AddDeviceScreen(onBack = { pop() }, onPaired = { pop() })
                is Screen.Remote -> RemoteScreen(
                    deviceName = screen.deviceName,
                    ipAddress = screen.ipAddress,
                    onBack = { pop() },
                )
            }
        }
    }
}
