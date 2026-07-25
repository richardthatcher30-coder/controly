package com.homecontrol.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.homecontrol.ios.navigation.ControlyApp
import platform.UIKit.UIViewController

/**
 * `iosApp/ContentView.swift` hosts this via `UIViewControllerRepresentable`,
 * the same way `MainActivity.kt` hosts `HomeControlNavHost()` on Android.
 *
 * Renders [ControlyApp] — Dashboard/About/Add-Device/Pairing screens built
 * directly against this module's own iOS-native storage/networking/crypto
 * (see the `ios/adb` and `ios/storage` packages), not the Android `feature-*`
 * modules, which remain unreachable from here until they're ported to KMP.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    ControlyApp()
}
