package com.homecontrol.core.crypto

/**
 * Raw, unhashed RSA signing of a pre-built ADB auth token — the ADB wire
 * protocol's own handshake already hands over the exact bytes to sign, with
 * no additional digest step on the client side. This is what Java's
 * `Signature.getInstance("NONEwithRSA")` does (PKCS#1v1.5-pad the caller's
 * bytes and RSA-encrypt, but skip hashing them first) — a genuinely
 * non-standard operation with no equivalent in general-purpose multiplatform
 * crypto libraries, which always couple RSA signing to a digest. Hand-rolled
 * per platform for exactly this reason.
 *
 * [privateKeyPkcs8] is the RSA private key in PKCS8 DER encoding — the
 * portable representation used everywhere in this module, since a live
 * `java.security.PrivateKey` object isn't itself portable to iOS.
 */
expect fun signAdbAuthToken(token: ByteArray, privateKeyPkcs8: ByteArray): ByteArray
