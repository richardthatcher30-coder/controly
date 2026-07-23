package com.homecontrol.feature.cameras

import com.homecontrol.feature.cameras.onvif.OnvifClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolves a [CameraConfig] to a playable RTSP URL via ONVIF — shared by the single-camera view and grid tiles. */
internal suspend fun resolveCameraStreamUri(camera: CameraConfig): Result<String> = withContext(Dispatchers.IO) {
    OnvifClient(camera.ipAddress, camera.onvifPort, camera.username, camera.password).resolveStreamUri()
}
