package com.homecontrol.plugins.sonytv

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What we remember per paired Sony TV. Two mutually exclusive auth modes:
 * - [clientId]: the `clientid` used during `actRegister`. Sony's trust model
 *   is keyed by that id (plus nickname), not a session cookie, so
 *   reconnecting is a fresh `actRegister` call with the same id — in theory
 *   the TV already trusts it and responds immediately with no new PIN, but
 *   some Bravia firmware doesn't persist that trust reliably and re-prompts
 *   on every reconnect regardless.
 * - [psk]: a pre-shared key set directly in the TV's own IP Control settings
 *   (Settings → Network → Home Network → IP Control Authentication). Sent as
 *   an `X-Auth-PSK` header on every request instead of using `actRegister` at
 *   all, so there's no cookie/trust-list to expire or forget — the more
 *   reliable option when the TV keeps asking to re-approve. Nothing here is
 *   secret to the rest of the app; plain JSON is fine.
 */
@Serializable
data class SonyDeviceRecord(
    val ipAddress: String,
    val clientId: String? = null,
    val psk: String? = null,
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
