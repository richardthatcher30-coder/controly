package com.homecontrol.core.netio

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

actual class TcpSocket actual constructor() {

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    actual var readTimeoutMs: Int = 0
        set(value) {
            field = value
            socket?.soTimeout = value
        }

    actual fun connect(host: String, port: Int, connectTimeoutMs: Int) {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket.soTimeout = readTimeoutMs
        this.socket = socket
        input = DataInputStream(socket.getInputStream())
        output = DataOutputStream(socket.getOutputStream())
    }

    actual fun write(bytes: ByteArray) {
        val output = checkNotNull(output) { "TcpSocket.write() called before connect()" }
        output.write(bytes)
        output.flush()
    }

    actual fun readFully(length: Int): ByteArray {
        val input = checkNotNull(input) { "TcpSocket.readFully() called before connect()" }
        val buffer = ByteArray(length)
        try {
            input.readFully(buffer)
        } catch (timeout: SocketTimeoutException) {
            throw TcpSocketTimeoutException(timeout.message ?: "Read timed out")
        }
        return buffer
    }

    actual fun skip(byteCount: Int) {
        val input = checkNotNull(input) { "TcpSocket.skip() called before connect()" }
        input.skipBytes(byteCount)
    }

    actual fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }
}
