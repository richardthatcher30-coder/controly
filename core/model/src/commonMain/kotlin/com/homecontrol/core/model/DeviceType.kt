package com.homecontrol.core.model

/**
 * The device families supported in this milestone. A new device family is
 * added here only when a matching module under `plugins/` implements it —
 * this enum is not a place to pre-declare future hardware.
 */
enum class DeviceType {
    ANDROID_TV,
    GOOGLE_TV,
    FIRE_TV,
    SAMSUNG_TV,
    SONY_TV,
    WINDOWS_PC,
    ANDROID_COMPANION,
    UNKNOWN,
}
