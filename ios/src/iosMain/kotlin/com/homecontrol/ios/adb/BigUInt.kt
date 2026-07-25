package com.homecontrol.ios.adb

/**
 * Minimal unsigned arbitrary-precision integer — just enough to compute the
 * ADB "mincrypt" public key wire format's Montgomery precomputed value
 * `RR = R^2 mod n` for a single RSA-2048 key at key-generation time.
 * Kotlin/Native has no built-in BigInteger (`java.math.BigInteger` is
 * JVM-only). Deliberately simple (bit-by-bit long division for [mod]) over
 * fast, since this runs once per keypair, not per pairing attempt.
 *
 * Limbs are 32-bit, little-endian order (`limbs[0]` = least significant),
 * with no high zero limbs except a single `[0]` limb representing zero.
 */
class BigUInt private constructor(private val limbs: UIntArray) {

    companion object {
        val ZERO = BigUInt(uintArrayOf(0u))
        val ONE = BigUInt(uintArrayOf(1u))

        fun fromBytesBigEndian(bytes: ByteArray): BigUInt {
            if (bytes.isEmpty()) return ZERO
            val limbCount = (bytes.size + 3) / 4
            val limbs = UIntArray(limbCount)
            var byteIndex = bytes.size - 1
            for (limbIndex in 0 until limbCount) {
                var limb = 0u
                var shift = 0
                while (shift < 32 && byteIndex >= 0) {
                    limb = limb or ((bytes[byteIndex].toUInt() and 0xFFu) shl shift)
                    byteIndex--
                    shift += 8
                }
                limbs[limbIndex] = limb
            }
            return normalize(limbs)
        }

        private fun normalize(limbs: UIntArray): BigUInt {
            var size = limbs.size
            while (size > 1 && limbs[size - 1] == 0u) size--
            return BigUInt(limbs.copyOf(size))
        }
    }

    /** Least significant 32 bits — everything needed for a mod-2^32 computation (e.g. the ADB n0inv constant). */
    fun lowWord(): UInt = limbs[0]

    fun toBytesLittleEndian(byteLength: Int): ByteArray {
        val result = ByteArray(byteLength)
        for (i in 0 until byteLength) {
            val limbIndex = i / 4
            val shift = (i % 4) * 8
            result[i] = if (limbIndex < limbs.size) ((limbs[limbIndex] shr shift) and 0xFFu).toByte() else 0
        }
        return result
    }

    fun bitLength(): Int {
        var bits = (limbs.size - 1) * 32
        var v = limbs[limbs.size - 1]
        while (v != 0u) {
            bits++
            v = v shr 1
        }
        return bits
    }

    fun testBit(bitIndex: Int): Boolean {
        val limbIndex = bitIndex / 32
        if (limbIndex >= limbs.size) return false
        return (limbs[limbIndex] shr (bitIndex % 32)) and 1u == 1u
    }

    fun shiftLeft1(): BigUInt {
        val result = UIntArray(limbs.size + 1)
        var carry = 0u
        for (i in limbs.indices) {
            result[i] = (limbs[i] shl 1) or carry
            carry = limbs[i] shr 31
        }
        result[limbs.size] = carry
        return normalize(result)
    }

    operator fun compareTo(other: BigUInt): Int {
        if (limbs.size != other.limbs.size) return limbs.size.compareTo(other.limbs.size)
        for (i in limbs.indices.reversed()) {
            if (limbs[i] != other.limbs[i]) return limbs[i].compareTo(other.limbs[i])
        }
        return 0
    }

    operator fun plus(other: BigUInt): BigUInt {
        val maxSize = maxOf(limbs.size, other.limbs.size)
        val result = UIntArray(maxSize + 1)
        var carry = 0uL
        for (i in 0 until maxSize) {
            val a = if (i < limbs.size) limbs[i].toULong() else 0uL
            val b = if (i < other.limbs.size) other.limbs[i].toULong() else 0uL
            val sum = a + b + carry
            result[i] = sum.toUInt()
            carry = sum shr 32
        }
        result[maxSize] = carry.toUInt()
        return normalize(result)
    }

    /** Subtracts [other] from this, assuming this >= other. */
    operator fun minus(other: BigUInt): BigUInt {
        val result = UIntArray(limbs.size)
        var borrow = 0L
        for (i in limbs.indices) {
            val otherLimb = if (i < other.limbs.size) other.limbs[i].toLong() else 0L
            var diff = limbs[i].toLong() - otherLimb - borrow
            if (diff < 0) {
                diff += 0x100000000L
                borrow = 1
            } else {
                borrow = 0
            }
            result[i] = diff.toUInt()
        }
        return normalize(result)
    }

    operator fun times(other: BigUInt): BigUInt {
        val result = ULongArray(limbs.size + other.limbs.size)
        for (i in limbs.indices) {
            val a = limbs[i].toULong()
            if (a == 0uL) continue
            var carry = 0uL
            for (j in other.limbs.indices) {
                val product = a * other.limbs[j].toULong() + result[i + j] + carry
                result[i + j] = product and 0xFFFFFFFFuL
                carry = product shr 32
            }
            var k = i + other.limbs.size
            while (carry != 0uL) {
                val sum = result[k] + carry
                result[k] = sum and 0xFFFFFFFFuL
                carry = sum shr 32
                k++
            }
        }
        return normalize(UIntArray(result.size) { result[it].toUInt() })
    }

    /** this mod [modulus], via bit-by-bit binary long division. */
    fun mod(modulus: BigUInt): BigUInt {
        var remainder = ZERO
        for (bitIndex in bitLength() - 1 downTo 0) {
            remainder = remainder.shiftLeft1()
            if (testBit(bitIndex)) {
                remainder += ONE
            }
            if (remainder >= modulus) {
                remainder -= modulus
            }
        }
        return remainder
    }
}
