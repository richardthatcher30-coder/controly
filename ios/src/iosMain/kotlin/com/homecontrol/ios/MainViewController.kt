package com.homecontrol.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Phase 1 scaffold placeholder — proves the Kotlin/Native framework links
 * and a Compose Multiplatform screen renders in the iOS Simulator/Xcode
 * project before any real feature module is wired in. `iosApp/ContentView.swift`
 * hosts this via `UIViewControllerRepresentable`, the same way `MainActivity.kt`
 * hosts `HomeControlNavHost()` on Android.
 *
 * Replace the body with `HomeControlTheme(darkTheme = true) { HomeControlNavHost() }`
 * once `core:designsystem` and the `features:*`/`plugin-registry` modules are
 * themselves ported to KMP (later phases) — this module intentionally has no
 * dependency on them yet.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Controly", style = MaterialTheme.typography.headlineMedium)
            Text(text = "iOS scaffold — Phase 1")
        }
    }
}
