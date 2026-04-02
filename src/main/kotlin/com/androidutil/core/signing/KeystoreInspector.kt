package com.androidutil.core.signing

import java.io.FileInputStream
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat

class KeystoreInspector {

    fun inspect(keystorePath: Path, password: String?): KeystoreInfo {
        val pass = password?.toCharArray() ?: CharArray(0)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        // Try JKS first, then PKCS12
        val keystore = tryLoadKeystore(keystorePath, pass, "JKS")
            ?: tryLoadKeystore(keystorePath, pass, "PKCS12")
            ?: throw IllegalStateException(
                "Cannot load keystore. Check the file format and password."
            )

        val entries = mutableListOf<CertificateInfo>()

        for (alias in keystore.aliases()) {
            val cert = keystore.getCertificate(alias)
            if (cert is X509Certificate) {
                val sha256 = fingerprint(cert.encoded, "SHA-256")
                val sha1 = fingerprint(cert.encoded, "SHA-1")

                entries.add(
                    CertificateInfo(
                        alias = alias,
                        subjectDN = cert.subjectDN.toString(),
                        issuerDN = cert.issuerDN.toString(),
                        serialNumber = cert.serialNumber.toString(16),
                        validFrom = dateFormat.format(cert.notBefore),
                        validUntil = dateFormat.format(cert.notAfter),
                        sha256Fingerprint = sha256,
                        sha1Fingerprint = sha1,
                        signatureAlgorithm = cert.sigAlgName
                    )
                )
            }
        }

        return KeystoreInfo(
            type = keystore.type,
            provider = keystore.provider.name,
            entries = entries
        )
    }

    private fun tryLoadKeystore(path: Path, password: CharArray, type: String): KeyStore? {
        return try {
            val ks = KeyStore.getInstance(type)
            FileInputStream(path.toFile()).use { fis ->
                ks.load(fis, password)
            }
            ks
        } catch (e: Exception) {
            null
        }
    }

    private fun fingerprint(certBytes: ByteArray, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        return md.digest(certBytes).joinToString(":") { "%02X".format(it) }
    }
}
