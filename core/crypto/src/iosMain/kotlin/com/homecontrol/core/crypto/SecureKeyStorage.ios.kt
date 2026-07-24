package com.homecontrol.core.crypto

/**
 * TODO(Phase 5 — plugin-androidtv port): implement via iOS Keychain Services
 * (`SecItemAdd`/`SecItemCopyMatching`/`SecItemDelete` with
 * `kSecClass = kSecClassGenericPassword`,
 * `kSecAttrAccessible = kSecAttrAccessibleWhenUnlockedThisDeviceOnly`) —
 * storing [alias]'s bytes directly as the Keychain item's `kSecValueData`,
 * since Keychain is itself the hardware-backed secure store (no separate
 * AES-GCM wrapping step needed the way Android's Keystore requires — see
 * the class doc on the `expect` declaration). [storageDir] is intentionally
 * unused on this platform.
 */
actual class SecureKeyStorage actual constructor(storageDir: String) {

    actual fun store(alias: String, bytes: ByteArray) {
        TODO("Implement via Keychain Services (SecItemAdd) in Phase 5")
    }

    actual fun retrieve(alias: String): ByteArray? {
        TODO("Implement via Keychain Services (SecItemCopyMatching) in Phase 5")
    }

    actual fun remove(alias: String) {
        TODO("Implement via Keychain Services (SecItemDelete) in Phase 5")
    }
}
