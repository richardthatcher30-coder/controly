package com.homecontrol.feature.cameras

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json

/** Plain local JSON storage for the camera grid layout — mirrors [CameraStore]'s quick-experiment approach. */
class CameraGridStore(context: Context) {

    private val file = File(context.filesDir, "camera_grid.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun load(): CameraGridConfig {
        if (!file.exists()) return CameraGridConfig()
        return runCatching { json.decodeFromString<CameraGridConfig>(file.readText()) }.getOrDefault(CameraGridConfig())
    }

    @Synchronized
    fun save(config: CameraGridConfig) {
        file.writeText(json.encodeToString(config))
    }
}
