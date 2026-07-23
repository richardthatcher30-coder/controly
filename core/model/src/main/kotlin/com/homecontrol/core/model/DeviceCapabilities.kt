package com.homecontrol.core.model

/**
 * What a specific paired device can actually do. `feature-remote` renders its
 * controls from this flag set alone — it never branches on [DeviceType].
 */
data class DeviceCapabilities(
    val supportsPower: Boolean = false,
    val supportsVolume: Boolean = false,
    val supportsKeyboard: Boolean = false,
    val supportsApps: Boolean = false,
    val supportsTouchpad: Boolean = false,
    val supportsMouse: Boolean = false,
    val supportsPTZ: Boolean = false,
    val supportsWakeOnLan: Boolean = false,
    val supportsScreenMirror: Boolean = false,
    val supportsChannels: Boolean = false,
    val supportsInputSource: Boolean = false,
    val supportsQuickActions: Boolean = false,
    /**
     * The device can only step to the next input one press at a time (no
     * way to name/target a specific one) — distinct from [supportsInputSource],
     * which means the plugin can list real inputs and jump straight to one.
     * Renders as a single repeatable "Source" button rather than a picker.
     */
    val supportsSourceCycle: Boolean = false,
    val supportsSmartHub: Boolean = false,
) {
    companion object {
        val NONE = DeviceCapabilities()
    }
}
