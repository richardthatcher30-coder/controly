package com.homecontrol.core.model

/**
 * An app launch button, as returned by
 * [com.homecontrol.core.pluginapi.IDevicePlugin.getInstalledApps] and shown
 * in the remote screen's Apps grid. For Windows, these are authored on the
 * PC itself (see the companion's "Manage app buttons" window) — the phone
 * only ever displays and launches them.
 */
data class AppInfo(
    val appId: String,
    val displayName: String,
    val iconUri: String? = null,
)
