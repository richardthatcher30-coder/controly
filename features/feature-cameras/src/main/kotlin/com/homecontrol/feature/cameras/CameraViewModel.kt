package com.homecontrol.feature.cameras

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StreamUiState {
    data object Loading : StreamUiState
    data class Ready(val rtspUrl: String) : StreamUiState
    data class Failed(val message: String) : StreamUiState
}

class CameraViewModel(
    savedStateHandle: SavedStateHandle,
    private val store: CameraStore,
) : ViewModel() {

    private val cameraId: String = checkNotNull(savedStateHandle[CAMERA_ID_ARG])

    val camera: CameraConfig? = store.list().firstOrNull { it.id == cameraId }

    private val _uiState = MutableStateFlow<StreamUiState>(StreamUiState.Loading)
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    init {
        resolveStream()
    }

    fun retry() = resolveStream()

    private fun resolveStream() {
        val camera = camera ?: run {
            _uiState.value = StreamUiState.Failed("Camera not found")
            return
        }
        _uiState.value = StreamUiState.Loading
        viewModelScope.launch {
            val result = resolveCameraStreamUri(camera)
            _uiState.value = result.fold(
                onSuccess = { StreamUiState.Ready(it) },
                onFailure = { StreamUiState.Failed(it.message ?: "Couldn't connect to the camera") },
            )
        }
    }
}
