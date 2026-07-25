package com.homecontrol.ios.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSHomeDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite

private const val FILE_NAME = "companion_devices.txt"
private const val FIELD_DELIMITER = "|"
private const val LINE_DELIMITER = "\n"

/**
 * What we remember per paired Companion-protocol device (Windows PC / Android
 * Companion) once pairing succeeds -- the server's ECDH public key (so a
 * reconnect can recompute the shared secret without a live re-exchange) and
 * the pinned TLS certificate fingerprint (so a later connection can detect a
 * changed/impersonating server). Mirrors the Android client's
 * `WindowsDeviceStore.kt`; separate from [PairedDeviceStore] since neither
 * field applies to ADB-paired devices. Same flat-file POSIX I/O approach as
 * [PairedDeviceStore] -- no shared `core:database` reachable from iOS yet.
 */
@OptIn(ExperimentalForeignApi::class)
class CompanionDeviceStore {

    data class Record(val ipAddress: String, val serverPublicKeyBase64: String, val certificateFingerprint: String)

    fun find(ipAddress: String): Record? = list().firstOrNull { it.ipAddress == ipAddress }

    fun save(record: Record) {
        writeAll(list().filterNot { it.ipAddress == record.ipAddress } + record)
    }

    fun forget(ipAddress: String) {
        writeAll(list().filterNot { it.ipAddress == ipAddress })
    }

    private fun list(): List<Record> {
        val content = readFile() ?: return emptyList()
        return content.split(LINE_DELIMITER)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val fields = line.split(FIELD_DELIMITER)
                if (fields.size != 3) null else Record(fields[0], fields[1], fields[2])
            }
    }

    private fun writeAll(records: List<Record>) {
        val content = records.joinToString(LINE_DELIMITER) { record ->
            listOf(record.ipAddress, record.serverPublicKeyBase64, record.certificateFingerprint)
                .joinToString(FIELD_DELIMITER)
        }
        writeFile(content)
    }

    private fun filePath(): String = NSHomeDirectory() + "/Documents/" + FILE_NAME

    private fun readFile(): String? {
        val file = fopen(filePath(), "r") ?: return null
        try {
            val chunks = StringBuilder()
            val chunk = ByteArray(4096)
            while (true) {
                val bytesRead = chunk.usePinned { pinned ->
                    fread(pinned.addressOf(0), 1u, chunk.size.convert(), file)
                }.toInt()
                if (bytesRead <= 0) break
                chunks.append(chunk.decodeToString(0, bytesRead))
            }
            return chunks.toString()
        } finally {
            fclose(file)
        }
    }

    private fun writeFile(content: String) {
        val file = fopen(filePath(), "w") ?: error("Failed to open ${filePath()} for writing")
        try {
            val bytes = content.encodeToByteArray()
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned -> fwrite(pinned.addressOf(0), 1u, bytes.size.convert(), file) }
            }
        } finally {
            fclose(file)
        }
    }
}
