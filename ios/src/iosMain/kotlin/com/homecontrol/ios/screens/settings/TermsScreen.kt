package com.homecontrol.ios.screens.settings

import androidx.compose.runtime.Composable

/**
 * Port of the Android app's `settings/TermsScreen.kt` — identical text, kept
 * word-for-word in sync so the two platforms' legal copy doesn't drift.
 * Generic placeholder terms for a personal-use remote-control utility, not a
 * substitute for a lawyer-reviewed agreement before any public release.
 */
@Composable
fun TermsScreen(onBack: () -> Unit) {
    LegalTextScreen(title = "Terms and conditions", onBack = onBack) {
        Section("Acceptance of terms")
        Body("By installing or using Controly, you agree to these terms. If you don't agree, please don't use the app.")

        Section("What Controly does")
        Body("Controly lets you discover, pair with, and control TVs, PCs, and IP cameras on your own local network from your phone. It communicates directly with your devices over your Wi-Fi network — it does not go through any Controly server.")

        Section("License to use")
        Body("You're granted a personal, non-exclusive, non-transferable licence to use Controly on devices you own or control, for controlling your own equipment. You may not reverse-engineer, resell, or redistribute the app outside of official app store channels.")

        Section("Your responsibility")
        Body("You're responsible for the devices you pair with Controly and any credentials you enter (e.g. camera usernames/passwords). Controly is provided to help you control equipment you already own and are authorised to access.")

        Section("No warranty")
        Body("Controly is provided \"as is\", without warranty of any kind. Device manufacturers' own protocols and firmware vary, and some features may not work identically on every device.")

        Section("Limitation of liability")
        Body("To the fullest extent permitted by law, Controly's developer is not liable for any indirect, incidental, or consequential damages arising from use of the app.")

        Section("Changes")
        Body("These terms may be updated from time to time as the app changes. Continued use after an update means you accept the revised terms.")

        Section("Contact")
        Body("Questions about these terms can be sent via support.controly.co.uk.")

        Spacer24()
    }
}
