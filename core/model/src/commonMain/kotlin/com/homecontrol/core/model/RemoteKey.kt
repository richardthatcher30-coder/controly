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
    // A single combined play/pause toggle, as found on most streaming-box
    // remotes (Fire TV included) — distinct from the separate PLAY/PAUSE
    // keys above, and mapped to Android's own KEYCODE_MEDIA_PLAY_PAUSE
    // rather than reusing PLAY, which is a different, play-only keycode.
    PLAY_PAUSE,
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
    // Generic "voice search / assistant" button, as found on Fire TV and
    // Android TV remotes. Unconfirmed against real hardware which intent
    // Fire OS actually wires this to — mapped to Android's own KEYCODE_ASSIST
    // as the closest stock equivalent (see AndroidTvPlugin.keyCodeFor).
    VOICE_ASSIST,
}
