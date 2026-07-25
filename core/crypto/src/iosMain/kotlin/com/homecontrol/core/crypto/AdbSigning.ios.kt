package com.homecontrol.core.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureDigestPKCS1v15Raw
import platform.posix.memcpy

/**
 * Implements the shared `signAdbAuthToken` contract via Apple's Security
 * framework. [privateKeyPkcs8] arrives PKCS8-wrapped (matching the Android
 * actual's `KeyFactory`/`PKCS8EncodedKeySpec` contract, which this module's
 * ADB key-generation code — in the `ios` module — must honor when it stores
 * a key); `SecKeyCreateWithData` only accepts the bare PKCS1 `RSAPrivateKey`
 * structure, so the PKCS8 envelope is stripped first via [pkcs8ToPkcs1].
 *
 * `kSecKeyAlgorithmRSASignatureDigestPKCS1v15Raw` is the one Security.framework
 * algorithm that PKCS#1v1.5-pads and RSA-signs pre-built bytes with no
 * additional hashing step, matching Java's `NONEwithRSA` used by the Android
 * actual — required because the ADB auth token itself is what gets signed,
 * not a hash of it.
 *
 * Two real-hardware bugs already found and fixed here: kSec* constants need
 * `interpretObjCPointer`, not `as NSString` (compiles, throws at runtime);
 * and `SecKeyCreateWithData`/`SecKeyCreateSignature`'s CFDataRef/CFDictionaryRef
 * parameters take the NSData/NSDictionary objects directly with no cast at
 * all (an `as CFDataRef`/`as CFDictionaryRef` cast also compiles, also
 * throws at runtime). Still unverified: whether the actual RSA signature
 * produced is byte-for-byte correct — that only shows up as the TV either
 * accepting or rejecting the pairing key, not as a crash.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun signAdbAuthToken(token: ByteArray, privateKeyPkcs8: ByteArray): ByteArray {
    val pkcs1 = pkcs8ToPkcs1(privateKeyPkcs8)
    return memScoped {
        val keyData = pkcs1.toNSData()
        // kSec* constants are typed CPointer<__CFString>? by cinterop. An `as NSString`
        // cast compiles (only warns CAST_NEVER_SUCCEEDS) but throws ClassCastException
        // at runtime, confirmed on real hardware -- interpretObjCPointer is the actual
        // correct bridge (reinterprets the raw pointer directly as its Kotlin/Native
        // ObjC wrapper type, rather than attempting an impossible representation cast).
        val attributes = NSMutableDictionary().apply {
            setObject(
                interpretObjCPointer<NSString>(kSecAttrKeyTypeRSA!!.rawValue),
                forKey = interpretObjCPointer<NSString>(kSecAttrKeyType!!.rawValue),
            )
            setObject(
                interpretObjCPointer<NSString>(kSecAttrKeyClassPrivate!!.rawValue),
                forKey = interpretObjCPointer<NSString>(kSecAttrKeyClass!!.rawValue),
            )
        }

        val keyError = alloc<CFErrorRefVar>()
        // SecKeyCreateWithData's CFDataRef/CFDictionaryRef parameters are toll-free
        // bridged to NSData/NSDictionary in the underlying header, and Kotlin/Native's
        // cinterop accepts the Foundation objects directly here -- no cast needed (an
        // `as CFDataRef`/`as CFDictionaryRef` cast was tried first and confirmed broken
        // at runtime on real hardware: "NSDictionaryAsKMap cannot be cast to CPointer").
        val secKey = SecKeyCreateWithData(keyData, attributes, keyError.ptr)
            ?: error("Failed to import ADB RSA private key into Security framework")

        try {
            val tokenData = token.toNSData()
            val sigError = alloc<CFErrorRefVar>()
            val signature = SecKeyCreateSignature(
                secKey,
                kSecKeyAlgorithmRSASignatureDigestPKCS1v15Raw,
                tokenData,
                sigError.ptr,
            ) ?: error("Failed to sign ADB auth token")

            (CFBridgingRelease(signature) as NSData).toByteArray()
        } finally {
            CFRelease(secKey)
        }
    }
}

/** Public (not internal) so other modules depending on this one — e.g. `ios`'s ADB keypair generation — can reuse the same bridging helpers. */
@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData()
} else {
    usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, size.convert()) }
    return result
}
