package com.homecontrol.feature.cameras

import com.homecontrol.feature.cameras.onvif.OnvifClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a [CameraConfig] to a playable RTSP URL — shared by the single-
 * camera view and grid tiles.
 *
 * Tries the camera's local (LAN) address first via full ONVIF negotiation,
 * unless [networkChecker] is confident this phone isn't even on the
 * camera's network right now (e.g. mobile data, or a different Wi-Fi
 * entirely) and a remote address is already configured — in which case the
 * local attempt is skipped outright rather than waiting out a connection
 * attempt against an address that's obviously unreachable from here. If the
 * local attempt does run and fails, or the network check itself is
 * inconclusive, this still falls back to the remote address the same as
 * before — the network check is a fast-path, not a replacement for the
 * fallback.
 *
 * Remote fallback deliberately does NOT redo ONVIF negotiation over the
 * remote address — most port-forwarding setups only forward the RTSP port,
 * not the separate ONVIF/SOAP port too — so remote viewing only works once
 * the camera has been viewed locally at least once and its stream details
 * are known (reuses the port + path last learned from a successful local
 * resolution, persisted on the camera itself via [CameraStore.update]).
 */
internal suspend fun resolveCameraStreamUri(
    camera: CameraConfig,
    cameraStore: CameraStore,
    networkChecker: LocalNetworkChecker,
): Result<String> =
    withContext(Dispatchers.IO) {
        fun remoteResult(): Result<String>? {
            val remoteHost = camera.remoteHost?.takeIf { it.isNotBlank() } ?: return null
            val remotePort = camera.remoteRtspPort ?: return null
            val path = camera.lastKnownRtspPath ?: return null
            return Result.success("rtsp://${camera.username}:${camera.password}@$remoteHost:$remotePort$path")
        }

        if (!networkChecker.isLikelyOnSameNetwork(camera.ipAddress)) {
            remoteResult()?.let { return@withContext it }
        }

        val localResult = OnvifClient(camera.ipAddress, camera.onvifPort, camera.username, camera.password).resolveStreamUri()

        localResult.onSuccess { uri ->
            val target = parseRtspTarget(uri)
            if (target != null && (target.port != camera.lastKnownRtspPort || target.pathAndQuery != camera.lastKnownRtspPath)) {
                cameraStore.update(camera.copy(lastKnownRtspPort = target.port, lastKnownRtspPath = target.pathAndQuery))
            }
        }
        if (localResult.isSuccess) return@withContext localResult

        remoteResult() ?: localResult
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
