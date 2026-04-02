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
import com.github.ajalt.mordant.terminal.Terminal

class JsonRenderer(private val terminal: Terminal) : OutputRenderer {

    override fun renderAabAnalysis(result: AabAnalysisResult) {
        val json = buildString {
            appendLine("{")
            appendLine("""  "type": "aab",""")
            appendLine("""  "filePath": "${escape(result.filePath)}",""")
            appendLine("""  "fileSizeBytes": ${result.fileSizeBytes},""")
            appendLine("""  "estimatedSize": ${result.estimatedSize},""")
            appendLine("""  "manifest": {""")
            appendLine("""    "packageName": "${escape(result.manifestInfo.packageName)}",""")
            appendLine("""    "versionCode": ${result.manifestInfo.versionCode},""")
            appendLine("""    "versionName": "${escape(result.manifestInfo.versionName)}",""")
            appendLine("""    "minSdk": ${result.manifestInfo.minSdk},""")
            appendLine("""    "targetSdk": ${result.manifestInfo.targetSdk},""")
            appendLine("""    "permissions": [${result.manifestInfo.permissions.joinToString(", ") { "\"${escape(it)}\"" }}]""")
            appendLine("""  },""")
            val results64bit = result.alignmentResults.filter { it.elfClass == 64 }
            appendLine("""  "alignment": {""")
            appendLine("""    "allCompatible": ${results64bit.isEmpty() || results64bit.all { it.isCompatible }},""")
            appendLine("""    "libraries": [${jsonArray(results64bit) { """{"path": "${escape(it.libraryPath)}", "abi": "${it.abi}", "compatible": ${it.isCompatible}}""" }}]""")
            appendLine("""  },""")
            appendLine("""  "certificates": [${jsonArray(result.certificates) { """{"sha256": "${escape(it.sha256Fingerprint)}", "subject": "${escape(it.subjectDN)}"}""" }}]""")
            appendLine("}")
        }
        terminal.println(json)
    }

    override fun renderApkAnalysis(result: ApkAnalysisResult) {
        val json = buildString {
            appendLine("{")
            appendLine("""  "type": "apk",""")
            appendLine("""  "filePath": "${escape(result.filePath)}",""")
            appendLine("""  "fileSizeBytes": ${result.fileSizeBytes},""")
            appendLine("""  "manifest": {""")
            appendLine("""    "packageName": "${escape(result.manifestInfo.packageName)}",""")
            appendLine("""    "versionCode": ${result.manifestInfo.versionCode},""")
            appendLine("""    "versionName": "${escape(result.manifestInfo.versionName)}",""")
            appendLine("""    "minSdk": ${result.manifestInfo.minSdk},""")
            appendLine("""    "targetSdk": ${result.manifestInfo.targetSdk},""")
            appendLine("""    "permissions": [${result.manifestInfo.permissions.joinToString(", ") { "\"${escape(it)}\"" }}]""")
            appendLine("""  },""")
            appendLine("""  "sizeBreakdown": {"totalBytes": ${result.sizeBreakdown.totalBytes}, "dexBytes": ${result.sizeBreakdown.dexBytes}, "resourceBytes": ${result.sizeBreakdown.resourceBytes}, "nativeLibBytes": ${result.sizeBreakdown.nativeLibBytes}, "assetBytes": ${result.sizeBreakdown.assetBytes}, "otherBytes": ${result.sizeBreakdown.otherBytes}},""")
            appendLine("""  "dexFiles": [${jsonArray(result.dexFiles) { """{"name": "${it.name}", "methods": ${it.methodCount}, "classes": ${it.classCount}}""" }}],""")
            appendLine("""  "alignment": {"allCompatible": ${result.alignmentResults.all { it.isCompatible }}, "libraries": [${jsonArray(result.alignmentResults) { """{"path": "${escape(it.libraryPath)}", "abi": "${it.abi}", "compatible": ${it.isCompatible}}""" }}]}""")
            appendLine("}")
        }
        terminal.println(json)
    }

    override fun renderSignatureVerification(result: SignatureVerificationResult) {
        val json = """{"valid": ${result.isValid}, "v1": ${result.v1SchemeValid}, "v2": ${result.v2SchemeValid}, "v3": ${result.v3SchemeValid}, "v4": ${result.v4SchemeValid}, "errors": [${result.errors.joinToString(", ") { "\"${escape(it)}\"" }}]}"""
        terminal.println(json)
    }

    override fun renderKeystoreInfo(result: KeystoreInfo) {
        val json = buildString {
            appendLine("{")
            appendLine("""  "type": "${result.type}",""")
            appendLine("""  "provider": "${result.provider}",""")
            appendLine("""  "entries": [${jsonArray(result.entries) { """{"alias": "${escape(it.alias)}", "sha256": "${escape(it.sha256Fingerprint)}", "validUntil": "${it.validUntil}"}""" }}]""")
            appendLine("}")
        }
        terminal.println(json)
    }

    override fun renderDeeplinkAnalysis(result: DeeplinkAnalysisResult) {
        val json = buildString {
            appendLine("{")
            appendLine("""  "totalCount": ${result.totalCount},""")
            appendLine("""  "appLinks": [${jsonArray(result.appLinks) { """{"url": "${escape(it.fullUrl)}", "activity": "${escape(it.activityName)}", "autoVerify": ${it.autoVerify}}""" }}],""")
            appendLine("""  "customSchemes": [${jsonArray(result.customSchemes) { """{"url": "${escape(it.fullUrl)}", "activity": "${escape(it.activityName)}"}""" }}],""")
            appendLine("""  "deeplinks": [${jsonArray(result.deeplinks) { """{"url": "${escape(it.fullUrl)}", "scheme": "${escape(it.scheme)}", "host": "${escape(it.host)}", "activity": "${escape(it.activityName)}", "autoVerify": ${it.autoVerify}}""" }}]""")
            appendLine("}")
        }
        terminal.println(json)
    }

    override fun renderPermissionDiff(result: PermissionDiffResult) {
        val json = buildString {
            appendLine("{")
            appendLine("""  "oldFile": "${escape(result.oldFile)}",""")
            appendLine("""  "newFile": "${escape(result.newFile)}",""")
            appendLine("""  "added": [${result.added.joinToString(", ") { "\"${escape(it)}\"" }}],""")
            appendLine("""  "removed": [${result.removed.joinToString(", ") { "\"${escape(it)}\"" }}],""")
            appendLine("""  "unchanged": [${result.unchanged.joinToString(", ") { "\"${escape(it)}\"" }}]""")
            appendLine("}")
        }
        terminal.println(json)
    }

    override fun renderDecodedStackTrace(result: DecodedStackTrace) {
        val json = buildString {
            appendLine("{")
            appendLine("""  "mappingsApplied": ${result.mappingsApplied},""")
            appendLine("""  "decodedLines": [${result.decodedLines.joinToString(", ") { "\"${escape(it)}\"" }}]""")
            appendLine("}")
        }
        terminal.println(json)
    }

    override fun renderSizeDiff(result: SizeDiffResult) {
        val json = buildString {
            appendLine("{")
            appendLine("""  "oldFile": "${escape(result.oldFile)}", "newFile": "${escape(result.newFile)}",""")
            appendLine("""  "oldTotalBytes": ${result.oldTotalBytes}, "newTotalBytes": ${result.newTotalBytes}, "totalDiffBytes": ${result.totalDiffBytes},""")
            appendLine("""  "categoryDiffs": [${jsonArray(result.categoryDiffs) { """{"category": "${it.category}", "oldBytes": ${it.oldBytes}, "newBytes": ${it.newBytes}, "diffBytes": ${it.diffBytes}}""" }}],""")
            appendLine("""  "topIncreases": [${jsonArray(result.topIncreases) { """{"path": "${escape(it.path)}", "diffBytes": ${it.diffBytes}}""" }}],""")
            appendLine("""  "topDecreases": [${jsonArray(result.topDecreases) { """{"path": "${escape(it.path)}", "diffBytes": ${it.diffBytes}}""" }}]""")
            appendLine("}")
        }
        terminal.println(json)
    }

    override fun renderNativeLibReport(result: NativeLibReport) {
        val json = buildString {
            appendLine("{")
            appendLine("""  "totalLibs": ${result.totalLibs}, "totalSizeBytes": ${result.totalSizeBytes},""")
            appendLine("""  "allPageAligned": ${result.allPageAligned},""")
            appendLine("""  "libraries": [${jsonArray(result.libraries) { """{"path": "${escape(it.path)}", "abi": "${it.abi}", "sizeBytes": ${it.sizeBytes}, "stripped": ${it.isStripped}, "pageAligned": ${it.pageAligned}}""" }}]""")
            appendLine("}")
        }
        terminal.println(json)
    }

    override fun renderPlayCompat(result: PlayCompatResult) {
        val json = buildString {
            appendLine("{")
            appendLine("""  "isReady": ${result.isReady}, "pass": ${result.passCount}, "fail": ${result.failCount}, "warn": ${result.warnCount},""")
            appendLine("""  "checks": [${jsonArray(result.checks) { """{"name": "${escape(it.name)}", "status": "${it.status}", "detail": "${escape(it.detail)}"}""" }}]""")
            appendLine("}")
        }
        terminal.println(json)
    }

    override fun renderResourceReport(result: ResourceReport) {
        val json = buildString {
            appendLine("{")
            appendLine("""  "totalResources": ${result.totalResources}, "totalBytes": ${result.totalBytes},""")
            appendLine("""  "categories": [${jsonArray(result.categories) { """{"name": "${escape(it.name)}", "fileCount": ${it.fileCount}, "totalBytes": ${it.totalBytes}}""" }}],""")
            appendLine("""  "largestFiles": [${jsonArray(result.largestFiles) { """{"path": "${escape(it.path)}", "compressedBytes": ${it.compressedBytes}, "uncompressedBytes": ${it.uncompressedBytes}}""" }}]""")
            appendLine("}")
        }
        terminal.println(json)
    }

    override fun renderManifestDiff(result: ManifestDiffResult) {
        val json = buildString {
            appendLine("{")
            appendLine("""  "oldFile": "${escape(result.oldFile)}", "newFile": "${escape(result.newFile)}",""")
            appendLine("""  "changes": [${jsonArray(result.changes) { """{"field": "${escape(it.field)}", "oldValue": "${escape(it.oldValue)}", "newValue": "${escape(it.newValue)}"}""" }}],""")
            appendLine("""  "permissionsAdded": [${result.permissionChanges.added.joinToString(", ") { "\"${escape(it)}\"" }}],""")
            appendLine("""  "permissionsRemoved": [${result.permissionChanges.removed.joinToString(", ") { "\"${escape(it)}\"" }}]""")
            appendLine("}")
        }
        terminal.println(json)
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun <T> jsonArray(items: List<T>, transform: (T) -> String): String =
        items.joinToString(", ") { transform(it) }
}
