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
    /** Router-facing address for viewing this camera when the phone isn't on its LAN — set manually, or auto-filled by a successful UPnP port-forward attempt. Null means remote viewing isn't configured. */
    val remoteHost: String? = null,
    val remoteRtspPort: Int? = null,
    /** RTSP path (e.g. "/onvif1") and the camera's actual RTSP port, both learned from the last successful *local* ONVIF resolution — reused as-is for remote viewing, since remote fallback doesn't redo ONVIF negotiation (see CameraStreamResolver), and the port is what a UPnP port-forward request needs to map to internally. Null until the camera has been viewed locally at least once. */
    val lastKnownRtspPath: String? = null,
    val lastKnownRtspPort: Int? = null,
)
