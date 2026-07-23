package com.homecontrol.plugins.windows.crypto

import java.nio.ByteBuffer

/**
 * Packs several variable-length byte arrays into one blob (and back) for
 * storage. Deliberately duplicated from `plugin-androidtv`'s copy rather
 * than shared — plugin modules don't depend on each other, and this is 20
 * lines, not worth a shared module over.
 */
internal object ByteArrayPacker {

    fun pack(vararg arrays: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(4 + arrays.sumOf { 4 + it.size })
        buffer.putInt(arrays.size)
        arrays.forEach { array ->
            buffer.putInt(array.size)
            buffer.put(array)
        }
        return buffer.array()
    }

    fun unpack(data: ByteArray): List<ByteArray> {
        val buffer = ByteBuffer.wrap(data)
        val count = buffer.int
        return (0 until count).map {
            val size = buffer.int
            ByteArray(size).also { array -> buffer.get(array) }
        }
    }
}
