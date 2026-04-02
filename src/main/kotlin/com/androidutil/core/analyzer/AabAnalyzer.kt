package com.androidutil.core.analyzer

import com.androidutil.core.elf.AlignmentCheckResult
import com.androidutil.core.elf.ElfParser
import com.androidutil.core.manifest.ManifestParser
import com.androidutil.core.signing.CertificateInfo
import com.androidutil.util.ZipUtils
import java.nio.file.Path
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import kotlin.io.path.fileSize

class AabAnalyzer {

    private val elfParser = ElfParser()
    private val manifestParser = ManifestParser()

    fun analyze(aabPath: Path): AabAnalysisResult {
        val fileSizeBytes = aabPath.fileSize()
        val entries = ZipUtils.listEntries(aabPath)

        // Manifest info
        val manifestInfo = manifestParser.parseFromAab(aabPath)

        // Certificates from META-INF
        val certificates = extractCertificates(aabPath)

        // Native libraries (in AAB: base/lib/arm64-v8a/xxx.so)
        val nativeLibs = entries.filter {
            it.name.contains("/lib/") && it.name.endsWith(".so")
        }.map { entry ->
            NativeLibInfo(
                path = entry.name,
                abi = extractAbiFromAab(entry.name),
                sizeBytes = entry.uncompressedSize
            )
        }

        // 16KB alignment check for native libs
        val alignmentResults = checkNativeAlignment(aabPath)

        // Estimated download size (compressed sizes total)
        val estimatedSize = entries.sumOf { it.compressedSize }

        return AabAnalysisResult(
            filePath = aabPath.toString(),
            fileSizeBytes = fileSizeBytes,
            manifestInfo = manifestInfo,
            certificates = certificates,
            nativeLibraries = nativeLibs,
            alignmentResults = alignmentResults,
            estimatedSize = estimatedSize
        )
    }

    private fun extractCertificates(aabPath: Path): List<CertificateInfo> {
        val entries = ZipUtils.listEntries(aabPath)
        val certEntries = entries.filter {
            (it.name.startsWith("META-INF/") || it.name.contains("/META-INF/")) &&
            (it.name.endsWith(".RSA") || it.name.endsWith(".DSA") || it.name.endsWith(".EC"))
        }

        val certs = mutableListOf<CertificateInfo>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        for (certEntry in certEntries) {
            try {
                val certBytes = ZipUtils.readEntry(aabPath, certEntry.name) ?: continue
                val cf = CertificateFactory.getInstance("X.509")
                val pkcs7Certs = try {
                    cf.generateCertificates(certBytes.inputStream())
                } catch (e: Exception) {
                    continue
                }

                for (cert in pkcs7Certs) {
                    if (cert is X509Certificate) {
                        val sha256 = fingerprint(cert.encoded, "SHA-256")
                        val sha1 = fingerprint(cert.encoded, "SHA-1")

                        certs.add(
                            CertificateInfo(
                                alias = certEntry.name.substringAfterLast('/').substringBeforeLast('.'),
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
            } catch (e: Exception) {
                // Skip unreadable certificate entries
            }
        }

        return certs
    }

    private fun fingerprint(certBytes: ByteArray, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        val digest = md.digest(certBytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    private fun checkNativeAlignment(aabPath: Path): List<AlignmentCheckResult> {
        return ZipUtils.withMatchingEntries(
            aabPath,
            Regex(".*/lib/[^/]+/.*\\.so")
        ) { name, stream, _ ->
            elfParser.checkAlignment(stream, name)
        }
    }

    private fun extractAbiFromAab(path: String): String {
        // AAB path: base/lib/arm64-v8a/libfoo.so
        val parts = path.split('/')
        val libIndex = parts.indexOf("lib")
        return if (libIndex >= 0 && libIndex + 1 < parts.size) {
            parts[libIndex + 1]
        } else {
            "unknown"
        }
    }
}
