package com.homecontrol.feature.cameras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.feature.cameras.onvif.DiscoveredCamera
import com.homecontrol.feature.cameras.onvif.OnvifDiscoveryScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CamerasViewModel(
    private val store: CameraStore,
    private val discoveryScanner: OnvifDiscoveryScanner,
) : ViewModel() {

    private val _cameras = MutableStateFlow<List<CameraConfig>>(emptyList())
    val cameras: StateFlow<List<CameraConfig>> = _cameras.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredCameras = MutableStateFlow<List<DiscoveredCamera>>(emptyList())
    val discoveredCameras: StateFlow<List<DiscoveredCamera>> = _discoveredCameras.asStateFlow()

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
}
