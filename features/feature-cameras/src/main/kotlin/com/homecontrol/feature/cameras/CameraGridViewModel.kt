package com.homecontrol.feature.cameras

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class CameraGridViewModel @Inject constructor(
    private val cameraStore: CameraStore,
    private val gridStore: CameraGridStore,
) : ViewModel() {

    private val _cameras = MutableStateFlow<List<CameraConfig>>(emptyList())
    val cameras: StateFlow<List<CameraConfig>> = _cameras.asStateFlow()

    private val _grid = MutableStateFlow(gridStore.load())
    val grid: StateFlow<CameraGridConfig> = _grid.asStateFlow()

    init {
        _cameras.value = cameraStore.list()
    }

    fun setTileCount(count: Int) {
        _grid.value = _grid.value.copy(tileCount = count).also(gridStore::save)
    }

    /** Pass `cameraId = null` to clear a tile. */
    fun assignCamera(tileIndex: Int, cameraId: String?) {
        val updated = _grid.value.cameraIds.toMutableList().apply { this[tileIndex] = cameraId }
        _grid.value = _grid.value.copy(cameraIds = updated).also(gridStore::save)
    }

    fun cameraFor(id: String?): CameraConfig? = id?.let { cameraId -> _cameras.value.firstOrNull { it.id == cameraId } }
}
