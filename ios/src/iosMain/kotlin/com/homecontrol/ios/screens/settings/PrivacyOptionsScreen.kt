package com.homecontrol.ios.screens.settings

import androidx.compose.runtime.Composable

/** Port of the Android app's `settings/PrivacyOptionsScreen.kt`, adapted for how removal actually works on this platform (long-press on the dashboard, not swipe; delete-and-reinstall instead of a separate "Clear storage" action — iOS has no equivalent). */
@Composable
fun PrivacyOptionsScreen(onBack: () -> Unit) {
    LegalTextScreen(title = "Privacy options", onBack = onBack) {
        Body("Controly has no analytics, advertising, or tracking of any kind, so there's nothing here to opt in or out of.")

        Section("What's on your device")
        Body("Paired TVs and PCs, and configured cameras (including any saved credentials), are stored locally in this app's private storage.")

        Section("Clearing it")
        Body("You can remove individual devices with the long-press action on the dashboard. To clear everything at once, delete Controly and reinstall it.")

        Spacer24()
    }
}
