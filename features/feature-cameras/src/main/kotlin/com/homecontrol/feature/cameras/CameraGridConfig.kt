package com.homecontrol.feature.cameras

import kotlinx.serialization.Serializable

/** The available tile counts for the camera grid view. */
val GRID_TILE_COUNTS = listOf(1, 2, 4, 6)

/**
 * [cameraIds] is always sized to the largest supported tile count so that
 * switching between tile counts doesn't lose the assignments for tiles
 * outside the currently visible range — only the first [tileCount] entries
 * are shown at a time.
 */
@Serializable
data class CameraGridConfig(
    val tileCount: Int = 4,
    val cameraIds: List<String?> = List(GRID_TILE_COUNTS.max()) { null },
)
