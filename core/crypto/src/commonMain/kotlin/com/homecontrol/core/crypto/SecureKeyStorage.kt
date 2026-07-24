package com.homecontrol.core.crypto

/**
 * Stores small secret byte blobs (the ADB private key, pairing tokens) behind
 * whatever hardware-backed secure store the platform provides. Deliberately
 * NOT a thin "AES-encrypt-then-write-a-file" abstraction: Android's Keystore
 * and iOS's Keychain have a genuinely different shape (Android needs an
 * explicit AES-GCM key held in `AndroidKeyStore` wrapping the ciphertext you
 * write yourself, and that ciphertext itself needs somewhere to live;
 * Keychain items are themselves the secure store, with no separate wrapping
 * or file-of-its-own needed) — each `actual` owns that difference, callers
 * only ever see plain bytes in and out.
 *
 * [storageDir] is a plain absolute path (the Android `actual` expects
 * `context.filesDir.path`, the iOS `actual` ignores it entirely since
 * Keychain items aren't file-backed) — passed in explicitly by the caller
 * rather than resolved via a hidden global `Context`, so this module stays
 * free of any DI-framework or Android-lifecycle coupling of its own.
 */
expect class SecureKeyStorage(storageDir: String) {
    fun store(alias: String, bytes: ByteArray)
    fun retrieve(alias: String): ByteArray?
    fun remove(alias: String)
}
