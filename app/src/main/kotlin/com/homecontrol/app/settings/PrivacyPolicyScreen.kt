package com.homecontrol.app.settings

import androidx.compose.runtime.Composable

/**
 * Accurate as of this build: no analytics, ads, or crash-reporting SDKs are
 * included (checked against the actual dependency list), and there is no
 * Controly backend at all — everything below reflects the real data flow,
 * not boilerplate.
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
        Body("Uninstalling Controly, or clearing its storage from Android Settings, permanently removes everything it has stored.")

        Section("Contact")
        Body("Questions about this policy can be sent via support.controly.co.uk.")

        Spacer24()
    }
}
