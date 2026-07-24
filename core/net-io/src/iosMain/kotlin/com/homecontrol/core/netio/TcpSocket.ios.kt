@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.homecontrol.core.netio

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.EAGAIN
import platform.posix.EINPROGRESS
import platform.posix.EWOULDBLOCK
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.O_NONBLOCK
import platform.posix.POLLOUT
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_ERROR
import platform.posix.SO_RCVTIMEO
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.getsockopt
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.timeval

/**
 * Hand-rolled on `platform.posix` — the same primitives `java.net.Socket`
 * wraps on the JVM side (`socket()`/`connect()`/`send()`/`recv()`), ships
 * with Kotlin/Native for Apple targets with no custom cinterop `.def` file
 * needed. `connect()` is done via a non-blocking socket + `poll()` so a
 * caller-supplied timeout can apply to the connect phase itself, matching
 * `java.net.Socket.connect(address, timeoutMs)`'s behavior; the socket is
 * restored to blocking mode afterward since every other call here is
 * meant to block (same "blocking by design" convention as the JVM actual).
 *
 * Neither `htons()` nor `inet_pton()` are exposed in Kotlin/Native's
 * `platform.posix` bindings for iOS targets — confirmed by searching the
 * downloaded klib's own metadata for both symbol names and finding no
 * match at all (they're implemented as static-inline/macro in Apple's
 * headers, which this cinterop binding doesn't capture). Both are hand-
 * rolled below instead of assumed available: [hostToNetworkShort] is a
 * plain big-endian byte swap; [parseIPv4Address] hand-parses a dotted-quad
 * string, which is all every caller in this app ever passes (already-
 * resolved LAN IPs, never hostnames needing real DNS resolution).
 *
 * UNVERIFIED AGAINST REAL HARDWARE: this compiles (checked via
 * `compileKotlinIosSimulatorArm64` on this machine, which lacks Xcode/macOS
 * entirely), but has not been linked, run, or tested against a real TCP
 * peer — that requires the Phase 0 de-risking spike described in the
 * migration plan, on a Mac or via a CodeMagic-built ad-hoc iOS build.
 */
actual class TcpSocket actual constructor() {

    private var fd: Int = -1

    actual var readTimeoutMs: Int = 0
        set(value) {
            field = value
            applyReadTimeout()
        }

    actual fun connect(host: String, port: Int, connectTimeoutMs: Int) {
        val socketFd = socket(AF_INET, SOCK_STREAM, 0)
        check(socketFd >= 0) { "socket() failed, errno=$errno" }
        fd = socketFd

        val originalFlags = fcntl(fd, F_GETFL, 0)
        fcntl(fd, F_SETFL, originalFlags or O_NONBLOCK)

        memScoped {
            val address = alloc<sockaddr_in>()
            address.sin_family = AF_INET.convert()
            address.sin_port = hostToNetworkShort(port.toUShort())
            address.sin_addr.s_addr = parseIPv4Address(host)

            val result = connect(fd, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
            if (result != 0) {
                if (errno != EINPROGRESS) {
                    close()
                    error("connect() failed immediately, errno=$errno")
                }

                val pollDescriptor = alloc<pollfd>()
                pollDescriptor.fd = fd
                pollDescriptor.events = POLLOUT.toShort()
                val pollResult = poll(pollDescriptor.ptr, 1u, connectTimeoutMs)
                if (pollResult <= 0) {
                    close()
                    throw TcpSocketTimeoutException("connect() to $host:$port timed out")
                }

                val socketError = alloc<IntVar>()
                val socketErrorLength = alloc<socklen_tVar>()
                socketErrorLength.value = sizeOf<IntVar>().convert()
                getsockopt(fd, SOL_SOCKET, SO_ERROR, socketError.ptr, socketErrorLength.ptr)
                if (socketError.value != 0) {
                    close()
                    error("connect() failed, socket error=${socketError.value}")
                }
            }
        }

        fcntl(fd, F_SETFL, originalFlags)
        applyReadTimeout()
    }

    actual fun write(bytes: ByteArray) {
        check(fd >= 0) { "TcpSocket.write() called before connect()" }
        if (bytes.isEmpty()) return
        bytes.usePinned { pinned ->
            var offset = 0
            while (offset < bytes.size) {
                val sent = send(fd, pinned.addressOf(offset), (bytes.size - offset).convert(), 0)
                check(sent > 0) { "send() failed, errno=$errno" }
                offset += sent.toInt()
            }
        }
    }

    actual fun readFully(length: Int): ByteArray {
        check(fd >= 0) { "TcpSocket.readFully() called before connect()" }
        val buffer = ByteArray(length)
        if (length == 0) return buffer
        buffer.usePinned { pinned ->
            var offset = 0
            while (offset < length) {
                val received = recv(fd, pinned.addressOf(offset), (length - offset).convert(), 0)
                when {
                    received == 0L -> error("Connection closed before $length bytes were read (got $offset)")
                    received < 0 -> {
                        if (errno == EAGAIN || errno == EWOULDBLOCK) {
                            throw TcpSocketTimeoutException("Read timed out after $offset/$length bytes")
                        }
                        error("recv() failed, errno=$errno")
                    }
                    else -> offset += received.toInt()
                }
            }
        }
        return buffer
    }

    actual fun skip(byteCount: Int) {
        readFully(byteCount)
    }

    actual fun close() {
        if (fd >= 0) {
            close(fd)
            fd = -1
        }
    }

    private fun applyReadTimeout() {
        if (fd < 0) return
        memScoped {
            val timeout = alloc<timeval>()
            timeout.tv_sec = (readTimeoutMs / 1000).convert()
            timeout.tv_usec = ((readTimeoutMs % 1000) * 1000).convert()
            setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
        }
    }
}

/** Network byte order is big-endian; every relevant target here (arm64/x86_64) is little-endian, so this is a plain byte swap. */
internal fun hostToNetworkShort(value: UShort): UShort =
    (((value.toInt() and 0xFF) shl 8) or ((value.toInt() shr 8) and 0xFF)).toUShort()

/**
 * Every caller in this app already has a resolved LAN IPv4 address in hand
 * (never a hostname needing real DNS resolution), so a plain dotted-quad
 * parser is all that's needed. `sin_addr.s_addr` must hold the address in
 * network byte order (first octet at the lowest memory address); since
 * every target here (arm64/x86_64) is little-endian, that means the first
 * dotted-quad octet has to land in this `UInt`'s own least-significant byte
 * position, not its most-significant one — i.e. build it octet[0] | octet[1]
 * << 8 | ..., not the more intuitive-looking octet[0] << 24 | octet[1] << 16 | ...
 * (which is what a naive left-to-right assembly gives you, and is wrong here).
 */
internal fun parseIPv4Address(dottedQuad: String): UInt {
    val octets = dottedQuad.split(".").map { it.toUInt() and 0xFFu }
    require(octets.size == 4) { "Not a dotted-quad IPv4 address: $dottedQuad" }
    return octets[0] or (octets[1] shl 8) or (octets[2] shl 16) or (octets[3] shl 24)
}
