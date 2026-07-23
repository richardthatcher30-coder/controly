package com.homecontrol.feature.cameras

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class CamerasViewModel @Inject constructor(
    private val store: CameraStore,
) : ViewModel() {

    private val _cameras = MutableStateFlow<List<CameraConfig>>(emptyList())
    val cameras: StateFlow<List<CameraConfig>> = _cameras.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        _cameras.value = store.list()
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
