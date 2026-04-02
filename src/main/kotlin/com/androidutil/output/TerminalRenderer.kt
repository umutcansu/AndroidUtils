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
import com.androidutil.util.FileSize
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal

class TerminalRenderer(private val terminal: Terminal) : OutputRenderer {

    override fun renderAabAnalysis(result: AabAnalysisResult) {
        terminal.println()
        terminal.println(bold("AAB Analizi: ${result.filePath}"))
        terminal.println("Dosya boyutu: ${FileSize.formatDetailed(result.fileSizeBytes)}")
        terminal.println("Tahmini indirme boyutu: ${FileSize.format(result.estimatedSize)}")
        terminal.println()

        renderManifest(result.manifestInfo)
        renderCertificates(result.certificates)
        renderAlignment(result.alignmentResults)
        renderPermissions(result.manifestInfo.permissions)
    }

    override fun renderApkAnalysis(result: ApkAnalysisResult) {
        terminal.println()
        terminal.println(bold("APK Analizi: ${result.filePath}"))
        terminal.println("Dosya boyutu: ${FileSize.formatDetailed(result.fileSizeBytes)}")
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
        terminal.println(bold("Imza Dogrulamasi"))

        val statusText = if (result.isValid) green("GECERLI") else red("GECERSIZ")
        terminal.println("Durum: $statusText")

        terminal.println(table {
            header { row("Sema", "Durum") }
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
            terminal.println(red(bold("Hatalar:")))
            result.errors.forEach { terminal.println(red("  - $it")) }
        }
    }

    override fun renderKeystoreInfo(result: KeystoreInfo) {
        terminal.println()
        terminal.println(bold("Keystore Bilgileri"))
        terminal.println("Tur: ${result.type}")
        terminal.println("Saglayici: ${result.provider}")
        terminal.println("Giris sayisi: ${result.entries.size}")
        terminal.println()

        for (entry in result.entries) {
            terminal.println(bold("Alias: ${entry.alias}"))
            terminal.println(table {
                body {
                    row("Konu", entry.subjectDN)
                    row("Veren", entry.issuerDN)
                    row("Seri No", entry.serialNumber)
                    row("Gecerlilik Baslangici", entry.validFrom)
                    row("Gecerlilik Bitisi", entry.validUntil)
                    row("SHA-256", entry.sha256Fingerprint)
                    row("SHA-1", entry.sha1Fingerprint)
                    row("Algoritma", entry.signatureAlgorithm)
                }
            })
            terminal.println()
        }
    }

    private fun renderManifest(info: ManifestInfo) {
        terminal.println(bold("Manifest"))
        terminal.println(table {
            body {
                row("Paket", info.packageName)
                row("Surum", "${info.versionName} (${info.versionCode})")
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
        terminal.println(bold("Boyut Dagilimi"))
        terminal.println(table {
            header { row("Bilesen", "Boyut", "Yuzde") }
            body {
                row("DEX", FileSize.format(breakdown.dexBytes), FileSize.percentage(breakdown.dexBytes, breakdown.totalBytes))
                row("Kaynaklar", FileSize.format(breakdown.resourceBytes), FileSize.percentage(breakdown.resourceBytes, breakdown.totalBytes))
                row("Native Kutuphaneler", FileSize.format(breakdown.nativeLibBytes), FileSize.percentage(breakdown.nativeLibBytes, breakdown.totalBytes))
                row("Asset", FileSize.format(breakdown.assetBytes), FileSize.percentage(breakdown.assetBytes, breakdown.totalBytes))
                row("Diger", FileSize.format(breakdown.otherBytes), FileSize.percentage(breakdown.otherBytes, breakdown.totalBytes))
                row(bold("Toplam"), bold(FileSize.format(breakdown.totalBytes)), bold("100.0%"))
            }
        })
        terminal.println()
    }

    private fun renderDexFiles(dexFiles: List<DexFileInfo>) {
        if (dexFiles.isEmpty()) return

        val totalMethods = dexFiles.sumOf { it.methodCount }
        val totalClasses = dexFiles.sumOf { it.classCount }

        terminal.println(bold("DEX Dosyalari (${dexFiles.size} dosya, $totalMethods metod, $totalClasses sinif)"))

        if (dexFiles.size > 1) {
            terminal.println(yellow("Multidex algilandi"))
        }

        terminal.println(table {
            header { row("Dosya", "Boyut", "Metod", "Sinif") }
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
            terminal.println(bold("16KB Sayfa Hizalamasi"))
            terminal.println("Native kutuphane bulunamadi.")
            terminal.println()
            return
        }

        // Google Play only requires 16KB alignment for 64-bit libraries
        val results64bit = results.filter { it.elfClass == 64 }
        val results32bit = results.filter { it.elfClass == 32 }
        val allCompatible = results64bit.isEmpty() || results64bit.all { it.isCompatible }
        val statusText = if (allCompatible) green("TUMU UYUMLU") else red("UYUMSUZ KUTUPHANE BULUNDU")

        terminal.println(bold("16KB Sayfa Hizalamasi: $statusText"))

        if (results64bit.isNotEmpty()) {
            terminal.println(table {
                header { row("Kutuphane", "ABI", "ELF", "Segment", "Min Hiza", "Uyumlu") }
                body {
                    results64bit.forEach { result ->
                        val minAlign = result.ptLoadSegments.minOfOrNull { it.pAlign } ?: 0L
                        val compatText = if (result.isCompatible) green("EVET") else red("HAYIR")
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
            terminal.println(gray("32-bit kutuphaneler (${results32bit.size} adet) — 16KB alignment gerekmez"))
        }

        // Show detailed segment info for incompatible 64-bit libraries
        val incompatible = results64bit.filter { !it.isCompatible }
        if (incompatible.isNotEmpty()) {
            terminal.println()
            terminal.println(red(bold("Uyumsuz Kutuphane Detaylari:")))
            for (lib in incompatible) {
                terminal.println(red("  ${lib.libraryPath}:"))
                for (seg in lib.ptLoadSegments) {
                    val status = if (seg.compatible) green("OK") else red("BASARISIZ (>= 16384 gerekli)")
                    terminal.println("    Segment ${seg.segmentIndex}: p_align=${seg.pAlign} $status")
                }
            }
            terminal.println()
            terminal.println(yellow("Duzeltme: Native kutuphaneleri -Wl,-z,max-page-size=16384 ile yeniden derleyin"))
        }
        terminal.println()
    }

    private fun renderCertificates(certs: List<CertificateInfo>) {
        if (certs.isEmpty()) return

        terminal.println(bold("Sertifikalar"))
        for (cert in certs) {
            terminal.println(table {
                body {
                    row("Konu", cert.subjectDN)
                    if (cert.issuerDN.isNotEmpty()) row("Veren", cert.issuerDN)
                    if (cert.validFrom.isNotEmpty()) row("Gecerlilik Baslangici", cert.validFrom)
                    if (cert.validUntil.isNotEmpty()) row("Gecerlilik Bitisi", cert.validUntil)
                    row("SHA-256", cert.sha256Fingerprint)
                    if (cert.sha1Fingerprint.isNotEmpty()) row("SHA-1", cert.sha1Fingerprint)
                    if (cert.signatureAlgorithm.isNotEmpty()) row("Algoritma", cert.signatureAlgorithm)
                }
            })
        }
        terminal.println()
    }

    private fun renderPermissions(permissions: List<String>) {
        if (permissions.isEmpty()) return

        terminal.println(bold("Izinler (${permissions.size})"))
        permissions.sorted().forEach { perm ->
            val shortName = perm.substringAfterLast('.')
            terminal.println("  - $shortName")
        }
        terminal.println()
    }

    override fun renderDeeplinkAnalysis(result: DeeplinkAnalysisResult) {
        terminal.println()
        terminal.println(bold("Deeplink Analizi: ${result.filePath}"))
        terminal.println("Toplam deeplink: ${result.totalCount}")
        terminal.println()

        if (result.appLinks.isNotEmpty()) {
            terminal.println(bold(green("Uygulama Baglantilari (dogrulanmis, ${result.appLinks.size})")))
            terminal.println(table {
                header { row("URL", "Activity", "Otomatik Dogrulama") }
                body {
                    result.appLinks.forEach { link ->
                        row(cyan(link.fullUrl).toString(), link.activityName.substringAfterLast('.'), green("EVET").toString())
                    }
                }
            })
            terminal.println()
        }

        if (result.customSchemes.isNotEmpty()) {
            terminal.println(bold(yellow("Ozel Semalar (${result.customSchemes.size})")))
            terminal.println(table {
                header { row("URL", "Activity") }
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
            terminal.println(bold("HTTP Deeplink'ler - otomatik dogrulama yok (${httpLinks.size})"))
            terminal.println(table {
                header { row("URL", "Activity") }
                body {
                    httpLinks.forEach { link ->
                        row(link.fullUrl, link.activityName.substringAfterLast('.'))
                    }
                }
            })
            terminal.println()
        }

        if (result.deeplinks.isEmpty()) {
            terminal.println(yellow("Deeplink bulunamadi."))
            terminal.println()
        }

        // ADB test commands
        if (result.deeplinks.isNotEmpty()) {
            terminal.println(bold("ADB ile test et:"))
            val sample = result.deeplinks.first()
            terminal.println(gray("  adb shell am start -a android.intent.action.VIEW -d '${sample.fullUrl}'"))
            terminal.println()
        }
    }

    override fun renderPermissionDiff(result: PermissionDiffResult) {
        terminal.println()
        terminal.println(bold("Izin Farki"))
        terminal.println("Eski: ${result.oldFile} (${result.oldTotal} izin)")
        terminal.println("Yeni: ${result.newFile} (${result.newTotal} izin)")
        terminal.println()

        if (result.added.isNotEmpty()) {
            terminal.println(green(bold("+ Eklenen (${result.added.size}):")))
            result.added.forEach { perm ->
                terminal.println(green("  + ${perm.substringAfterLast('.')}"))
                terminal.println(gray("    $perm"))
            }
            terminal.println()
        }

        if (result.removed.isNotEmpty()) {
            terminal.println(red(bold("- Kaldirilan (${result.removed.size}):")))
            result.removed.forEach { perm ->
                terminal.println(red("  - ${perm.substringAfterLast('.')}"))
                terminal.println(gray("    $perm"))
            }
            terminal.println()
        }

        if (result.added.isEmpty() && result.removed.isEmpty()) {
            terminal.println(green("Fark yok — izinler ayni."))
            terminal.println()
        }

        terminal.println(gray("Degismeyen: ${result.unchanged.size} izin"))
        terminal.println()
    }

    override fun renderDecodedStackTrace(result: DecodedStackTrace) {
        terminal.println()
        terminal.println(bold("Cozulmus Stack Trace"))
        terminal.println("Uygulanan esleme: ${result.mappingsApplied}")
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
        terminal.println(bold("Boyut Farki"))
        terminal.println("Eski: ${result.oldFile} (${FileSize.format(result.oldTotalBytes)})")
        terminal.println("Yeni: ${result.newFile} (${FileSize.format(result.newTotalBytes)})")
        val diffColor = if (result.totalDiffBytes > 0) red else green
        val diffSign = if (result.totalDiffBytes > 0) "+" else ""
        val pctChange = if (result.oldTotalBytes > 0) {
            val pct = (result.totalDiffBytes.toDouble() / result.oldTotalBytes * 100)
            " (${if (pct > 0) "+" else ""}${"%.1f".format(pct)}%)"
        } else ""
        terminal.println("Fark: ${diffColor("$diffSign${FileSize.format(result.totalDiffBytes)}$pctChange")}")
        terminal.println()

        terminal.println(bold("Kategori Dagilimi"))
        terminal.println(table {
            header { row("Kategori", "Eski", "Yeni", "Fark") }
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
            terminal.println(red(bold("En Buyuk Artislar")))
            terminal.println(table {
                header { row("Dosya", "Eski", "Yeni", "Fark") }
                body {
                    result.topIncreases.forEach { e ->
                        row(e.path.substringAfterLast('/'), FileSize.format(e.oldBytes), FileSize.format(e.newBytes), red("+${FileSize.format(e.diffBytes)}").toString())
                    }
                }
            })
            terminal.println()
        }

        if (result.topDecreases.isNotEmpty()) {
            terminal.println(green(bold("En Buyuk Azalislar")))
            terminal.println(table {
                header { row("Dosya", "Eski", "Yeni", "Fark") }
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
        terminal.println(bold("Native Kutuphane Raporu"))
        terminal.println("Toplam: ${result.totalLibs} kutuphane, ${FileSize.format(result.totalSizeBytes)}")
        terminal.println("ABI'ler: ${result.abiSummary.entries.joinToString(", ") { "${it.key} (${it.value})" }}")
        val alignStatus = if (result.allPageAligned) green("TUMU 16KB UYUMLU") else red("UYUMSUZ BULUNDU")
        terminal.println("16KB Hizalama: $alignStatus")
        terminal.println()

        terminal.println(table {
            header { row("Kutuphane", "ABI", "Boyut", "Sikistirilmamis", "Strip", "ELF", "16KB") }
            body {
                result.libraries.forEach { lib ->
                    val strippedText = if (lib.isStripped) green("Evet") else yellow("Hayir")
                    val alignText = when {
                        lib.elfClass == 32 -> gray("—")
                        lib.pageAligned -> green("OK")
                        else -> red("BASARISIZ")
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
        val readyText = if (result.isReady) green(bold("PLAY STORE HAZIR")) else red(bold("HAZIR DEGIL"))
        terminal.println(bold("Google Play Uyumlulugu: $readyText"))
        terminal.println("Basarili: ${result.passCount} | Basarisiz: ${result.failCount} | Uyari: ${result.warnCount}")
        terminal.println()

        terminal.println(table {
            column(1) { width = ColumnWidth(priority = 10, width = 6) }
            header { row("Kontrol", "Durum", "Detay") }
            body {
                result.checks.forEach { check ->
                    val statusText = when (check.status) {
                        CheckStatus.PASS -> green("OK")
                        CheckStatus.FAIL -> red("HATA")
                        CheckStatus.WARN -> yellow("UYARI")
                    }
                    row(check.name, statusText.toString(), check.detail)
                }
            }
        })

        // Actionable suggestions for failed checks
        val failedChecks = result.checks.filter { it.status == CheckStatus.FAIL }
        if (failedChecks.isNotEmpty()) {
            terminal.println(bold(yellow("Oneriler:")))
            for (check in failedChecks) {
                when {
                    check.name.contains("Target SDK") -> terminal.println(yellow("  → build.gradle'da targetSdk'yi ${com.androidutil.core.playcompat.PlayCompatChecker.REQUIRED_TARGET_SDK}+ yapın"))
                    check.name.contains("16KB") -> terminal.println(yellow("  → Native kutuphaneleri -Wl,-z,max-page-size=16384 ile yeniden derleyin"))
                    check.name.contains("64-bit") -> terminal.println(yellow("  → arm64-v8a ABI destegi ekleyin (NDK ile 64-bit build)"))
                }
            }
            terminal.println()
        }
    }

    override fun renderResourceReport(result: ResourceReport) {
        terminal.println()
        terminal.println(bold("Kaynak ve Asset Raporu"))
        terminal.println("Toplam: ${result.totalResources} dosya, ${FileSize.format(result.totalBytes)}")
        terminal.println()

        terminal.println(table {
            header { row("Kategori", "Dosya", "Boyut") }
            body {
                result.categories.forEach { cat ->
                    row(cat.name, cat.fileCount.toString(), FileSize.format(cat.totalBytes))
                }
            }
        })
        terminal.println()

        terminal.println(bold("En Buyuk Dosyalar (Ilk 15)"))
        terminal.println(table {
            header { row("Dosya", "Sikistirilmis", "Sikistirilmamis") }
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
        terminal.println(bold("Manifest Farki"))
        terminal.println("Eski: ${result.oldFile}")
        terminal.println("Yeni: ${result.newFile}")
        terminal.println()

        if (result.changes.isNotEmpty()) {
            terminal.println(bold("Degisiklikler (${result.changes.size})"))
            terminal.println(table {
                header { row("Alan", "Eski", "Yeni") }
                body {
                    result.changes.forEach { change ->
                        row(change.field, red(change.oldValue).toString(), green(change.newValue).toString())
                    }
                }
            })
            terminal.println()
        } else {
            terminal.println(green("Manifest alanlari ayni (version, SDK, component sayilari)."))
            terminal.println()
        }

        val perms = result.permissionChanges
        if (perms.added.isNotEmpty()) {
            terminal.println(green(bold("+ Eklenen Izinler (${perms.added.size})")))
            perms.added.forEach { terminal.println(green("  + ${it.substringAfterLast('.')}")) }
            terminal.println()
        }
        if (perms.removed.isNotEmpty()) {
            terminal.println(red(bold("- Kaldirilan Izinler (${perms.removed.size})")))
            perms.removed.forEach { terminal.println(red("  - ${it.substringAfterLast('.')}")) }
            terminal.println()
        }
        if (perms.added.isEmpty() && perms.removed.isEmpty()) {
            terminal.println(green("Izinler degismemis."))
            terminal.println()
        }
        terminal.println(gray("Degismeyen: ${perms.unchanged.size} izin"))
        terminal.println()
    }

    private fun schemeStatus(valid: Boolean?): String {
        return when (valid) {
            true -> green("DOGRULANDI").toString()
            false -> red("DOGRULANMADI").toString()
            null -> "N/A"
        }
    }
}
