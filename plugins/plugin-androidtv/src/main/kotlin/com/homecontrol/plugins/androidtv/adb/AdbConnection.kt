package com.homecontrol.plugins.androidtv.adb

import android.util.Base64
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

private const val TAG = "AdbConnection"
internal const val ADB_PORT = 5555
private const val CONNECT_TIMEOUT_MS = 5_000
private const val RECONNECT_ATTEMPTS = 3
private const val RECONNECT_DELAY_MS = 750L

// Long enough to actually find the TV's own remote and navigate a multi-step
// dialog (tick "Always allow", then move to and select OK) — both 25s and
// 60s proved too tight in real use once you account for finding the remote.
private const val APPROVAL_TIMEOUT_MS = 120_000
private const val A_VERSION = 0x01000000
private const val MAX_PAYLOAD = 1 * 1024 * 1024
private const val AUTH_TOKEN = 1
private const val AUTH_SIGNATURE = 2
private const val AUTH_RSAPUBLICKEY = 3

private fun cmd(a: Char, b: Char, c: Char, d: Char): Int =
    (a.code and 0xFF) or ((b.code and 0xFF) shl 8) or ((c.code and 0xFF) shl 16) or ((d.code and 0xFF) shl 24)

private val A_CNXN = cmd('C', 'N', 'X', 'N')
private val A_AUTH = cmd('A', 'U', 'T', 'H')
private val A_OPEN = cmd('O', 'P', 'E', 'N')
private val A_OKAY = cmd('O', 'K', 'A', 'Y')
private val A_CLSE = cmd('C', 'L', 'S', 'E')
private val A_WRTE = cmd('W', 'R', 'T', 'E')

private data class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

/** Distinct from a raw connect-level [java.net.SocketTimeoutException] so callers can tell "couldn't reach the device at all" apart from "reached it, but nobody tapped Allow in time". */
class AdbApprovalTimeoutException(message: String) : Exception(message)

/**
 * One ADB-over-TCP session (port 5555) against a single device — the
 * classic "USB/network debugging" protocol: RSA key sign-and-approve, not
 * the newer wireless-debugging SPAKE2 pairing. Blocking I/O throughout by
 * design; callers are expected to run this on [kotlinx.coroutines.Dispatchers.IO].
 */
internal class AdbConnection private constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: DataOutputStream,
) {
    private var nextLocalId = 1

    // This connection is a single multiplexed stream over one socket — two
    // shell() calls in flight at once (e.g. two quick button taps) would
    // otherwise interleave their reads, so one call can read the *other*
    // call's reply and misinterpret it as its own rejection, even though
    // the original command still reaches the device moments later. Only one
    // command at a time is allowed through per connection to prevent that.
    private val commandLock = Any()

    /**
     * Runs a shell command over this session and returns its combined
     * stdout/stderr text. Retries opening the stream a couple of times if
     * the device closes it instead of accepting it — the same "briefly not
     * ready right after a fresh connection" behavior observed on at least
     * one real TV for the CNXN handshake itself; a moment later the exact
     * same command opens fine.
     */
    fun shell(command: String): String = synchronized(commandLock) {
        var lastRejection: IllegalStateException? = null
        repeat(RECONNECT_ATTEMPTS) { attempt ->
            if (attempt > 0) Thread.sleep(RECONNECT_DELAY_MS)
            try {
                return@synchronized shellOnce(command)
            } catch (rejected: IllegalStateException) {
                lastRejection = rejected
            }
        }
        throw lastRejection!!
    }

    private fun shellOnce(command: String): String {
        val localId = nextLocalId++
        writeMessage(A_OPEN, localId, 0, "shell:$command\u0000".toByteArray())

        val opened = readMessage()
        check(opened.command == A_OKAY) { "Device rejected shell command: $command" }
        val remoteId = opened.arg0

        val result = StringBuilder()
        while (true) {
            val message = readMessage()
            when (message.command) {
                A_WRTE -> {
                    result.append(String(message.data, Charsets.UTF_8))
                    writeMessage(A_OKAY, localId, remoteId)
                }
                A_CLSE -> {
                    writeMessage(A_CLSE, localId, remoteId)
                    return result.toString()
                }
                else -> return result.toString()
            }
        }
    }

    fun close() {
        runCatching { socket.close() }
    }

    /**
     * Cheap round-trip probe for whether this session is still usable. A
     * locked/backgrounded phone (or the TV itself, e.g. Fire TV's more
     * aggressive standby behavior) can silently sever the underlying socket
     * without either side seeing a close — [Socket.isConnected] only
     * reflects whether `connect()` was ever called, not the current state,
     * so an actual command is the only reliable way to tell.
     *
     * Retries once after a short delay before giving up: on real hardware, a
     * connection used seconds earlier for a fresh pairing approval can fail
     * its very first follow-up command while the device's own ADB daemon is
     * still settling right after the user tapped Allow — a single transient
     * failure there isn't proof the socket is actually dead, and treating it
     * as dead was forcing a full reconnect (and, if the device hadn't
     * finished registering the just-approved key yet, a second on-screen
     * approval prompt) immediately after the first one succeeded.
     */
    fun isAlive(): Boolean {
        repeat(2) { attempt ->
            try {
                shell("echo")
                return true
            } catch (error: Exception) {
                if (attempt == 0) Thread.sleep(500)
            }
        }
        return false
    }

    private fun writeMessage(command: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(data.size)
        header.putInt(checksum(data))
        header.putInt(command.inv())
        output.write(header.array())
        if (data.isNotEmpty()) output.write(data)
        output.flush()
    }

    private fun readMessage(): AdbMessage {
        val header = ByteArray(24)
        input.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val length = buffer.int
        buffer.int // data checksum — not verified on read, only produced on write
        buffer.int // magic (command inverted) — redundant with `command`, ignored
        val data = ByteArray(length)
        if (length > 0) input.readFully(data)
        return AdbMessage(command, arg0, arg1, data)
    }

    companion object {

        private fun checksum(data: ByteArray): Int {
            var sum = 0
            for (byte in data) sum += byte.toInt() and 0xFF
            return sum
        }

        private fun signToken(token: ByteArray, privateKey: RSAPrivateKey): ByteArray =
            Signature.getInstance("NONEwithRSA").apply {
                initSign(privateKey)
                update(token)
            }.sign()

        private fun encodePublicKeyForWire(publicKey: RSAPublicKey): ByteArray {
            val base64Key = Base64.encodeToString(AdbPublicKeyEncoder.encode(publicKey), Base64.NO_WRAP)
            return "$base64Key homecontrol@android\u0000".toByteArray()
        }

        /**
         * Opens a new ADB session, handling the CNXN/AUTH handshake. If the
         * device doesn't already trust [keyPair]'s public key, this sends it
         * and blocks — up to [APPROVAL_TIMEOUT_MS] — waiting for the user to
         * tap Allow on the device's own screen.
         *
         * Retries a couple of times on [EOFException] specifically — observed
         * in practice on at least one real TV, where a fresh connection to an
         * *already-trusted* key sometimes gets its stream closed mid-handshake
         * for no discernible protocol reason, but a follow-up attempt moments
         * later succeeds outright. Any other failure (refused, timed out,
         * approval denied) is a real result and is not retried.
         */
        fun open(ipAddress: String, keyPair: KeyPair): AdbConnection {
            var lastError: EOFException? = null
            repeat(RECONNECT_ATTEMPTS) { attempt ->
                if (attempt > 0) Thread.sleep(RECONNECT_DELAY_MS)
                try {
                    return openOnce(ipAddress, keyPair)
                } catch (eof: EOFException) {
                    lastError = eof
                }
            }
            throw lastError!!
        }

        private fun openOnce(ipAddress: String, keyPair: KeyPair): AdbConnection {
            Log.d(TAG, "openOnce: connecting to $ipAddress:$ADB_PORT")
            val socket = Socket()
            socket.connect(InetSocketAddress(ipAddress, ADB_PORT), CONNECT_TIMEOUT_MS)
            socket.soTimeout = CONNECT_TIMEOUT_MS
            Log.d(TAG, "openOnce: TCP connected, sending CNXN")

            val connection = AdbConnection(socket, DataInputStream(socket.getInputStream()), DataOutputStream(socket.getOutputStream()))
            try {
                connection.writeMessage(A_CNXN, A_VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray())
                var response = connection.readMessage()
                Log.d(TAG, "openOnce: first response 0x" + response.command.toString(16))

                if (response.command == A_AUTH && response.arg0 == AUTH_TOKEN) {
                    val signature = signToken(response.data, keyPair.private as RSAPrivateKey)
                    connection.writeMessage(A_AUTH, AUTH_SIGNATURE, 0, signature)
                    response = connection.readMessage()
                    Log.d(TAG, "openOnce: after signature response 0x" + response.command.toString(16))
                }

                if (response.command == A_AUTH && response.arg0 == AUTH_TOKEN) {
                    // The device doesn't recognize this key yet — send it and
                    // wait for the on-screen approval prompt to be answered.
                    Log.d(TAG, "openOnce: device wants public key, awaiting on-screen approval")
                    connection.writeMessage(A_AUTH, AUTH_RSAPUBLICKEY, 0, encodePublicKeyForWire(keyPair.public as RSAPublicKey))
                    socket.soTimeout = APPROVAL_TIMEOUT_MS
                    try {
                        do {
                            response = connection.readMessage()
                        } while (response.command == A_AUTH)
                    } catch (timeout: SocketTimeoutException) {
                        Log.d(TAG, "openOnce: approval wait timed out")
                        throw AdbApprovalTimeoutException("Timed out waiting for the on-screen approval prompt")
                    }
                    socket.soTimeout = CONNECT_TIMEOUT_MS
                    Log.d(TAG, "openOnce: approval resolved, response 0x" + response.command.toString(16))
                }

                check(response.command == A_CNXN) { "ADB handshake failed (unexpected response 0x${response.command.toString(16)})" }
                Log.d(TAG, "openOnce: handshake complete")
                return connection
            } catch (error: Exception) {
                Log.d(TAG, "openOnce: failed with " + error::class.simpleName + ": " + error.message)
                connection.close()
                throw error
            }
        }
    }
}
