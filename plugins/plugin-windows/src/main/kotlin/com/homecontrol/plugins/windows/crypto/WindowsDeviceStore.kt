package com.homecontrol.plugins.windows.crypto

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What we remember per paired PC once pairing succeeds — the pinned
 * certificate fingerprint (so future connections can detect an
 * impersonation attempt) and the PC's ECDH public key (so we can
 * recompute the shared secret for the reconnect challenge-response without
 * needing a live re-exchange). Neither value is secret; plain JSON is fine.
 */
@Serializable
data class WindowsDeviceRecord(
    val ipAddress: String,
    val serverPublicKeyBase64: String,
    val certificateFingerprint: String,
)

@Singleton
class WindowsDeviceStore @Inject constructor(@ApplicationContext context: Context) {

    private val file = File(context.filesDir, "windows_devices.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun find(ipAddress: String): WindowsDeviceRecord? = loadAll().firstOrNull { it.ipAddress == ipAddress }

    @Synchronized
    fun save(record: WindowsDeviceRecord) {
        val records = loadAll().filterNot { it.ipAddress == record.ipAddress } + record
        file.writeText(json.encodeToString(records))
    }

    @Synchronized
    fun forget(ipAddress: String) {
        file.writeText(json.encodeToString(loadAll().filterNot { it.ipAddress == ipAddress }))
    }

    private fun loadAll(): List<WindowsDeviceRecord> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<WindowsDeviceRecord>>(file.readText()) }.getOrDefault(emptyList())
    }
}
