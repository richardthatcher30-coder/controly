package com.homecontrol.app.settings

import androidx.compose.runtime.Composable

@Composable
fun PrivacyOptionsScreen(onBack: () -> Unit) {
    LegalTextScreen(title = "Privacy options", onBack = onBack) {
        Body("Controly has no analytics, advertising, or tracking of any kind, so there's nothing here to opt in or out of.")

        Section("What's on your device")
        Body("Paired TVs and PCs, and configured cameras (including any saved credentials), are stored locally in this app's private storage.")

        Section("Clearing it")
        Body("You can remove individual devices with the swipe-to-delete action on the dashboard. To clear everything at once, open Android Settings → Apps → Controly → Storage → Clear storage.")

        Spacer24()
    }
}
