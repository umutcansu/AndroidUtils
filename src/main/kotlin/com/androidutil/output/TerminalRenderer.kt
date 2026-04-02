package com.androidutil.output

import com.androidutil.core.analyzer.AabAnalysisResult
import com.androidutil.core.analyzer.ApkAnalysisResult
import com.androidutil.core.analyzer.SizeBreakdown
import com.androidutil.core.deeplink.DeeplinkAnalysisResult
import com.androidutil.core.deeplink.DeeplinkInfo
import com.androidutil.core.dex.DexFileInfo
import com.androidutil.core.diff.ManifestDiffResult
import com.androidutil.core.diff.PermissionDiffResult
import com.androidutil.core.diff.SizeDiffResult
import com.androidutil.core.elf.AlignmentCheckResult
import com.androidutil.core.manifest.ManifestInfo
import com.androidutil.core.nativelib.NativeLibReport
import com.androidutil.core.playcompat.CheckStatus
import com.androidutil.core.playcompat.PlayCompatResult
import com.androidutil.core.resources.ResourceReport
import com.androidutil.core.signing.CertificateInfo
import com.androidutil.core.signing.KeystoreInfo
import com.androidutil.core.signing.SignatureVerificationResult
import com.androidutil.core.stacktrace.DecodedStackTrace
import com.androidutil.i18n.Messages
import com.androidutil.util.FileSize
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal

class TerminalRenderer(private val terminal: Terminal, private val msg: Messages) : OutputRenderer {

    override fun renderAabAnalysis(result: AabAnalysisResult) {
        terminal.println()
        terminal.println(bold(msg.get("terminal.aabAnalysis", result.filePath)))
        terminal.println(msg.get("terminal.fileSize", FileSize.formatDetailed(result.fileSizeBytes)))
        terminal.println(msg.get("terminal.estimatedDownloadSize", FileSize.format(result.estimatedSize)))
        terminal.println()

        renderManifest(result.manifestInfo)
        renderCertificates(result.certificates)
        renderAlignment(result.alignmentResults)
        renderPermissions(result.manifestInfo.permissions)
    }

    override fun renderApkAnalysis(result: ApkAnalysisResult) {
        terminal.println()
        terminal.println(bold(msg.get("terminal.apkAnalysis", result.filePath)))
        terminal.println(msg.get("terminal.fileSize", FileSize.formatDetailed(result.fileSizeBytes)))
        terminal.println()

        renderManifest(result.manifestInfo)
        renderSizeBreakdown(result.sizeBreakdown)
        renderDexFiles(result.dexFiles)
        renderAlignment(result.alignmentResults)

        if (result.signatures != null) {
            renderSignatureVerification(result.signatures)
        }

        renderPermissions(result.manifestInfo.permissions)
    }

    override fun renderSignatureVerification(result: SignatureVerificationResult) {
        terminal.println()
        terminal.println(bold(msg["terminal.signatureVerification"]))

        val statusText = if (result.isValid) green(msg["terminal.valid"]) else red(msg["terminal.invalid"])
        terminal.println("${msg["common.status"]}: $statusText")

        terminal.println(table {
            header { row(msg["terminal.scheme"], msg["common.status"]) }
            body {
                row("v1 (JAR)", schemeStatus(result.v1SchemeValid))
                row("v2 (APK Imza)", schemeStatus(result.v2SchemeValid))
                row("v3 (APK Imza v3)", schemeStatus(result.v3SchemeValid))
                row("v4 (APK Imza v4)", schemeStatus(result.v4SchemeValid))
            }
        })

        if (result.certificates.isNotEmpty()) {
            renderCertificates(result.certificates)
        }

        if (result.errors.isNotEmpty()) {
            terminal.println()
            terminal.println(red(bold(msg["terminal.errors"])))
            result.errors.forEach { terminal.println(red("  - $it")) }
        }
    }

    override fun renderKeystoreInfo(result: KeystoreInfo) {
        terminal.println()
        terminal.println(bold(msg["terminal.keystoreInfo"]))
        terminal.println(msg.get("terminal.type", result.type))
        terminal.println(msg.get("terminal.provider", result.provider))
        terminal.println(msg.get("terminal.entryCount", result.entries.size))
        terminal.println()

        for (entry in result.entries) {
            terminal.println(bold(msg.get("terminal.alias", entry.alias)))
            terminal.println(table {
                body {
                    row(msg["terminal.subject"], entry.subjectDN)
                    row(msg["terminal.issuer"], entry.issuerDN)
                    row(msg["terminal.serialNumber"], entry.serialNumber)
                    row(msg["terminal.validFrom"], entry.validFrom)
                    row(msg["terminal.validUntil"], entry.validUntil)
                    row("SHA-256", entry.sha256Fingerprint)
                    row("SHA-1", entry.sha1Fingerprint)
                    row(msg["terminal.algorithm"], entry.signatureAlgorithm)
                }
            })
            terminal.println()
        }
    }

    private fun renderManifest(info: ManifestInfo) {
        terminal.println(bold(msg["terminal.manifest"]))
        terminal.println(table {
            body {
                row(msg["terminal.package"], info.packageName)
                row(msg["terminal.version"], "${info.versionName} (${info.versionCode})")
                row("Min SDK", info.minSdk.toString())
                row("Target SDK", info.targetSdk.toString())
                if (info.compileSdk != null) {
                    row("Compile SDK", info.compileSdk.toString())
                }
                row("Activity", info.activities.toString())
                row("Service", info.services.toString())
                row("Receiver", info.receivers.toString())
                row("Provider", info.providers.toString())
            }
        })
        terminal.println()
    }

    private fun renderSizeBreakdown(breakdown: SizeBreakdown) {
        terminal.println(bold(msg["terminal.sizeBreakdown"]))
        terminal.println(table {
            header { row(msg["terminal.component"], msg["common.size"], msg["terminal.percentage"]) }
            body {
                row("DEX", FileSize.format(breakdown.dexBytes), FileSize.percentage(breakdown.dexBytes, breakdown.totalBytes))
                row(msg["terminal.resources"], FileSize.format(breakdown.resourceBytes), FileSize.percentage(breakdown.resourceBytes, breakdown.totalBytes))
                row(msg["terminal.nativeLibraries"], FileSize.format(breakdown.nativeLibBytes), FileSize.percentage(breakdown.nativeLibBytes, breakdown.totalBytes))
                row("Asset", FileSize.format(breakdown.assetBytes), FileSize.percentage(breakdown.assetBytes, breakdown.totalBytes))
                row(msg["terminal.other"], FileSize.format(breakdown.otherBytes), FileSize.percentage(breakdown.otherBytes, breakdown.totalBytes))
                row(bold(msg["terminal.total"]), bold(FileSize.format(breakdown.totalBytes)), bold("100.0%"))
            }
        })
        terminal.println()
    }

    private fun renderDexFiles(dexFiles: List<DexFileInfo>) {
        if (dexFiles.isEmpty()) return

        val totalMethods = dexFiles.sumOf { it.methodCount }
        val totalClasses = dexFiles.sumOf { it.classCount }

        terminal.println(bold(msg.get("terminal.dexFiles", dexFiles.size, totalMethods, totalClasses)))

        if (dexFiles.size > 1) {
            terminal.println(yellow(msg["terminal.multidexDetected"]))
        }

        terminal.println(table {
            header { row(msg["terminal.dexFile"], msg["common.size"], msg["terminal.method"], msg["terminal.class"]) }
            body {
                dexFiles.forEach { dex ->
                    row(dex.name, FileSize.format(dex.sizeBytes), dex.methodCount.toString(), dex.classCount.toString())
                }
            }
        })
        terminal.println()
    }

    private fun renderAlignment(results: List<AlignmentCheckResult>) {
        if (results.isEmpty()) {
            terminal.println(bold(msg["terminal.pageAlignment"]))
            terminal.println(msg["terminal.noNativeLib"])
            terminal.println()
            return
        }

        // Google Play only requires 16KB alignment for 64-bit libraries
        val results64bit = results.filter { it.elfClass == 64 }
        val results32bit = results.filter { it.elfClass == 32 }
        val allCompatible = results64bit.isEmpty() || results64bit.all { it.isCompatible }
        val statusText = if (allCompatible) green(msg["terminal.allCompatible"]) else red(msg["terminal.incompatibleFound"])

        terminal.println(bold("${msg["terminal.pageAlignment"]}: $statusText"))

        if (results64bit.isNotEmpty()) {
            terminal.println(table {
                header { row(msg["terminal.library"], msg["terminal.abi"], msg["terminal.elf"], msg["terminal.segment"], msg["terminal.minAlignment"], msg["terminal.compatible"]) }
                body {
                    results64bit.forEach { result ->
                        val minAlign = result.ptLoadSegments.minOfOrNull { it.pAlign } ?: 0L
                        val compatText = if (result.isCompatible) green(msg["common.yes"]) else red(msg["common.no"])
                        row(
                            result.libraryPath.substringAfterLast('/'),
                            result.abi,
                            "${result.elfClass}-bit",
                            result.ptLoadSegments.size.toString(),
                            minAlign.toString(),
                            compatText
                        )
                    }
                }
            })
        }

        if (results32bit.isNotEmpty()) {
            terminal.println(gray(msg.get("terminal.32bitNote", results32bit.size)))
        }

        // Show detailed segment info for incompatible 64-bit libraries
        val incompatible = results64bit.filter { !it.isCompatible }
        if (incompatible.isNotEmpty()) {
            terminal.println()
            terminal.println(red(bold(msg["terminal.incompatibleDetails"])))
            for (lib in incompatible) {
                terminal.println(red("  ${lib.libraryPath}:"))
                for (seg in lib.ptLoadSegments) {
                    val status = if (seg.compatible) green("OK") else red(msg["terminal.segmentFailed"])
                    terminal.println("    Segment ${seg.segmentIndex}: p_align=${seg.pAlign} $status")
                }
            }
            terminal.println()
            terminal.println(yellow(msg["terminal.fixHint"]))
        }
        terminal.println()
    }

    private fun renderCertificates(certs: List<CertificateInfo>) {
        if (certs.isEmpty()) return

        terminal.println(bold(msg["terminal.certificates"]))
        for (cert in certs) {
            terminal.println(table {
                body {
                    row(msg["terminal.subject"], cert.subjectDN)
                    if (cert.issuerDN.isNotEmpty()) row(msg["terminal.issuer"], cert.issuerDN)
                    if (cert.validFrom.isNotEmpty()) row(msg["terminal.validFrom"], cert.validFrom)
                    if (cert.validUntil.isNotEmpty()) row(msg["terminal.validUntil"], cert.validUntil)
                    row("SHA-256", cert.sha256Fingerprint)
                    if (cert.sha1Fingerprint.isNotEmpty()) row("SHA-1", cert.sha1Fingerprint)
                    if (cert.signatureAlgorithm.isNotEmpty()) row(msg["terminal.algorithm"], cert.signatureAlgorithm)
                }
            })
        }
        terminal.println()
    }

    private fun renderPermissions(permissions: List<String>) {
        if (permissions.isEmpty()) return

        terminal.println(bold(msg.get("terminal.permissions", permissions.size)))
        permissions.sorted().forEach { perm ->
            val shortName = perm.substringAfterLast('.')
            terminal.println("  - $shortName")
        }
        terminal.println()
    }

    override fun renderDeeplinkAnalysis(result: DeeplinkAnalysisResult) {
        terminal.println()
        terminal.println(bold(msg.get("terminal.deeplinkAnalysis", result.filePath)))
        terminal.println(msg.get("terminal.totalDeeplinks", result.totalCount))
        terminal.println()

        if (result.appLinks.isNotEmpty()) {
            terminal.println(bold(green(msg.get("terminal.appLinks", result.appLinks.size))))
            terminal.println(table {
                header { row(msg["terminal.url"], msg["terminal.activity"], msg["terminal.autoVerify"]) }
                body {
                    result.appLinks.forEach { link ->
                        row(cyan(link.fullUrl).toString(), link.activityName.substringAfterLast('.'), green(msg["common.yes"]).toString())
                    }
                }
            })
            terminal.println()
        }

        if (result.customSchemes.isNotEmpty()) {
            terminal.println(bold(yellow(msg.get("terminal.customSchemes", result.customSchemes.size))))
            terminal.println(table {
                header { row(msg["terminal.url"], msg["terminal.activity"]) }
                body {
                    result.customSchemes.forEach { link ->
                        row(cyan(link.fullUrl).toString(), link.activityName.substringAfterLast('.'))
                    }
                }
            })
            terminal.println()
        }

        val httpLinks = result.deeplinks.filter {
            (it.scheme == "https" || it.scheme == "http") && !it.autoVerify
        }
        if (httpLinks.isNotEmpty()) {
            terminal.println(bold(msg.get("terminal.httpDeeplinks", httpLinks.size)))
            terminal.println(table {
                header { row(msg["terminal.url"], msg["terminal.activity"]) }
                body {
                    httpLinks.forEach { link ->
                        row(link.fullUrl, link.activityName.substringAfterLast('.'))
                    }
                }
            })
            terminal.println()
        }

        if (result.deeplinks.isEmpty()) {
            terminal.println(yellow(msg["terminal.noDeeplinks"]))
            terminal.println()
        }

        // ADB test commands
        if (result.deeplinks.isNotEmpty()) {
            terminal.println(bold(msg["terminal.adbTestHint"]))
            val sample = result.deeplinks.first()
            terminal.println(gray("  adb shell am start -a android.intent.action.VIEW -d '${sample.fullUrl}'"))
            terminal.println()
        }
    }

    override fun renderPermissionDiff(result: PermissionDiffResult) {
        terminal.println()
        terminal.println(bold(msg["terminal.permissionDiff"]))
        terminal.println(msg.get("terminal.old", result.oldFile, result.oldTotal))
        terminal.println(msg.get("terminal.new", result.newFile, result.newTotal))
        terminal.println()

        if (result.added.isNotEmpty()) {
            terminal.println(green(bold(msg.get("terminal.added", result.added.size))))
            result.added.forEach { perm ->
                terminal.println(green("  + ${perm.substringAfterLast('.')}"))
                terminal.println(gray("    $perm"))
            }
            terminal.println()
        }

        if (result.removed.isNotEmpty()) {
            terminal.println(red(bold(msg.get("terminal.removed", result.removed.size))))
            result.removed.forEach { perm ->
                terminal.println(red("  - ${perm.substringAfterLast('.')}"))
                terminal.println(gray("    $perm"))
            }
            terminal.println()
        }

        if (result.added.isEmpty() && result.removed.isEmpty()) {
            terminal.println(green(msg["terminal.noDiff"]))
            terminal.println()
        }

        terminal.println(gray(msg.get("terminal.unchanged", result.unchanged.size)))
        terminal.println()
    }

    override fun renderDecodedStackTrace(result: DecodedStackTrace) {
        terminal.println()
        terminal.println(bold(msg["terminal.decodedStackTrace"]))
        terminal.println(msg.get("terminal.mappingsApplied", result.mappingsApplied))
        terminal.println()

        for ((i, line) in result.decodedLines.withIndex()) {
            val original = result.originalLines[i]
            if (line != original) {
                terminal.println(green(line))
            } else {
                terminal.println(line)
            }
        }
        terminal.println()
    }

    override fun renderSizeDiff(result: SizeDiffResult) {
        terminal.println()
        terminal.println(bold(msg["terminal.sizeDiff"]))
        terminal.println(msg.get("terminal.oldFile", result.oldFile, FileSize.format(result.oldTotalBytes)))
        terminal.println(msg.get("terminal.newFile", result.newFile, FileSize.format(result.newTotalBytes)))
        val diffColor = if (result.totalDiffBytes > 0) red else green
        val diffSign = if (result.totalDiffBytes > 0) "+" else ""
        val pctChange = if (result.oldTotalBytes > 0) {
            val pct = (result.totalDiffBytes.toDouble() / result.oldTotalBytes * 100)
            " (${if (pct > 0) "+" else ""}${"%.1f".format(pct)}%)"
        } else ""
        terminal.println(msg.get("terminal.diff", diffColor("$diffSign${FileSize.format(result.totalDiffBytes)}$pctChange")))
        terminal.println()

        terminal.println(bold(msg["terminal.categoryBreakdown"]))
        val oldLabel = msg["terminal.oldFile"].substringBefore(":")
        val newLabel = msg["terminal.newFile"].substringBefore(":")
        terminal.println(table {
            header { row(msg["terminal.category"], oldLabel, newLabel, msg["terminal.diff"].substringBefore(":")) }
            body {
                result.categoryDiffs.forEach { cat ->
                    val catDiffColor = if (cat.diffBytes > 0) red else if (cat.diffBytes < 0) green else white
                    val catSign = if (cat.diffBytes > 0) "+" else ""
                    row(cat.category.uppercase(), FileSize.format(cat.oldBytes), FileSize.format(cat.newBytes),
                        catDiffColor("$catSign${FileSize.format(cat.diffBytes)}").toString())
                }
            }
        })
        terminal.println()

        if (result.topIncreases.isNotEmpty()) {
            terminal.println(red(bold(msg["terminal.topIncreases"])))
            terminal.println(table {
                header { row(msg["common.file"], oldLabel, newLabel, msg["terminal.diff"].substringBefore(":")) }
                body {
                    result.topIncreases.forEach { e ->
                        row(e.path.substringAfterLast('/'), FileSize.format(e.oldBytes), FileSize.format(e.newBytes), red("+${FileSize.format(e.diffBytes)}").toString())
                    }
                }
            })
            terminal.println()
        }

        if (result.topDecreases.isNotEmpty()) {
            terminal.println(green(bold(msg["terminal.topDecreases"])))
            terminal.println(table {
                header { row(msg["common.file"], oldLabel, newLabel, msg["terminal.diff"].substringBefore(":")) }
                body {
                    result.topDecreases.forEach { e ->
                        row(e.path.substringAfterLast('/'), FileSize.format(e.oldBytes), FileSize.format(e.newBytes), green(FileSize.format(e.diffBytes)).toString())
                    }
                }
            })
            terminal.println()
        }
    }

    override fun renderNativeLibReport(result: NativeLibReport) {
        terminal.println()
        terminal.println(bold(msg["terminal.nativeLibReport"]))
        terminal.println(msg.get("terminal.nativeLibTotal", result.totalLibs, FileSize.format(result.totalSizeBytes)))
        terminal.println("ABI'ler: ${result.abiSummary.entries.joinToString(", ") { "${it.key} (${it.value})" }}")
        val alignStatus = if (result.allPageAligned) green(msg["terminal.allAligned"]) else red(msg["terminal.unalignedFound"])
        terminal.println(msg.get("terminal.nativeLibAlignment", alignStatus))
        terminal.println()

        terminal.println(table {
            header { row(msg["terminal.library"], msg["terminal.abi"], msg["common.size"], msg["terminal.uncompressed"], msg["terminal.strip"], msg["terminal.elf"], "16KB") }
            body {
                result.libraries.forEach { lib ->
                    val strippedText = if (lib.isStripped) green(msg["common.yes"]) else yellow(msg["common.no"])
                    val alignText = when {
                        lib.elfClass == 32 -> gray("—")
                        lib.pageAligned -> green("OK")
                        else -> red(msg["terminal.failed"])
                    }
                    row(
                        lib.path.substringAfterLast('/'),
                        lib.abi,
                        FileSize.format(lib.sizeBytes),
                        FileSize.format(lib.uncompressedBytes),
                        strippedText.toString(),
                        "${lib.elfClass}-bit",
                        alignText.toString()
                    )
                }
            }
        })
        terminal.println()
    }

    override fun renderPlayCompat(result: PlayCompatResult) {
        terminal.println()
        val readyText = if (result.isReady) green(bold(msg["terminal.playReady"])) else red(bold(msg["terminal.playNotReady"]))
        terminal.println(bold(msg.get("terminal.playCompat", readyText)))
        terminal.println(msg.get("terminal.playStats", result.passCount, result.failCount, result.warnCount))
        terminal.println()

        terminal.println(table {
            column(1) { width = ColumnWidth(priority = 10, width = 6) }
            header { row(msg["terminal.check"], msg["common.status"], msg["common.detail"]) }
            body {
                result.checks.forEach { check ->
                    val statusText = when (check.status) {
                        CheckStatus.PASS -> green(msg["terminal.ok"])
                        CheckStatus.FAIL -> red(msg["terminal.error"])
                        CheckStatus.WARN -> yellow(msg["terminal.warning"])
                    }
                    row(check.name, statusText.toString(), check.detail)
                }
            }
        })

        // Actionable suggestions for failed checks
        val failedChecks = result.checks.filter { it.status == CheckStatus.FAIL }
        if (failedChecks.isNotEmpty()) {
            terminal.println(bold(yellow(msg["terminal.suggestions"])))
            for (check in failedChecks) {
                when {
                    check.name.contains("Target SDK") -> terminal.println(yellow("  ${msg.get("terminal.suggestTargetSdk", com.androidutil.core.playcompat.PlayCompatChecker.REQUIRED_TARGET_SDK)}"))
                    check.name.contains("16KB") -> terminal.println(yellow("  ${msg["terminal.suggestAlignment"]}"))
                    check.name.contains("64-bit") -> terminal.println(yellow("  ${msg["terminal.suggest64bit"]}"))
                }
            }
            terminal.println()
        }
    }

    override fun renderResourceReport(result: ResourceReport) {
        terminal.println()
        terminal.println(bold(msg["terminal.resourceReport"]))
        terminal.println(msg.get("terminal.resourceTotal", result.totalResources, FileSize.format(result.totalBytes)))
        terminal.println()

        terminal.println(table {
            header { row(msg["terminal.category"], msg["terminal.fileCount"], msg["common.size"]) }
            body {
                result.categories.forEach { cat ->
                    row(cat.name, cat.fileCount.toString(), FileSize.format(cat.totalBytes))
                }
            }
        })
        terminal.println()

        terminal.println(bold(msg["terminal.largestFiles"]))
        terminal.println(table {
            header { row(msg["common.file"], msg["terminal.compressed"], msg["terminal.uncompressed"]) }
            body {
                result.largestFiles.forEach { entry ->
                    row(entry.path, FileSize.format(entry.compressedBytes), FileSize.format(entry.uncompressedBytes))
                }
            }
        })
        terminal.println()
    }

    override fun renderManifestDiff(result: ManifestDiffResult) {
        terminal.println()
        terminal.println(bold(msg["terminal.manifestDiff"]))
        val oldLabel = msg["terminal.oldFile"].substringBefore(":")
        val newLabel = msg["terminal.newFile"].substringBefore(":")
        terminal.println("$oldLabel: ${result.oldFile}")
        terminal.println("$newLabel: ${result.newFile}")
        terminal.println()

        if (result.changes.isNotEmpty()) {
            terminal.println(bold(msg.get("terminal.changes", result.changes.size)))
            terminal.println(table {
                header { row(msg["terminal.field"], oldLabel, newLabel) }
                body {
                    result.changes.forEach { change ->
                        row(change.field, red(change.oldValue).toString(), green(change.newValue).toString())
                    }
                }
            })
            terminal.println()
        } else {
            terminal.println(green(msg["terminal.manifestSame"]))
            terminal.println()
        }

        val perms = result.permissionChanges
        if (perms.added.isNotEmpty()) {
            terminal.println(green(bold(msg.get("terminal.addedPermissions", perms.added.size))))
            perms.added.forEach { terminal.println(green("  + ${it.substringAfterLast('.')}")) }
            terminal.println()
        }
        if (perms.removed.isNotEmpty()) {
            terminal.println(red(bold(msg.get("terminal.removedPermissions", perms.removed.size))))
            perms.removed.forEach { terminal.println(red("  - ${it.substringAfterLast('.')}")) }
            terminal.println()
        }
        if (perms.added.isEmpty() && perms.removed.isEmpty()) {
            terminal.println(green(msg["terminal.permissionsUnchanged"]))
            terminal.println()
        }
        terminal.println(gray(msg.get("terminal.unchanged", perms.unchanged.size)))
        terminal.println()
    }

    private fun schemeStatus(valid: Boolean?): String {
        return when (valid) {
            true -> green(msg["terminal.verified"]).toString()
            false -> red(msg["terminal.notVerified"]).toString()
            null -> "N/A"
        }
    }
}
