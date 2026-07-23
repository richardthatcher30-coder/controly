package com.homecontrol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.homecontrol.app.navigation.HomeControlNavHost
import com.homecontrol.core.designsystem.theme.HomeControlTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. `installSplashScreen()` covers only the OS-level
 * cold-start window before the first Compose frame; the in-app branded
 * splash (feature-splash) is the first navigation destination and takes
 * over from there.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HomeControlTheme {
                HomeControlNavHost()
            }
        }
    }
}
