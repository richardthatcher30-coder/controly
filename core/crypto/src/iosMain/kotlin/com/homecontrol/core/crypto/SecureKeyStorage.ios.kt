package com.homecontrol.core.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
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
 * UNVERIFIED AGAINST REAL HARDWARE — same caveat as `AdbSigning.ios.kt`: no
 * Mac/Xcode available in the environment that wrote this. Needs a CodeMagic
 * build + real-device run to confirm the NSMutableDictionary/CFDictionaryRef
 * bridging casts behave as expected.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SecureKeyStorage actual constructor(storageDir: String) {

    actual fun store(alias: String, bytes: ByteArray) {
        remove(alias) // SecItemAdd fails with errSecDuplicateItem if the alias is already present
        val query = NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword.asNSString(), forKey = kSecClass.asNSString())
            setObject(SERVICE_NAME, forKey = kSecAttrService.asNSString())
            setObject(alias, forKey = kSecAttrAccount.asNSString())
            setObject(bytes.toNSData(), forKey = kSecValueData.asNSString())
            setObject(kSecAttrAccessibleWhenUnlockedThisDeviceOnly.asNSString(), forKey = kSecAttrAccessible.asNSString())
        }
        @Suppress("UNCHECKED_CAST")
        val status = SecItemAdd(query as CFDictionaryRef, null)
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
        @Suppress("UNCHECKED_CAST")
        val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
        if (status != errSecSuccess) return@memScoped null
        (CFBridgingRelease(result.value) as? NSData)?.toByteArray()
    }

    actual fun remove(alias: String) {
        val query = NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword.asNSString(), forKey = kSecClass.asNSString())
            setObject(SERVICE_NAME, forKey = kSecAttrService.asNSString())
            setObject(alias, forKey = kSecAttrAccount.asNSString())
        }
        @Suppress("UNCHECKED_CAST")
        SecItemDelete(query as CFDictionaryRef)
        // Return status intentionally ignored — errSecItemNotFound is an expected, benign outcome here.
    }
}

/**
 * kSec* constants are typed `CPointer<__CFString>?` by cinterop, not
 * auto-bridged to `NSString`/`NSCopyingProtocol` despite CFString/NSString
 * being toll-free bridged at the ObjC runtime level — an explicit unchecked
 * cast is required to use them as `NSDictionary` keys/values.
 */
@Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST")
private fun kotlinx.cinterop.CPointer<*>?.asNSString(): NSString = this as NSString
