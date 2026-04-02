package com.androidutil.output

import com.androidutil.core.analyzer.AabAnalysisResult
import com.androidutil.core.analyzer.ApkAnalysisResult
import com.androidutil.core.deeplink.DeeplinkAnalysisResult
import com.androidutil.core.diff.ManifestDiffResult
import com.androidutil.core.diff.PermissionDiffResult
import com.androidutil.core.diff.SizeDiffResult
import com.androidutil.core.nativelib.NativeLibReport
import com.androidutil.core.playcompat.PlayCompatResult
import com.androidutil.core.resources.ResourceReport
import com.androidutil.core.signing.KeystoreInfo
import com.androidutil.core.signing.SignatureVerificationResult
import com.androidutil.core.stacktrace.DecodedStackTrace

interface OutputRenderer {
    fun renderAabAnalysis(result: AabAnalysisResult)
    fun renderApkAnalysis(result: ApkAnalysisResult)
    fun renderSignatureVerification(result: SignatureVerificationResult)
    fun renderKeystoreInfo(result: KeystoreInfo)
    fun renderDeeplinkAnalysis(result: DeeplinkAnalysisResult)
    fun renderPermissionDiff(result: PermissionDiffResult)
    fun renderDecodedStackTrace(result: DecodedStackTrace)
    fun renderSizeDiff(result: SizeDiffResult)
    fun renderNativeLibReport(result: NativeLibReport)
    fun renderPlayCompat(result: PlayCompatResult)
    fun renderResourceReport(result: ResourceReport)
    fun renderManifestDiff(result: ManifestDiffResult)
}
