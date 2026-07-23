package com.homecontrol.feature.cameras

import kotlinx.serialization.Serializable

@Serializable
data class CameraConfig(
    val id: String,
    val name: String,
    val ipAddress: String,
    val onvifPort: Int = 80,
    val username: String,
    val password: String,
)
