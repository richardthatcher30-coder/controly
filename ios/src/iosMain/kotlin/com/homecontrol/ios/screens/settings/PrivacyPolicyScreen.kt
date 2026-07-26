package com.homecontrol.ios.screens.settings

import androidx.compose.runtime.Composable

/**
 * Port of the Android app's `settings/PrivacyPolicyScreen.kt` — same real
 * data flow (no analytics/ads/crash-reporting SDKs, no Controly backend),
 * adapted only where the platform genuinely differs (storage removal via
 * uninstall rather than Android's separate "Clear storage" action).
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalTextScreen(title = "Privacy policy", onBack = onBack) {
        Body("Controly does not collect, store, or transmit any personal data to us or to any third party.")

        Section("What's stored, and where")
        Body("Device details you add (names, IP addresses, saved credentials for TVs, PCs, and cameras) are stored only in this app's private storage on your phone. None of it is uploaded anywhere.")

        Section("How your devices are controlled")
        Body("When you use a remote, control commands travel directly between your phone and your TV, PC, or camera over your local Wi-Fi network. Nothing passes through a Controly server, because there isn't one.")

        Section("Camera credentials")
        Body("Usernames and passwords you enter for IP cameras are stored locally on your device so Controly can reconnect automatically, and are sent only to that camera, never anywhere else.")

        Section("Voice dictation")
        Body("Typing by voice uses your phone's own built-in speech recognition. Audio is handled according to your device's speech-recognition settings — Controly itself never receives or stores audio.")

        Section("Analytics and advertising")
        Body("This app contains no analytics, advertising, or crash-reporting SDKs of any kind.")

        Section("Removing your data")
        Body("Deleting Controly from your device permanently removes everything it has stored.")

        Section("Contact")
        Body("Questions about this policy can be sent via support.controly.co.uk.")

        Spacer24()
    }
}
