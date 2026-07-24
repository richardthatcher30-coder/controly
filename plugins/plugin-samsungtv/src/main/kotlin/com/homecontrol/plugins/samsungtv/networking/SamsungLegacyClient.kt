package com.homecontrol.plugins.samsungtv.networking

import android.util.Base64
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

private const val LEGACY_PORT = 55000
private const val CONNECT_TIMEOUT_MS = 5_000
// The TV shows an on-screen Allow/Deny prompt and blocks its reply until
// answered — matches the same real-world timing lesson learned from
// Android TV's ADB approval dialog (25s/60s both proved too tight).
private const val APPROVAL_TIMEOUT_MS = 120_000
private const val APP_NAME = "HomeControl"

internal sealed interface SamsungLegacyOutcome {
    data object Connected : SamsungLegacyOutcome
    data object Denied : SamsungLegacyOutcome
    data object TimedOut : SamsungLegacyOutcome
    data class Failed(val reason: String) : SamsungLegacyOutcome
}

/**
 * Samsung's older (pre-2016) plain-TCP remote-control protocol on port
 * 55000 — this, not the modern WebSocket `samsung.remote.control` API, is
 * what a real 2015 UE60JU6000 actually speaks (confirmed by direct port
 * testing against the device: 8001/8002 both closed, 55000 open).
 * Reverse-engineered originally by the SamyGO project; this wire format
 * follows the well-established, widely-used `samsungctl` Python library
 * (github.com/Ape/samsungctl) rather than being re-derived from scratch.
 *
 * There's no token or certificate here: whatever the TV uses to remember a
 * previously-approved connection, it's keyed off the plain-text fields sent
 * in the handshake ([APP_NAME] and a per-install client id), not anything
 * this client stores itself — reconnecting just re-sends the same values.
 */
internal class SamsungLegacyClient(private val ipAddress: String, private val clientId: String) {

    private var socket: Socket? = null

    /** Connects and completes the handshake, waiting for the user to answer the TV's on-screen prompt if it shows one. */
    fun connect(): SamsungLegacyOutcome {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ipAddress, LEGACY_PORT), CONNECT_TIMEOUT_MS)
            socket.soTimeout = APPROVAL_TIMEOUT_MS
            this.socket = socket

            DataOutputStream(socket.getOutputStream()).apply {
                write(handshakePacket())
                flush()
            }

            when (val response = readResponse(socket)) {
                LegacyResponse.Granted -> SamsungLegacyOutcome.Connected
                LegacyResponse.Denied -> SamsungLegacyOutcome.Denied
                is LegacyResponse.Failed -> SamsungLegacyOutcome.Failed(response.reason)
            }
        } catch (timeout: SocketTimeoutException) {
            close()
            SamsungLegacyOutcome.TimedOut
        } catch (error: Exception) {
            close()
            SamsungLegacyOutcome.Failed(error.message ?: "Connection failed")
        }
    }

    /** Throws if the underlying socket has died (e.g. a locked/backgrounded phone silently killed it) — callers must not swallow this, it's what lets a dead connection get evicted and rebuilt instead of failing forever. */
    fun sendKey(keyCode: String) {
        val socket = socket ?: error("Not connected")
        DataOutputStream(socket.getOutputStream()).apply {
            write(controlPacket(keyCode))
            flush()
        }
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

    private fun serialize(value: String, raw: Boolean = false): ByteArray = serialize(value.toByteArray(Charsets.UTF_8), raw)

    private fun serialize(value: ByteArray, raw: Boolean): ByteArray {
        val bytes = if (raw) value else Base64.encode(value, Base64.NO_WRAP)
        return byteArrayOf(bytes.size.toByte(), 0x00) + bytes
    }

    private sealed interface LegacyResponse {
        data object Granted : LegacyResponse
        data object Denied : LegacyResponse
        data class Failed(val reason: String) : LegacyResponse
    }

    private fun readResponse(socket: Socket): LegacyResponse {
        val input = DataInputStream(socket.getInputStream())

        // 3-byte header: [type byte][2-byte LE length] naming the TV itself, then a second [2-byte LE length]-prefixed response payload.
        val header = ByteArray(3)
        input.readFully(header)
        val nameLength = littleEndianShort(header[1], header[2])
        input.skipBytes(nameLength)

        val lengthBytes = ByteArray(2)
        input.readFully(lengthBytes)
        val responseLength = littleEndianShort(lengthBytes[0], lengthBytes[1])
        val response = ByteArray(responseLength)
        input.readFully(response)

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
