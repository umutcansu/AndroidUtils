package com.androidutil.core.signing

import com.androidutil.sdk.AndroidSdkLocator
import com.androidutil.util.ProcessRunner
import java.nio.file.Path
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.jar.JarFile

class SignatureVerifier {

    fun verify(filePath: Path): SignatureVerificationResult {
        val tools = AndroidSdkLocator.locate()
        val ext = filePath.toString().substringAfterLast('.').lowercase()

        // apksigner only works with APK files
        if (ext == "apk" && tools.apksignerPath != null) {
            return verifyWithApksigner(filePath, tools.apksignerPath)
        }

        // JAR signature check works for both AAB and APK
        return verifyBasic(filePath)
    }

    private fun verifyWithApksigner(apkPath: Path, apksignerPath: Path): SignatureVerificationResult {
        val result = ProcessRunner.run(
            listOf(apksignerPath.toString(), "verify", "--verbose", "--print-certs", apkPath.toString())
        )

        val output = result.stdout + result.stderr

        val v1 = parseSchemeStatus(output, "v1")
        val v2 = parseSchemeStatus(output, "v2")
        val v3 = parseSchemeStatus(output, "v3")
        val v4 = parseSchemeStatus(output, "v4")

        val certificates = parseCertificates(output)
        val errors = if (result.exitCode != 0) {
            output.lines().filter { it.contains("ERROR") || it.contains("DOES NOT VERIFY") }
        } else {
            emptyList()
        }

        return SignatureVerificationResult(
            isValid = result.exitCode == 0,
            v1SchemeValid = v1,
            v2SchemeValid = v2,
            v3SchemeValid = v3,
            v4SchemeValid = v4,
            certificates = certificates,
            errors = errors
        )
    }

    private fun verifyBasic(apkPath: Path): SignatureVerificationResult {
        val certs = mutableListOf<CertificateInfo>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        try {
            JarFile(apkPath.toFile()).use { jar ->
                val entry = jar.getJarEntry("META-INF/CERT.RSA")
                    ?: jar.getJarEntry("META-INF/CERT.DSA")
                    ?: return SignatureVerificationResult(
                        isValid = false, null, null, null, null, emptyList(),
                        listOf("No signature found in file")
                    )

                jar.getInputStream(entry).use { stream ->
                    val cf = CertificateFactory.getInstance("X.509")
                    for (cert in cf.generateCertificates(stream)) {
                        if (cert is X509Certificate) {
                            val sha256 = fingerprint(cert.encoded, "SHA-256")
                            val sha1 = fingerprint(cert.encoded, "SHA-1")
                            certs.add(
                                CertificateInfo(
                                    alias = "signer",
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
                }
            }
        } catch (e: Exception) {
            return SignatureVerificationResult(
                isValid = false, null, null, null, null, emptyList(),
                listOf("Failed to read signature: ${e.message}")
            )
        }

        return SignatureVerificationResult(
            isValid = certs.isNotEmpty(),
            v1SchemeValid = certs.isNotEmpty(),
            v2SchemeValid = null,
            v3SchemeValid = null,
            v4SchemeValid = null,
            certificates = certs,
            errors = emptyList()
        )
    }

    private fun parseSchemeStatus(output: String, scheme: String): Boolean? {
        val regex = Regex("Verified using $scheme scheme.*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        return regex.find(output)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }

    private fun parseCertificates(output: String): List<CertificateInfo> {
        val certs = mutableListOf<CertificateInfo>()
        val sha256Regex = Regex("Signer #\\d+ certificate SHA-256 digest:\\s*([a-fA-F0-9]+)")
        val sha1Regex = Regex("Signer #\\d+ certificate SHA-1 digest:\\s*([a-fA-F0-9]+)")
        val dnRegex = Regex("Signer #\\d+ certificate DN:\\s*(.*)")

        val sha256 = sha256Regex.find(output)?.groupValues?.get(1) ?: ""
        val sha1 = sha1Regex.find(output)?.groupValues?.get(1) ?: ""
        val dn = dnRegex.find(output)?.groupValues?.get(1) ?: ""

        if (sha256.isNotEmpty() || dn.isNotEmpty()) {
            certs.add(
                CertificateInfo(
                    alias = "signer",
                    subjectDN = dn,
                    issuerDN = "",
                    serialNumber = "",
                    validFrom = "",
                    validUntil = "",
                    sha256Fingerprint = sha256.chunked(2).joinToString(":"),
                    sha1Fingerprint = sha1.chunked(2).joinToString(":"),
                    signatureAlgorithm = ""
                )
            )
        }

        return certs
    }

    private fun fingerprint(certBytes: ByteArray, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        return md.digest(certBytes).joinToString(":") { "%02X".format(it) }
    }
}
