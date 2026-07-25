package com.homecontrol.ios.adb

import com.homecontrol.core.crypto.SecureKeyStorage
import com.homecontrol.core.crypto.asCFDictionaryRef
import com.homecontrol.core.crypto.pkcs1ToPkcs8
import com.homecontrol.core.crypto.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFErrorRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCreateRandomKey
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA

private const val KEY_ALIAS = "adb_identity_key_pkcs8"
private const val RSA_KEY_SIZE_BITS = 2048

/**
 * Owns a single RSA-2048 identity keypair reused for every ADB pairing
 * attempt — exactly like a real `~/.android/adbkey`. Android TVs remember
 * which public keys they've approved, so generating a fresh key on every
 * attempt would mean re-approving the "Allow debugging?" dialog on the TV
 * every single time instead of just once. Generated on first use via
 * `SecKeyCreateRandomKey`, exported to PKCS1 then wrapped to PKCS8 (matching
 * [com.homecontrol.core.crypto.signAdbAuthToken]'s documented contract), and
 * persisted via [SecureKeyStorage] (iOS Keychain).
 */
@OptIn(ExperimentalForeignApi::class)
class AdbKeyStore(private val secureKeyStorage: SecureKeyStorage = SecureKeyStorage("")) {

    /** PKCS8-encoded RSA private key, generating and persisting a new keypair on first use. */
    fun privateKeyPkcs8(): ByteArray {
        secureKeyStorage.retrieve(KEY_ALIAS)?.let { return it }
        val generated = generateKeyPkcs8()
        secureKeyStorage.store(KEY_ALIAS, generated)
        return generated
    }

    private fun generateKeyPkcs8(): ByteArray = memScoped {
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
            setObject(RSA_KEY_SIZE_BITS, forKey = interpretObjCPointer<NSString>(kSecAttrKeySizeInBits!!.rawValue))
        }
        val keyError = alloc<CFErrorRefVar>()
        // See core:crypto's AdbSigning.ios.kt doc comment: asCFDictionaryRef()
        // (CFBridgingRetain + reinterpret) is the only approach that both compiles
        // and works at runtime for this NSDictionary -> CFDictionaryRef direction.
        val secKey = SecKeyCreateRandomKey(attributes.asCFDictionaryRef(), keyError.ptr)
            ?: error("Failed to generate ADB RSA identity key")

        val exportError = alloc<CFErrorRefVar>()
        val exported = SecKeyCopyExternalRepresentation(secKey, exportError.ptr)
            ?: error("Failed to export ADB RSA identity key")
        val pkcs1Bytes = (CFBridgingRelease(exported) as NSData).toByteArray()
        pkcs1ToPkcs8(pkcs1Bytes)
    }
}
