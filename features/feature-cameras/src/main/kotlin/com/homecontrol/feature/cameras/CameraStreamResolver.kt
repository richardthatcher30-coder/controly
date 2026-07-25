package com.homecontrol.feature.cameras

import com.homecontrol.feature.cameras.onvif.OnvifClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a [CameraConfig] to a playable RTSP URL — shared by the single-
 * camera view and grid tiles.
 *
 * Tries the camera's local (LAN) address first via full ONVIF negotiation,
 * same as always. If that fails and a remote address is configured, falls
 * back to it directly as an RTSP URL, reusing the port + path last learned
 * from a successful local resolution (persisted on the camera itself via
 * [CameraStore.update]). Remote fallback deliberately does NOT redo ONVIF
 * negotiation over the remote address — most port-forwarding setups only
 * forward the RTSP port, not the separate ONVIF/SOAP port too — so remote
 * viewing only works once the camera has been viewed locally at least once
 * and its stream details are known.
 */
internal suspend fun resolveCameraStreamUri(camera: CameraConfig, cameraStore: CameraStore): Result<String> =
    withContext(Dispatchers.IO) {
        val localResult = OnvifClient(camera.ipAddress, camera.onvifPort, camera.username, camera.password).resolveStreamUri()

        localResult.onSuccess { uri ->
            val target = parseRtspTarget(uri)
            if (target != null && (target.port != camera.lastKnownRtspPort || target.pathAndQuery != camera.lastKnownRtspPath)) {
                cameraStore.update(camera.copy(lastKnownRtspPort = target.port, lastKnownRtspPath = target.pathAndQuery))
            }
        }
        if (localResult.isSuccess) return@withContext localResult

        val remoteHost = camera.remoteHost?.takeIf { it.isNotBlank() }
        val remotePort = camera.remoteRtspPort
        val path = camera.lastKnownRtspPath
        if (remoteHost == null || remotePort == null || path == null) {
            return@withContext localResult
        }
        Result.success("rtsp://${camera.username}:${camera.password}@$remoteHost:$remotePort$path")
    }

private data class RtspTarget(val port: Int, val pathAndQuery: String)

/** Pulls the port and path+query out of an rtsp://user:pass@host:port/path URL. */
private fun parseRtspTarget(rtspUrl: String): RtspTarget? {
    val schemeEnd = rtspUrl.indexOf("://").takeIf { it != -1 }?.plus(3) ?: return null
    val afterScheme = rtspUrl.substring(schemeEnd)
    val hostStart = afterScheme.indexOf('@').let { if (it == -1) 0 else it + 1 }
    val pathStart = afterScheme.indexOf('/', hostStart)
    val authority = if (pathStart == -1) afterScheme.substring(hostStart) else afterScheme.substring(hostStart, pathStart)
    val port = authority.substringAfter(':', "").toIntOrNull() ?: 554
    val pathAndQuery = if (pathStart == -1) null else afterScheme.substring(pathStart)
    return pathAndQuery?.let { RtspTarget(port, it) }
}
