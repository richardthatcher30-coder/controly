package com.homecontrol.core.crypto

/**
 * TODO(Phase 5 — plugin-androidtv port): implement via Apple's Security
 * framework, `SecKeyCreateSignature` with algorithm
 * `kSecKeyAlgorithmRSASignatureDigestPKCS1v15Raw` — the one Security.framework
 * algorithm that signs pre-built bytes with PKCS#1v1.5 padding and no
 * additional hashing, matching Java's `NONEwithRSA` exactly (see the
 * migration plan's crypto section for why this specific algorithm was
 * chosen). `Security.framework` is a plain C API, directly cinterop-able
 * from Kotlin/Native without a Swift shim. Left unimplemented here rather
 * than guessed at, since correctness can only be verified with a fixed
 * test vector run on real hardware/CodeMagic (also specified in the plan),
 * neither of which is available from this development environment.
 */
actual fun signAdbAuthToken(token: ByteArray, privateKeyPkcs8: ByteArray): ByteArray {
    TODO("Implement via SecKeyCreateSignature(kSecKeyAlgorithmRSASignatureDigestPKCS1v15Raw) in Phase 5")
}
