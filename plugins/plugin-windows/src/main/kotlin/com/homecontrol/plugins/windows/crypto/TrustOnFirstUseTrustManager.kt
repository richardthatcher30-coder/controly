package com.homecontrol.plugins.windows.crypto

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Home-network trust-on-first-use: the first connection to a given PC
 * accepts whatever self-signed certificate it presents and reports its
 * fingerprint back via [onCertificateSeen]; every connection after that must
 * present the same fingerprint ([expectedFingerprint]) or the connection is
 * rejected as a possible impersonation.
 */
internal class TrustOnFirstUseTrustManager(
    private val expectedFingerprint: String?,
    private val onCertificateSeen: (String) -> Unit,
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val certificate = chain.firstOrNull() ?: throw CertificateException("No certificate presented")
        val fingerprint = fingerprintOf(certificate)

        if (expectedFingerprint != null && !expectedFingerprint.equals(fingerprint, ignoreCase = true)) {
            throw CertificateException("Certificate fingerprint changed since pairing — refusing to connect")
        }

        onCertificateSeen(fingerprint)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        fun fingerprintOf(certificate: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            return digest.joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
