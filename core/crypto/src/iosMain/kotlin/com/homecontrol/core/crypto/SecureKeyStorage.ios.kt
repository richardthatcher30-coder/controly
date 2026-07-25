package com.homecontrol.core.crypto

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val SERVICE_NAME = "com.controly.securekeystorage"

/**
 * Implements the shared `SecureKeyStorage` contract via iOS Keychain
 * Services (`SecItemAdd`/`SecItemCopyMatching`/`SecItemDelete`) — storing
 * [alias]'s bytes directly as the Keychain item's `kSecValueData`, since
 * Keychain is itself the hardware-backed secure store (no separate AES-GCM
 * wrapping step needed the way Android's Keystore requires — see the class
 * doc on the `expect` declaration). [storageDir] is intentionally unused on
 * this platform; every alias lives under one fixed Keychain service name
 * instead.
 *
 * See `AdbSigning.ios.kt`'s doc comment for the full story — three rounds of
 * NS<->CF bridging bugs found via real-hardware testing. `SecItemAdd`/
 * `SecItemCopyMatching`/`SecItemDelete`'s `CFDictionaryRef` parameter needs
 * [asCFDictionaryRef] (`CFBridgingRetain` + reinterpret) — neither an
 * `as CFDictionaryRef` cast (compiles, throws at runtime) nor passing the
 * `NSMutableDictionary` with no conversion at all (doesn't compile) works.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SecureKeyStorage actual constructor(storageDir: String) {

    /**
     * The raw `OSStatus` from the most recent [retrieve] call that didn't find
     * anything -- surfaced (not part of the shared `expect` contract, iOS-only)
     * to diagnose the "TV always re-asks for approval" bug report: if [retrieve]
     * keeps missing an item [store] just wrote in the very same run, the actual
     * status code (e.g. errSecItemNotFound vs. an entitlement/access error) tells
     * us why instead of requiring another guess-and-ship cycle.
     */
    var lastRetrieveMissStatus: Long? = null
        private set

    actual fun store(alias: String, bytes: ByteArray) {
        remove(alias) // SecItemAdd fails with errSecDuplicateItem if the alias is already present
        val query = NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword.asNSString(), forKey = kSecClass.asNSString())
            setObject(SERVICE_NAME, forKey = kSecAttrService.asNSString())
            setObject(alias, forKey = kSecAttrAccount.asNSString())
            setObject(bytes.toNSData(), forKey = kSecValueData.asNSString())
            setObject(kSecAttrAccessibleWhenUnlockedThisDeviceOnly.asNSString(), forKey = kSecAttrAccessible.asNSString())
        }
        val status = SecItemAdd(query.asCFDictionaryRef(), null)
        check(status == errSecSuccess) { "Keychain SecItemAdd failed with OSStatus $status" }
    }

    actual fun retrieve(alias: String): ByteArray? = memScoped {
        val query = NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword.asNSString(), forKey = kSecClass.asNSString())
            setObject(SERVICE_NAME, forKey = kSecAttrService.asNSString())
            setObject(alias, forKey = kSecAttrAccount.asNSString())
            setObject(true, forKey = kSecReturnData.asNSString())
            setObject(kSecMatchLimitOne.asNSString(), forKey = kSecMatchLimit.asNSString())
        }
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query.asCFDictionaryRef(), result.ptr)
        if (status != errSecSuccess) {
            lastRetrieveMissStatus = status.toLong()
            return@memScoped null
        }
        lastRetrieveMissStatus = null
        (CFBridgingRelease(result.value) as? NSData)?.toByteArray()
    }

    actual fun remove(alias: String) {
        val query = NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword.asNSString(), forKey = kSecClass.asNSString())
            setObject(SERVICE_NAME, forKey = kSecAttrService.asNSString())
            setObject(alias, forKey = kSecAttrAccount.asNSString())
        }
        SecItemDelete(query.asCFDictionaryRef())
        // Return status intentionally ignored — errSecItemNotFound is an expected, benign outcome here.
    }
}

/**
 * kSec* constants are typed `CPointer<__CFString>?` by cinterop. Despite
 * CFString/NSString being toll-free bridged at the ObjC runtime level — the
 * underlying object is genuinely the same memory, just represented by two
 * different Kotlin static types — an `as NSString` cast does NOT work: it
 * compiles (the compiler permits it, only warning `CAST_NEVER_SUCCEEDS`,
 * which is a much more literal warning than it looks — confirmed by an
 * actual `ClassCastException` at runtime on real hardware) but always
 * throws. The correct bridge is `interpretObjCPointer`, which reinterprets
 * the raw pointer value directly as its Kotlin/Native ObjC wrapper type
 * without going through a (impossible) representation-changing cast.
 */
@OptIn(ExperimentalForeignApi::class)
private fun CPointer<*>?.asNSString(): NSString = interpretObjCPointer(this!!.rawValue)
