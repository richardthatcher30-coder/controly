package com.homecontrol.plugins.androidtv.adb

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.interfaces.RSAPublicKey

private const val RSA_KEY_SIZE_BITS = 2048
private const val RSA_NUM_WORDS = RSA_KEY_SIZE_BITS / 32 // 64
private const val RSA_NUM_BYTES = RSA_KEY_SIZE_BITS / 8 // 256
private val WORD_MODULUS: BigInteger = BigInteger.ONE.shiftLeft(32)

/**
 * Encodes an RSA public key into ADB's own "mincrypt" wire format — the
 * device-side auth daemon verifies signatures with a fixed-size Montgomery
 * multiplication routine rather than a general bignum library, so the key
 * has to travel as (word count, -1/n0 mod 2^32, modulus, R^2 mod n,
 * exponent) rather than standard X.509/PEM. See AOSP
 * `system/core/adb/adb_auth_host.cpp` and `system/core/libmincrypt`.
 */
internal object AdbPublicKeyEncoder {

    fun encode(publicKey: RSAPublicKey): ByteArray {
        val n = publicKey.modulus
        val e = publicKey.publicExponent

        val n0inv = WORD_MODULUS.subtract(n.mod(WORD_MODULUS).modInverse(WORD_MODULUS)).mod(WORD_MODULUS)
        val r = BigInteger.ONE.shiftLeft(RSA_NUM_WORDS * 32)
        val rr = r.multiply(r).mod(n)

        val buffer = ByteBuffer
            .allocate(4 + 4 + RSA_NUM_BYTES + RSA_NUM_BYTES + 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(RSA_NUM_WORDS)
        buffer.putInt(n0inv.toInt())
        buffer.put(toLittleEndianFixedWidth(n, RSA_NUM_BYTES))
        buffer.put(toLittleEndianFixedWidth(rr, RSA_NUM_BYTES))
        buffer.putInt(e.toInt())
        return buffer.array()
    }

    private fun toLittleEndianFixedWidth(value: BigInteger, width: Int): ByteArray {
        val bigEndianBytes = value.toByteArray()
        val fixedWidthBigEndian = when {
            bigEndianBytes.size == width -> bigEndianBytes
            bigEndianBytes.size == width + 1 && bigEndianBytes[0] == 0.toByte() ->
                bigEndianBytes.copyOfRange(1, bigEndianBytes.size)
            bigEndianBytes.size < width ->
                ByteArray(width - bigEndianBytes.size) + bigEndianBytes
            else -> bigEndianBytes.copyOfRange(bigEndianBytes.size - width, bigEndianBytes.size)
        }
        return fixedWidthBigEndian.reversedArray()
    }
}
