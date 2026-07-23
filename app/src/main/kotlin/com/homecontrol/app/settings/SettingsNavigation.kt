package com.homecontrol.app.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

const val SETTINGS_ROUTE = "settings"
private const val TERMS_ROUTE = "settings/terms"
private const val PRIVACY_POLICY_ROUTE = "settings/privacy-policy"
private const val PRIVACY_OPTIONS_ROUTE = "settings/privacy-options"
private const val LICENSES_ROUTE = "settings/licenses"

fun NavGraphBuilder.settingsScreens(navController: NavHostController) {
    composable(SETTINGS_ROUTE) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onTermsClick = { navController.navigate(TERMS_ROUTE) },
            onPrivacyPolicyClick = { navController.navigate(PRIVACY_POLICY_ROUTE) },
            onPrivacyOptionsClick = { navController.navigate(PRIVACY_OPTIONS_ROUTE) },
            onLicensesClick = { navController.navigate(LICENSES_ROUTE) },
        )
    }
    composable(TERMS_ROUTE) {
        TermsScreen(onBack = { navController.popBackStack() })
    }
    composable(PRIVACY_POLICY_ROUTE) {
        PrivacyPolicyScreen(onBack = { navController.popBackStack() })
    }
    composable(PRIVACY_OPTIONS_ROUTE) {
        PrivacyOptionsScreen(onBack = { navController.popBackStack() })
    }
    composable(LICENSES_ROUTE) {
        LicensesScreen(onBack = { navController.popBackStack() })
    }
}
