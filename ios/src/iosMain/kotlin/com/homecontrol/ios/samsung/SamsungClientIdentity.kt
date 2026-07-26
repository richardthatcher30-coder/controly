package com.homecontrol.ios.samsung

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSUUID
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite

private const val FILE_NAME = "samsung_client_id.txt"

/**
 * A stable per-install identifier sent as the legacy Samsung protocol's `id`
 * field — port of the Android plugin's `SamsungClientIdentity.kt`. Same flat
 * POSIX-file approach as [com.homecontrol.ios.storage.PairedDeviceStore]
 * rather than Room (unavailable on iOS) or Keychain (overkill for a value
 * that isn't secret, just needs to be stable).
 */
@OptIn(ExperimentalForeignApi::class)
class SamsungClientIdentity {

    fun getOrCreate(): String {
        readFile()?.takeIf { it.isNotBlank() }?.let { return it }
        val id = NSUUID().UUIDString
        writeFile(id)
        return id
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
