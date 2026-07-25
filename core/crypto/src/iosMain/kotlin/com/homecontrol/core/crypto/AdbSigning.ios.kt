package com.homecontrol.core.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
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
 * UNVERIFIED AGAINST REAL HARDWARE: this compiles for iosArm64/iosSimulatorArm64
 * but has not been run against a real ADB handshake — no Mac/Xcode is
 * available in the environment that wrote it. Needs a CodeMagic build +
 * on-device pairing attempt against a real Android TV/Fire TV to confirm the
 * NSData/CFDataRef bridging casts and DER unwrapping are correct end to end.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun signAdbAuthToken(token: ByteArray, privateKeyPkcs8: ByteArray): ByteArray {
    val pkcs1 = pkcs8ToPkcs1(privateKeyPkcs8)
    return memScoped {
        val keyData = pkcs1.toNSData()
        // kSec* constants are typed CPointer<__CFString>? by cinterop, not auto-bridged
        // to NSString/NSCopyingProtocol despite CFString/NSString being toll-free bridged
        // at the ObjC runtime level — an explicit unchecked cast is required.
        @Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST")
        val attributes = NSMutableDictionary().apply {
            setObject(kSecAttrKeyTypeRSA as NSString, forKey = kSecAttrKeyType as NSString)
            setObject(kSecAttrKeyClassPrivate as NSString, forKey = kSecAttrKeyClass as NSString)
        }

        val keyError = alloc<CFErrorRefVar>()
        @Suppress("UNCHECKED_CAST")
        val secKey = SecKeyCreateWithData(keyData as CFDataRef, attributes as CFDictionaryRef, keyError.ptr)
            ?: error("Failed to import ADB RSA private key into Security framework")

        try {
            val tokenData = token.toNSData()
            val sigError = alloc<CFErrorRefVar>()
            @Suppress("UNCHECKED_CAST")
            val signature = SecKeyCreateSignature(
                secKey,
                kSecKeyAlgorithmRSASignatureDigestPKCS1v15Raw,
                tokenData as CFDataRef,
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
