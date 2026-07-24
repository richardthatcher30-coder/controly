package com.homecontrol.plugins.sonytv

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What we remember per paired Sony TV — just the `clientid` used during
 * `actRegister`. Sony's trust model is keyed by that id (plus nickname), not
 * a session cookie, so reconnecting is a fresh `actRegister` call with the
 * same id: the TV already trusts it and responds immediately with a new
 * session, no PIN prompt needed. Nothing here is secret; plain JSON is fine.
 */
@Serializable
data class SonyDeviceRecord(
    val ipAddress: String,
    val clientId: String,
)

class SonyDeviceStore(context: Context) {

    private val file = File(context.filesDir, "sony_devices.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun find(ipAddress: String): SonyDeviceRecord? = loadAll().firstOrNull { it.ipAddress == ipAddress }

    @Synchronized
    fun save(record: SonyDeviceRecord) {
        val records = loadAll().filterNot { it.ipAddress == record.ipAddress } + record
        file.writeText(json.encodeToString(records))
    }

    private fun loadAll(): List<SonyDeviceRecord> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<SonyDeviceRecord>>(file.readText()) }.getOrDefault(emptyList())
    }
}
