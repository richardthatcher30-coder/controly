package com.homecontrol.plugins.samsungtv

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * A stable per-install identifier sent as the legacy protocol's `id` field
 * (previously always sent empty). Some Samsung legacy firmware appears to
 * key its remembered-device list on this field rather than (or in addition
 * to) the app name — sending the same non-empty id on every connection, as
 * opposed to always sending "", is a plausible fix for the TV re-showing
 * the Allow/Deny prompt on every reconnect, though this isn't confirmed
 * against Samsung's own documentation (there isn't any public spec for
 * this reverse-engineered protocol) — it needs testing against real
 * hardware to know for sure.
 */
class SamsungClientIdentity(context: Context) {

    private val file = File(context.filesDir, "samsung_client_id.txt")

    @Synchronized
    fun getOrCreate(): String {
        if (file.exists()) return file.readText()
        val id = UUID.randomUUID().toString()
        file.writeText(id)
        return id
    }
}
