package com.homecontrol.ios.samsung

import com.homecontrol.core.netio.TcpSocket
import com.homecontrol.core.netio.TcpSocketTimeoutException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val LEGACY_PORT = 55000
private const val CONNECT_TIMEOUT_MS = 5_000
// The TV shows an on-screen Allow/Deny prompt and blocks its reply until
// answered — same real-world timing lesson as the ADB approval dialog.
private const val APPROVAL_TIMEOUT_MS = 120_000
private const val APP_NAME = "Controly"

internal sealed interface SamsungLegacyOutcome {
    data object Connected : SamsungLegacyOutcome
    data object Denied : SamsungLegacyOutcome
    data object TimedOut : SamsungLegacyOutcome
    data class Failed(val reason: String) : SamsungLegacyOutcome
}

/**
 * iOS port of `plugin-samsungtv`'s `SamsungLegacyClient.kt` — same wire
 * format (reverse-engineered by the SamyGO project, following the
 * `samsungctl` Python library), same port 55000, same handshake/control
 * packet layout. Uses [TcpSocket] (the shared blocking-socket abstraction
 * `AdbConnection` also uses) instead of `java.net.Socket`, and
 * `kotlin.io.encoding.Base64` instead of `android.util.Base64` — everything
 * else is an exact behavioral port, not a reinterpretation.
 */
@OptIn(ExperimentalEncodingApi::class)
internal class SamsungLegacyClient(private val ipAddress: String, private val clientId: String) {

    private var socket: TcpSocket? = null

    /** Connects and completes the handshake, waiting for the user to answer the TV's on-screen prompt if it shows one. */
    fun connect(): SamsungLegacyOutcome {
        val socket = TcpSocket()
        return try {
            socket.connect(ipAddress, LEGACY_PORT, CONNECT_TIMEOUT_MS)
            socket.readTimeoutMs = APPROVAL_TIMEOUT_MS
            this.socket = socket

            socket.write(handshakePacket())

            when (val response = readResponse(socket)) {
                LegacyResponse.Granted -> SamsungLegacyOutcome.Connected
                LegacyResponse.Denied -> SamsungLegacyOutcome.Denied
                is LegacyResponse.Failed -> SamsungLegacyOutcome.Failed(response.reason)
            }
        } catch (timeout: TcpSocketTimeoutException) {
            close()
            SamsungLegacyOutcome.TimedOut
        } catch (error: Exception) {
            close()
            SamsungLegacyOutcome.Failed(error.message ?: "Connection failed")
        }
    }

    /** Throws if the underlying socket has died — callers must not swallow this, it's what lets a dead connection get evicted and rebuilt instead of failing forever. */
    fun sendKey(keyCode: String) {
        val socket = socket ?: error("Not connected")
        socket.write(controlPacket(keyCode))
        readResponse(socket)
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun handshakePacket(): ByteArray {
        val payload = byteArrayOf(0x64, 0x00) +
            serialize(APP_NAME) + // description
            serialize(clientId) + // id
            serialize(APP_NAME) // name
        return byteArrayOf(0x00, 0x00, 0x00) + serialize(payload, raw = true)
    }

    private fun controlPacket(keyCode: String): ByteArray {
        val payload = byteArrayOf(0x00, 0x00, 0x00) + serialize(keyCode)
        return byteArrayOf(0x00, 0x00, 0x00) + serialize(payload, raw = true)
    }

    private fun serialize(value: String, raw: Boolean = false): ByteArray = serialize(value.encodeToByteArray(), raw)

    private fun serialize(value: ByteArray, raw: Boolean): ByteArray {
        val bytes = if (raw) value else Base64.encode(value).encodeToByteArray()
        return byteArrayOf(bytes.size.toByte(), 0x00) + bytes
    }

    private sealed interface LegacyResponse {
        data object Granted : LegacyResponse
        data object Denied : LegacyResponse
        data class Failed(val reason: String) : LegacyResponse
    }

    private fun readResponse(socket: TcpSocket): LegacyResponse {
        // 3-byte header: [type byte][2-byte LE length] naming the TV itself, then a second [2-byte LE length]-prefixed response payload.
        val header = socket.readFully(3)
        val nameLength = littleEndianShort(header[1], header[2])
        socket.skip(nameLength)

        val lengthBytes = socket.readFully(2)
        val responseLength = littleEndianShort(lengthBytes[0], lengthBytes[1])
        val response = socket.readFully(responseLength)

        return when {
            response.contentEquals(byteArrayOf(0x64, 0x00, 0x01, 0x00)) -> LegacyResponse.Granted
            response.contentEquals(byteArrayOf(0x64, 0x00, 0x00, 0x00)) -> LegacyResponse.Denied
            response.isNotEmpty() && response[0] == 0x0a.toByte() -> readResponse(socket) // still waiting for the user to answer the TV's popup
            response.isNotEmpty() && response[0] == 0x65.toByte() -> LegacyResponse.Denied // authorization cancelled
            response.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00)) -> LegacyResponse.Granted // control-command ack
            else -> LegacyResponse.Failed("Unexpected response from TV")
        }
    }

    private fun littleEndianShort(low: Byte, high: Byte): Int = (high.toInt() and 0xFF shl 8) or (low.toInt() and 0xFF)
}
