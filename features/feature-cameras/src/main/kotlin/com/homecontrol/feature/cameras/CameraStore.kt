package com.homecontrol.feature.cameras

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Plain local JSON storage for added cameras — this is a quick experiment, not wired into the shared Room database. */
@Singleton
class CameraStore @Inject constructor(@ApplicationContext context: Context) {

    private val file = File(context.filesDir, "cameras.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun list(): List<CameraConfig> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<CameraConfig>>(file.readText()) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(name: String, ipAddress: String, onvifPort: Int, username: String, password: String): CameraConfig {
        val camera = CameraConfig(
            id = UUID.randomUUID().toString(),
            name = name,
            ipAddress = ipAddress,
            onvifPort = onvifPort,
            username = username,
            password = password,
        )
        file.writeText(json.encodeToString(list() + camera))
        return camera
    }

    @Synchronized
    fun remove(id: String) {
        file.writeText(json.encodeToString(list().filterNot { it.id == id }))
    }
}
