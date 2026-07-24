package com.homecontrol.core.crypto

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

actual fun signAdbAuthToken(token: ByteArray, privateKeyPkcs8: ByteArray): ByteArray {
    val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateKeyPkcs8))
    return Signature.getInstance("NONEwithRSA").apply {
        initSign(privateKey)
        update(token)
    }.sign()
}
