package com.homecontrol.ios.storage

import com.homecontrol.core.model.DeviceCapabilities
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.PairedDevice
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSHomeDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite

private const val FILE_NAME = "paired_devices.txt"
private const val FIELD_DELIMITER = ""
private const val LINE_DELIMITER = "\n"

/**
 * Simple flat-file local store for paired devices. There's no shared
 * `core:database` reachable from iOS yet (Room is Android-only), so this is
 * a fresh, iOS-only persistence layer rather than a port of it. One
 * delimited line per device, stored via plain POSIX file I/O (matching
 * `core:net-io`'s TcpSocket.ios.kt's own preference for POSIX over Foundation
 * APIs where both are viable, for more predictable Kotlin/Native interop).
 * Deliberately not JSON, to avoid hand-rolling a JSON parser for something
 * this small.
 *
 * [DeviceCapabilities] isn't persisted (defaults to [DeviceCapabilities.NONE]
 * on load) — nothing in this iOS release reads it yet; the remote-control
 * screen that will is a later phase.
 */
@OptIn(ExperimentalForeignApi::class)
class PairedDeviceStore {

    fun list(): List<PairedDevice> {
        val content = readFile() ?: return emptyList()
        return content.split(LINE_DELIMITER)
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { decodeDevice(it) }.getOrNull() }
    }

    fun add(device: PairedDevice) {
        val existing = list().filterNot { it.id == device.id }
        writeAll(existing + device)
    }

    fun remove(deviceId: String) {
        writeAll(list().filterNot { it.id == deviceId })
    }

    fun setOnline(deviceId: String, isOnline: Boolean) {
        writeAll(list().map { if (it.id == deviceId) it.copy(isOnline = isOnline) else it })
    }

    private fun writeAll(devices: List<PairedDevice>) {
        writeFile(devices.joinToString(LINE_DELIMITER) { encodeDevice(it) })
    }

    private fun encodeDevice(device: PairedDevice): String = listOf(
        device.id,
        device.name,
        device.manufacturer,
        device.model,
        device.ipAddress,
        device.macAddress.orEmpty(),
        device.deviceType.name,
        device.firmwareVersion.orEmpty(),
        device.isOnline.toString(),
        device.pluginId,
    ).joinToString(FIELD_DELIMITER) { it.replace(LINE_DELIMITER, " ").replace(FIELD_DELIMITER, " ") }

    private fun decodeDevice(line: String): PairedDevice {
        val fields = line.split(FIELD_DELIMITER)
        return PairedDevice(
            id = fields[0],
            name = fields[1],
            manufacturer = fields[2],
            model = fields[3],
            ipAddress = fields[4],
            macAddress = fields[5].ifEmpty { null },
            deviceType = DeviceType.valueOf(fields[6]),
            firmwareVersion = fields[7].ifEmpty { null },
            capabilities = DeviceCapabilities.NONE,
            isOnline = fields[8].toBoolean(),
            pluginId = fields[9],
        )
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
