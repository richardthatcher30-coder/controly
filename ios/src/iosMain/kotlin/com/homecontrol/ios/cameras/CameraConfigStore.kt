package com.homecontrol.ios.cameras

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

private const val FILE_NAME = "cameras.txt"
private const val FIELD_DELIMITER = "|"
private const val LINE_DELIMITER = "\n"

/** Port of the Android app's `CameraStore.kt` -- separate from [com.homecontrol.ios.storage.PairedDeviceStore] since cameras aren't [com.homecontrol.core.model.PairedDevice]s (no plugin/deviceType, ONVIF credentials instead). Same flat POSIX-file approach as every other iOS store. */
@OptIn(ExperimentalForeignApi::class)
class CameraConfigStore {

    fun list(): List<CameraConfig> {
        val content = readFile() ?: return emptyList()
        return content.split(LINE_DELIMITER)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val fields = line.split(FIELD_DELIMITER)
                if (fields.size != 6) return@mapNotNull null
                val port = fields[3].toIntOrNull() ?: return@mapNotNull null
                CameraConfig(
                    id = fields[0],
                    name = fields[1],
                    ipAddress = fields[2],
                    onvifPort = port,
                    username = fields[4],
                    password = fields[5],
                )
            }
    }

    fun add(name: String, ipAddress: String, onvifPort: Int, username: String, password: String) {
        val camera = CameraConfig(NSUUID().UUIDString, name, ipAddress, onvifPort, username, password)
        writeAll(list() + camera)
    }

    fun remove(id: String) {
        writeAll(list().filterNot { it.id == id })
    }

    private fun writeAll(cameras: List<CameraConfig>) {
        val content = cameras.joinToString(LINE_DELIMITER) { camera ->
            listOf(camera.id, camera.name, camera.ipAddress, camera.onvifPort.toString(), camera.username, camera.password)
                .joinToString(FIELD_DELIMITER) { it.replace(LINE_DELIMITER, " ").replace(FIELD_DELIMITER, " ") }
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
