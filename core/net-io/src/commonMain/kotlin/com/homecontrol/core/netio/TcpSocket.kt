package com.homecontrol.core.netio

/**
 * A blocking TCP client socket, shared between Android and iOS.
 *
 * Ktor's multiplatform socket API (`io.ktor.network.sockets`) does NOT cover
 * Apple/iOS targets at all (confirmed by checking its supported-platforms
 * list during the KMP migration plan — it targets JVM/linuxX64/mingwX64
 * only), so this had to be its own `expect`/`actual`, not a thin Ktor
 * wrapper. The Android `actual` wraps the existing `java.net.Socket`-based
 * code from `AdbConnection.kt`/`SamsungLegacyClient.kt` essentially
 * unchanged; the iOS `actual` is hand-rolled on `platform.posix` (ships with
 * Kotlin/Native for Apple targets, no custom cinterop `.def` file needed).
 *
 * Deliberately blocking, not suspending — every existing caller of the raw
 * sockets this replaces is already documented as "blocking by design, run on
 * `Dispatchers.IO`" (see `AdbConnection.kt`), so this preserves that exact
 * calling convention rather than introducing a second concurrency model.
 */
expect class TcpSocket() {
    /** Blocks until connected or [connectTimeoutMs] elapses. */
    fun connect(host: String, port: Int, connectTimeoutMs: Int)

    /** Read timeout applied to every subsequent [readFully]/[skip] call — mutable so callers can widen it while waiting for a user to answer an on-screen approval prompt, then narrow it back afterward. */
    var readTimeoutMs: Int

    fun write(bytes: ByteArray)

    /** Blocks until exactly [length] bytes have been read, or throws if the stream ends first. */
    fun readFully(length: Int): ByteArray

    /** Discards exactly [byteCount] bytes from the stream. */
    fun skip(byteCount: Int)

    fun close()
}

/**
 * Thrown when [TcpSocket.readTimeoutMs] elapses before the requested bytes
 * arrive — distinct from other I/O failures so callers can tell "still
 * waiting" apart from "connection actually broke". A plain common class
 * (not `expect`/`actual`) since both platforms can throw the exact same
 * Kotlin type; only the underlying platform timeout signal they translate
 * from differs.
 */
class TcpSocketTimeoutException(message: String) : Exception(message)
