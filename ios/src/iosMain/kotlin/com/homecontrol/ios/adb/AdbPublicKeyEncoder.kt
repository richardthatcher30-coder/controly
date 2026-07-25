package com.homecontrol.ios.adb

import com.homecontrol.core.crypto.extractModulusAndExponent

private const val RSA_NUM_WORDS = 64 // 2048-bit key / 32 bits per word
private const val RSA_NUM_BYTES = 256

/**
 * Re-implementation of `plugin-androidtv`'s Android-only `AdbPublicKeyEncoder`
 * for iOS (ADB's "mincrypt" binary public-key format, matching AOSP
 * `system/core/adb/adb_auth_host.cpp` / libmincrypt) — that class lives in
 * an Android-only Gradle module unreachable from `:ios`, so this is a fresh
 * but wire-compatible implementation against the same target format.
 *
 * Given a PKCS1 `RSAPrivateKey` DER blob, encodes the fixed 524-byte struct:
 * `int32 numWords; int32 n0inv; byte[256] modulus; byte[256] rr; int32 exponent`
 * (all little-endian). The caller base64-encodes this and appends
 * `" <comment>\0"` for the actual ADB `AUTH_RSAPUBLICKEY` wire payload,
 * matching the real `adbkey.pub` file format.
 */
fun encodeMincryptPublicKey(pkcs1PrivateKey: ByteArray): ByteArray {
    val (modulusBytes, exponentBytes) = extractModulusAndExponent(pkcs1PrivateKey)
    val n = BigUInt.fromBytesBigEndian(modulusBytes)

    val n0inv = computeN0Inv(n.lowWord())

    // R = 2^(RSA_NUM_WORDS * 32) = 2^2048; RR = R^2 mod n = 2^4096 mod n.
    // 4096 = 2^12, so this is 12 squarings of 2 (2^1 -> 2^2 -> 2^4 -> ... -> 2^4096).
    var rr = BigUInt.fromBytesBigEndian(byteArrayOf(2)).mod(n)
    repeat(12) { rr = (rr * rr).mod(n) }

    val exponent = exponentBytes.toUIntBigEndian()

    return int32LE(RSA_NUM_WORDS) +
        int32LE(n0inv.toInt()) +
        n.toBytesLittleEndian(RSA_NUM_BYTES) +
        rr.toBytesLittleEndian(RSA_NUM_BYTES) +
        int32LE(exponent.toInt())
}

/**
 * Computes n0inv = -1/n mod 2^32 (the Montgomery constant mincrypt stores
 * alongside the modulus), operating only on n's low 32 bits — everything
 * above bit 32 is irrelevant to a mod-2^32 inverse. Uses Newton's iteration
 * for the modular inverse of an odd number mod 2^32 (converges to full
 * 32-bit precision in 5 iterations); all arithmetic here is UInt, which
 * wraps mod 2^32 automatically on overflow, so no explicit masking is needed.
 */
private fun computeN0Inv(nLow32: UInt): UInt {
    var x = nLow32
    repeat(5) { x *= (2u - nLow32 * x) }
    return 0u - x
}

private fun int32LE(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value shr 8) and 0xFF).toByte(),
    ((value shr 16) and 0xFF).toByte(),
    ((value shr 24) and 0xFF).toByte(),
)

private fun ByteArray.toUIntBigEndian(): UInt {
    var result = 0u
    for (b in this) result = (result shl 8) or (b.toUInt() and 0xFFu)
    return result
}
