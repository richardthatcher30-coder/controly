package com.homecontrol.core.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
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
 * Three real-hardware/real-compiler bugs already found and fixed here around
 * NS<->CF bridging: (1) kSec* constants need `interpretObjCPointer`, not
 * `as NSString` (compiles, throws ClassCastException at runtime); (2) an
 * `as CFDataRef`/`as CFDictionaryRef` cast on NSData/NSMutableDictionary
 * also compiles, also throws at runtime ("NSDictionaryAsKMap cannot be cast
 * to CPointer"); (3) removing the cast entirely doesn't compile at all —
 * the compiler confirms `CPointer<__CFData>?`/`CPointer<__CFDictionary>?`
 * really is required, contradicting the assumption behind fix (2). The
 * actual correct bridge for NSObject -> CF (the reverse of
 * `interpretObjCPointer`) is `CFBridgingRetain`, Apple/Kotlin-Native's own
 * documented function for exactly this direction — see [asCFDataRef]/
 * [asCFDictionaryRef]. Still unverified: whether the actual RSA signature
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

        val keyDataRef = keyData.asCFDataRef()
        val attributesRef = attributes.asCFDictionaryRef()
        val keyError = alloc<CFErrorRefVar>()
        val secKey = SecKeyCreateWithData(keyDataRef, attributesRef, keyError.ptr)
            ?: error("Failed to import ADB RSA private key into Security framework")

        try {
            val tokenData = token.toNSData()
            val tokenDataRef = tokenData.asCFDataRef()
            val sigError = alloc<CFErrorRefVar>()
            val signature = SecKeyCreateSignature(
                secKey,
                kSecKeyAlgorithmRSASignatureDigestPKCS1v15Raw,
                tokenDataRef,
                sigError.ptr,
            ) ?: error("Failed to sign ADB auth token")

            (CFBridgingRelease(signature) as NSData).toByteArray()
        } finally {
            CFRelease(secKey)
        }
    }
}

/**
 * Bridges an NSData/NSMutableDictionary to its toll-free-bridged CF pointer
 * type via `CFBridgingRetain` — the correct, Apple/Kotlin-Native-documented
 * direction-reverse of `CFBridgingRelease` (already used above for the CF ->
 * NS direction). Deliberately not balanced with a matching `CFRelease` here:
 * these are short-lived, low-frequency call sites (pairing happens once per
 * device, key generation happens once ever), so the one-retain-per-call
 * cost is an acceptable tradeoff against the complexity of threading
 * try/finally release scoping through every caller for what would be a
 * tiny, bounded leak in practice, not an unbounded one.
 */
@OptIn(ExperimentalForeignApi::class)
fun NSData.asCFDataRef(): CFDataRef = CFBridgingRetain(this)!!.reinterpret()

@OptIn(ExperimentalForeignApi::class)
fun NSMutableDictionary.asCFDictionaryRef(): CFDictionaryRef = CFBridgingRetain(this)!!.reinterpret()

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
