package com.homecontrol.core.model

/**
 * Discrete remote-control keys sent via [com.homecontrol.core.pluginapi.IDevicePlugin.sendKey].
 * Free-form input (typed text, touchpad deltas) is intentionally not modeled
 * here — see `sendText` on the plugin contract, and the touchpad/mouse
 * pointer path added alongside `plugin-windows`.
 */
enum class RemoteKey {
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    DPAD_CENTER,
    BACK,
    HOME,
    MENU,
    PLAY,
    PAUSE,
    STOP,
    FAST_FORWARD,
    REWIND,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    POWER,
    BACKSPACE,
    CHANNEL_UP,
    CHANNEL_DOWN,
    INPUT_SOURCE,
    SMART_HUB,
}
