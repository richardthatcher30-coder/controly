package com.homecontrol.ios.cameras

data class CameraConfig(
    val id: String,
    val name: String,
    val ipAddress: String,
    val onvifPort: Int,
    val username: String,
    val password: String,
)
