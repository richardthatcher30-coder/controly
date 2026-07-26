package com.homecontrol.ios.sony

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSHomeDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite

private const val FILE_NAME = "sony_devices.txt"
private const val FIELD_DELIMITER = "|"
private const val LINE_DELIMITER = "\n"

/**
 * What we remember per paired Sony TV -- port of the Android plugin's
 * `SonyDeviceStore.kt`, two mutually exclusive auth modes ([clientId] from
 * `actRegister` pairing, or a TV-side pre-shared [psk]) -- see that class's
 * doc comment for why both exist. Same flat POSIX-file approach as
 * [com.homecontrol.ios.storage.CompanionDeviceStore] rather than the
 * Android version's JSON file (no shared `core:database`/serialization-to-disk
 * convention on iOS yet; kept consistent with this platform's other stores).
 */
@OptIn(ExperimentalForeignApi::class)
class SonyDeviceStore {

    data class Record(val ipAddress: String, val clientId: String?, val psk: String?)

    fun find(ipAddress: String): Record? = list().firstOrNull { it.ipAddress == ipAddress }

    fun save(record: Record) {
        writeAll(list().filterNot { it.ipAddress == record.ipAddress } + record)
    }

    private fun list(): List<Record> {
        val content = readFile() ?: return emptyList()
        return content.split(LINE_DELIMITER)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val fields = line.split(FIELD_DELIMITER)
                if (fields.size != 3) null else Record(fields[0], fields[1].ifEmpty { null }, fields[2].ifEmpty { null })
            }
    }

    private fun writeAll(records: List<Record>) {
        val content = records.joinToString(LINE_DELIMITER) { record ->
            listOf(record.ipAddress, record.clientId.orEmpty(), record.psk.orEmpty())
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
