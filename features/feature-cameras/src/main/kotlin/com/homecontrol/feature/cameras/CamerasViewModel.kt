package com.homecontrol.feature.cameras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.feature.cameras.onvif.DiscoveredCamera
import com.homecontrol.feature.cameras.onvif.OnvifDiscoveryScanner
import com.homecontrol.feature.cameras.upnp.PortForwardOutcome
import com.homecontrol.feature.cameras.upnp.UpnpPortForwarder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RemoteAccessUiState {
    data object InProgress : RemoteAccessUiState
    data class Success(val host: String, val port: Int) : RemoteAccessUiState
    data class Failed(val reason: String) : RemoteAccessUiState
}

class CamerasViewModel(
    private val store: CameraStore,
    private val discoveryScanner: OnvifDiscoveryScanner,
    private val portForwarder: UpnpPortForwarder,
) : ViewModel() {

    private val _cameras = MutableStateFlow<List<CameraConfig>>(emptyList())
    val cameras: StateFlow<List<CameraConfig>> = _cameras.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredCameras = MutableStateFlow<List<DiscoveredCamera>>(emptyList())
    val discoveredCameras: StateFlow<List<DiscoveredCamera>> = _discoveredCameras.asStateFlow()

    private val _remoteAccessState = MutableStateFlow<RemoteAccessUiState?>(null)
    val remoteAccessState: StateFlow<RemoteAccessUiState?> = _remoteAccessState.asStateFlow()

    private var scanJob: Job? = null

    init {
        refresh()
    }

    private fun refresh() {
        _cameras.value = store.list()
    }

    fun startScan() {
        if (_isScanning.value) return

        _discoveredCameras.value = emptyList()
        _isScanning.value = true

        scanJob = viewModelScope.launch {
            discoveryScanner.scan().collect { camera ->
                // The same camera can answer more than one Probe response
                // (once per network interface it has) — keep just one entry
                // per IP rather than showing duplicates.
                _discoveredCameras.update { current ->
                    if (current.any { it.ipAddress == camera.ipAddress }) current else current + camera
                }
            }
            _isScanning.value = false
        }
    }

    fun addCamera(name: String, ipAddress: String, onvifPort: Int, username: String, password: String) {
        store.add(name, ipAddress, onvifPort, username, password)
        refresh()
    }

    fun removeCamera(camera: CameraConfig) {
        store.remove(camera.id)
        refresh()
    }

    /**
     * Asks the router to forward this camera's RTSP port so it's reachable
     * off the LAN — only works while the phone is currently on the same
     * network as the camera/router, and only if the router has UPnP
     * enabled. Requires the camera to have been viewed locally at least
     * once already, since that's how its actual RTSP port gets learned
     * (see [CameraStreamResolver]) — there's nothing to forward otherwise.
     */
    fun attemptUpnpAutoConfigure(camera: CameraConfig) {
        val internalPort = camera.lastKnownRtspPort
        if (internalPort == null) {
            _remoteAccessState.value = RemoteAccessUiState.Failed(
                "View this camera locally first (on the same Wi-Fi) so its stream port is known, then try again.",
            )
            return
        }

        _remoteAccessState.value = RemoteAccessUiState.InProgress
        viewModelScope.launch {
            _remoteAccessState.value = when (val outcome = portForwarder.forwardPort(camera.ipAddress, internalPort, internalPort)) {
                is PortForwardOutcome.Success -> {
                    store.update(camera.copy(remoteHost = outcome.externalHost, remoteRtspPort = outcome.externalPort))
                    refresh()
                    RemoteAccessUiState.Success(outcome.externalHost, outcome.externalPort)
                }
                is PortForwardOutcome.Failed -> RemoteAccessUiState.Failed(outcome.reason)
            }
        }
    }

    fun setManualRemoteAddress(camera: CameraConfig, host: String, port: Int) {
        store.update(camera.copy(remoteHost = host, remoteRtspPort = port))
        refresh()
    }

    fun clearRemoteAddress(camera: CameraConfig) {
        store.update(camera.copy(remoteHost = null, remoteRtspPort = null))
        refresh()
    }

    fun dismissRemoteAccessResult() {
        _remoteAccessState.value = null
    }
}
