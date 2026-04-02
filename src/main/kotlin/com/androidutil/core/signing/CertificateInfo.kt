package com.androidutil.core.signing

data class CertificateInfo(
    val alias: String,
    val subjectDN: String,
    val issuerDN: String,
    val serialNumber: String,
    val validFrom: String,
    val validUntil: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val signatureAlgorithm: String
)

data class SignatureVerificationResult(
    val isValid: Boolean,
    val v1SchemeValid: Boolean?,
    val v2SchemeValid: Boolean?,
    val v3SchemeValid: Boolean?,
    val v4SchemeValid: Boolean?,
    val certificates: List<CertificateInfo>,
    val errors: List<String>
)

data class KeystoreInfo(
    val type: String,
    val provider: String,
    val entries: List<CertificateInfo>
)
