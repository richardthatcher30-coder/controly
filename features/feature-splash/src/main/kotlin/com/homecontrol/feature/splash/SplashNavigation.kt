package com.homecontrol.feature.splash

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val SPLASH_ROUTE = "splash"

fun NavGraphBuilder.splashScreen(onFinished: () -> Unit) {
    composable(SPLASH_ROUTE) {
        SplashScreen(onFinished = onFinished)
    }
}
