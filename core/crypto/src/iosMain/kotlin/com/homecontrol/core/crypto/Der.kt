package com.homecontrol.core.crypto

/**
 * Minimal ASN.1 DER reader/writer — only what's needed to walk RSA
 * PKCS1/PKCS8 key structures (SEQUENCE, INTEGER, OCTET STRING, and skipping
 * arbitrary TLVs like an AlgorithmIdentifier's OID+NULL). Not a general
 * ASN.1 library. Public (not internal) so both this module's [AdbSigning.ios.kt]
 * and the `ios` module's ADB public-key encoder can share one parser instead
 * of each hand-rolling their own.
 */
class DerReader(private val data: ByteArray) {
    private var pos = 0

    private fun readTag(): Int = data[pos++].toInt() and 0xFF

    private fun readLength(): Int {
        val first = data[pos++].toInt() and 0xFF
        if (first and 0x80 == 0) return first
        val numBytes = first and 0x7F
        var length = 0
        repeat(numBytes) { length = (length shl 8) or (data[pos++].toInt() and 0xFF) }
        return length
    }

    /** Enters a constructed TLV (SEQUENCE etc.) and returns a reader scoped to just its content. */
    fun readSequence(): DerReader {
        val tag = readTag()
        require(tag == 0x30) { "Expected SEQUENCE (0x30), got 0x${tag.toString(16)}" }
        val length = readLength()
        val content = data.copyOfRange(pos, pos + length)
        pos += length
        return DerReader(content)
    }

    /** Reads an INTEGER's content bytes, stripping a single DER-mandated leading 0x00 pad byte if present. */
    fun readInteger(): ByteArray {
        val tag = readTag()
        require(tag == 0x02) { "Expected INTEGER (0x02), got 0x${tag.toString(16)}" }
        val length = readLength()
        var bytes = data.copyOfRange(pos, pos + length)
        pos += length
        if (bytes.size > 1 && bytes[0] == 0.toByte() && (bytes[1].toInt() and 0x80) != 0) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        return bytes
    }

    /** Reads an OCTET STRING's raw content bytes. */
    fun readOctetString(): ByteArray {
        val tag = readTag()
        require(tag == 0x04) { "Expected OCTET STRING (0x04), got 0x${tag.toString(16)}" }
        val length = readLength()
        val bytes = data.copyOfRange(pos, pos + length)
        pos += length
        return bytes
    }

    /** Skips over the next TLV entirely (tag + length + content), regardless of type. */
    fun skipValue() {
        readTag()
        val length = readLength()
        pos += length
    }
}

private fun derLength(length: Int): ByteArray = when {
    length < 0x80 -> byteArrayOf(length.toByte())
    else -> {
        val lengthBytes = mutableListOf<Byte>()
        var remaining = length
        while (remaining > 0) {
            lengthBytes.add(0, (remaining and 0xFF).toByte())
            remaining = remaining ushr 8
        }
        byteArrayOf((0x80 or lengthBytes.size).toByte()) + lengthBytes.toByteArray()
    }
}

private fun derTlv(tag: Int, content: ByteArray): ByteArray =
    byteArrayOf(tag.toByte()) + derLength(content.size) + content

fun derSequence(vararg elements: ByteArray): ByteArray =
    derTlv(0x30, elements.fold(byteArrayOf()) { acc, e -> acc + e })

/**
 * Encodes an unsigned big-endian integer as a DER INTEGER, adding a leading
 * 0x00 pad byte if the high bit is set (so it isn't misread as negative).
 */
fun derInteger(unsignedBigEndian: ByteArray): ByteArray {
    val dropped = unsignedBigEndian.dropWhile { it == 0.toByte() }.toByteArray()
    // ByteArray has no stdlib ifEmpty (unlike Array<T>/Collection/etc.) — check manually.
    val trimmed = if (dropped.isEmpty()) byteArrayOf(0) else dropped
    val content = if (trimmed[0].toInt() and 0x80 != 0) byteArrayOf(0) + trimmed else trimmed
    return derTlv(0x02, content)
}

fun derOctetString(content: ByteArray): ByteArray = derTlv(0x04, content)

fun derNull(): ByteArray = byteArrayOf(0x05, 0x00)

fun derObjectIdentifier(encoded: ByteArray): ByteArray = derTlv(0x06, encoded)

/** DER content bytes for OID 1.2.840.113549.1.1.1 (rsaEncryption) — the standard PKCS#1/X.509 RSA algorithm identifier. */
val RSA_ENCRYPTION_OID = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)

/** Extracts the inner PKCS1 RSAPrivateKey DER bytes from a PKCS8 PrivateKeyInfo structure. */
fun pkcs8ToPkcs1(pkcs8: ByteArray): ByteArray {
    val reader = DerReader(pkcs8).readSequence()
    reader.readInteger() // version
    reader.skipValue() // AlgorithmIdentifier SEQUENCE (rsaEncryption OID + NULL)
    return reader.readOctetString() // contains the PKCS1 bytes verbatim
}

/** Wraps a PKCS1 RSAPrivateKey DER blob into a PKCS8 PrivateKeyInfo structure. */
fun pkcs1ToPkcs8(pkcs1PrivateKey: ByteArray): ByteArray {
    val version = derInteger(byteArrayOf(0))
    val algorithmIdentifier = derSequence(derObjectIdentifier(RSA_ENCRYPTION_OID), derNull())
    val privateKeyOctetString = derOctetString(pkcs1PrivateKey)
    return derSequence(version, algorithmIdentifier, privateKeyOctetString)
}

/** Reads the modulus (n) and public exponent (e) out of a PKCS1 RSAPrivateKey DER blob. */
fun extractModulusAndExponent(pkcs1PrivateKey: ByteArray): Pair<ByteArray, ByteArray> {
    val reader = DerReader(pkcs1PrivateKey).readSequence()
    reader.readInteger() // version
    val modulus = reader.readInteger()
    val publicExponent = reader.readInteger()
    return modulus to publicExponent
}
