package com.androidutil.output

import com.androidutil.core.analyzer.AabAnalysisResult
import com.androidutil.core.analyzer.ApkAnalysisResult
import com.androidutil.core.nativelib.NativeLibReport
import com.androidutil.core.playcompat.PlayCompatResult
import com.androidutil.core.playcompat.CheckStatus
import com.androidutil.core.resources.ResourceReport
import com.androidutil.util.FileSize
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.writeText

class HtmlReportGenerator {

    fun generateReport(
        filePath: String,
        apkResult: ApkAnalysisResult? = null,
        aabResult: AabAnalysisResult? = null,
        nativeLibReport: NativeLibReport? = null,
        playCompat: PlayCompatResult? = null,
        resourceReport: ResourceReport? = null,
        outputPath: Path
    ) {
        val html = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang='tr'><head><meta charset='UTF-8'>")
            appendLine("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
            appendLine("<title>AndroidUtil Raporu - $filePath</title>")
            appendLine("<style>")
            appendLine(CSS)
            appendLine("</style></head><body>")
            appendLine("<div class='container'>")

            // Header
            appendLine("<h1>AndroidUtil Analiz Raporu</h1>")
            appendLine("<p class='meta'>Olusturulma: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}</p>")
            appendLine("<p class='meta'>Dosya: <code>$filePath</code></p>")

            // Manifest section
            val manifest = apkResult?.manifestInfo ?: aabResult?.manifestInfo
            if (manifest != null) {
                appendLine("<h2>Manifest</h2>")
                appendLine("<table><tbody>")
                appendLine("<tr><td>Paket</td><td><strong>${manifest.packageName}</strong></td></tr>")
                appendLine("<tr><td>Surum</td><td>${manifest.versionName} (${manifest.versionCode})</td></tr>")
                appendLine("<tr><td>Min SDK</td><td>${manifest.minSdk}</td></tr>")
                appendLine("<tr><td>Target SDK</td><td>${manifest.targetSdk}</td></tr>")
                if (manifest.compileSdk != null) appendLine("<tr><td>Compile SDK</td><td>${manifest.compileSdk}</td></tr>")
                appendLine("<tr><td>Bilesenler</td><td>${manifest.activities} activity, ${manifest.services} service, ${manifest.receivers} receiver, ${manifest.providers} provider</td></tr>")
                appendLine("</tbody></table>")
            }

            // Size breakdown (APK only)
            if (apkResult != null) {
                appendLine("<h2>Boyut Dagilimi</h2>")
                appendLine("<p>Toplam: <strong>${FileSize.formatDetailed(apkResult.fileSizeBytes)}</strong></p>")
                appendLine("<table><thead><tr><th>Bilesen</th><th>Boyut</th><th>%</th></tr></thead><tbody>")
                val bd = apkResult.sizeBreakdown
                appendLine("<tr><td>DEX</td><td>${FileSize.format(bd.dexBytes)}</td><td>${FileSize.percentage(bd.dexBytes, bd.totalBytes)}</td></tr>")
                appendLine("<tr><td>Kaynaklar</td><td>${FileSize.format(bd.resourceBytes)}</td><td>${FileSize.percentage(bd.resourceBytes, bd.totalBytes)}</td></tr>")
                appendLine("<tr><td>Native Kutuphaneler</td><td>${FileSize.format(bd.nativeLibBytes)}</td><td>${FileSize.percentage(bd.nativeLibBytes, bd.totalBytes)}</td></tr>")
                appendLine("<tr><td>Asset</td><td>${FileSize.format(bd.assetBytes)}</td><td>${FileSize.percentage(bd.assetBytes, bd.totalBytes)}</td></tr>")
                appendLine("<tr><td>Diger</td><td>${FileSize.format(bd.otherBytes)}</td><td>${FileSize.percentage(bd.otherBytes, bd.totalBytes)}</td></tr>")
                appendLine("</tbody></table>")
            }

            // Play compatibility
            if (playCompat != null) {
                appendLine("<h2>Google Play Uyumlulugu</h2>")
                val readyClass = if (playCompat.isReady) "pass" else "fail"
                val readyText = if (playCompat.isReady) "HAZIR" else "HAZIR DEGIL"
                appendLine("<p class='status-$readyClass'><strong>$readyText</strong> (${playCompat.passCount} basarili, ${playCompat.failCount} basarisiz, ${playCompat.warnCount} uyari)</p>")
                appendLine("<table><thead><tr><th>Kontrol</th><th>Durum</th><th>Detay</th></tr></thead><tbody>")
                playCompat.checks.forEach { check ->
                    val statusClass = when (check.status) {
                        CheckStatus.PASS -> "pass"
                        CheckStatus.FAIL -> "fail"
                        CheckStatus.WARN -> "warn"
                    }
                    val statusText = when (check.status) {
                        CheckStatus.PASS -> "OK"
                        CheckStatus.FAIL -> "HATA"
                        CheckStatus.WARN -> "UYARI"
                    }
                    appendLine("<tr><td>${check.name}</td><td class='status-$statusClass'>$statusText</td><td>${check.detail}</td></tr>")
                }
                appendLine("</tbody></table>")
            }

            // Native libraries
            if (nativeLibReport != null && nativeLibReport.totalLibs > 0) {
                appendLine("<h2>Native Kutuphaneler (${nativeLibReport.totalLibs})</h2>")
                appendLine("<p>Toplam boyut: ${FileSize.format(nativeLibReport.totalSizeBytes)} | ABI'ler: ${nativeLibReport.abiSummary.entries.joinToString(", ") { "${it.key} (${it.value})" }}</p>")
                appendLine("<table><thead><tr><th>Kutuphane</th><th>ABI</th><th>Boyut</th><th>Strip</th><th>16KB</th></tr></thead><tbody>")
                nativeLibReport.libraries.forEach { lib ->
                    val alignText = when {
                        lib.elfClass == 32 -> "<span class='meta'>—</span>"
                        lib.pageAligned -> "<span class='status-pass'>OK</span>"
                        else -> "<span class='status-fail'>BASARISIZ</span>"
                    }
                    appendLine("<tr><td>${lib.path.substringAfterLast('/')}</td><td>${lib.abi}</td><td>${FileSize.format(lib.sizeBytes)}</td><td>${if (lib.isStripped) "Evet" else "<span class='status-warn'>Hayir</span>"}</td><td>$alignText</td></tr>")
                }
                appendLine("</tbody></table>")
            }

            // Resources
            if (resourceReport != null) {
                appendLine("<h2>Kaynaklar (${resourceReport.totalResources} dosya, ${FileSize.format(resourceReport.totalBytes)})</h2>")
                appendLine("<table><thead><tr><th>Kategori</th><th>Dosya</th><th>Boyut</th></tr></thead><tbody>")
                resourceReport.categories.forEach { cat ->
                    appendLine("<tr><td>${cat.name}</td><td>${cat.fileCount}</td><td>${FileSize.format(cat.totalBytes)}</td></tr>")
                }
                appendLine("</tbody></table>")

                appendLine("<h3>En Buyuk Dosyalar (Ilk 15)</h3>")
                appendLine("<table><thead><tr><th>Dosya</th><th>Sikistirilmis</th><th>Sikistirilmamis</th></tr></thead><tbody>")
                resourceReport.largestFiles.forEach { entry ->
                    appendLine("<tr><td><code>${entry.path}</code></td><td>${FileSize.format(entry.compressedBytes)}</td><td>${FileSize.format(entry.uncompressedBytes)}</td></tr>")
                }
                appendLine("</tbody></table>")
            }

            // Permissions
            if (manifest != null && manifest.permissions.isNotEmpty()) {
                appendLine("<h2>Izinler (${manifest.permissions.size})</h2>")
                appendLine("<ul>")
                manifest.permissions.sorted().forEach { perm ->
                    appendLine("<li><code>${perm.substringAfterLast('.')}</code> <span class='meta'>$perm</span></li>")
                }
                appendLine("</ul>")
            }

            // Alignment details
            val alignmentResults = apkResult?.alignmentResults ?: aabResult?.alignmentResults
            if (alignmentResults != null && alignmentResults.isNotEmpty()) {
                val results64bit = alignmentResults.filter { it.elfClass == 64 }
                appendLine("<h2>16KB Sayfa Hizalamasi</h2>")
                val allOk = results64bit.isEmpty() || results64bit.all { it.isCompatible }
                val statusClass = if (allOk) "pass" else "fail"
                appendLine("<p class='status-$statusClass'>${if (allOk) "TUMU UYUMLU" else "UYUMSUZ KUTUPHANE BULUNDU"}</p>")
                if (results64bit.isNotEmpty()) {
                    appendLine("<table><thead><tr><th>Kutuphane</th><th>ABI</th><th>ELF</th><th>Min Hiza</th><th>Durum</th></tr></thead><tbody>")
                    results64bit.forEach { r ->
                        val minAlign = r.ptLoadSegments.minOfOrNull { it.pAlign } ?: 0L
                        val cls = if (r.isCompatible) "pass" else "fail"
                        appendLine("<tr><td>${r.libraryPath.substringAfterLast('/')}</td><td>${r.abi}</td><td>${r.elfClass}-bit</td><td>$minAlign</td><td class='status-$cls'>${if (r.isCompatible) "OK" else "BASARISIZ"}</td></tr>")
                    }
                    appendLine("</tbody></table>")
                }
                val results32bit = alignmentResults.filter { it.elfClass == 32 }
                if (results32bit.isNotEmpty()) {
                    appendLine("<p class='meta'>32-bit kutuphaneler (${results32bit.size} adet) — 16KB alignment gerekmez</p>")
                }
            }

            appendLine("<footer><p>AndroidUtil v1.0.0 tarafindan olusturuldu</p></footer>")
            appendLine("</div></body></html>")
        }

        outputPath.writeText(html)
    }

    companion object {
        private val CSS = """
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; color: #333; line-height: 1.6; }
            .container { max-width: 900px; margin: 20px auto; background: #fff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); padding: 30px; }
            h1 { color: #1a73e8; border-bottom: 2px solid #1a73e8; padding-bottom: 10px; margin-bottom: 20px; }
            h2 { color: #333; margin: 25px 0 10px; padding-bottom: 5px; border-bottom: 1px solid #e0e0e0; }
            h3 { color: #555; margin: 15px 0 8px; }
            table { width: 100%; border-collapse: collapse; margin: 10px 0 20px; font-size: 14px; }
            th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; }
            th { background: #f8f9fa; font-weight: 600; color: #555; }
            tr:hover { background: #f8f9fa; }
            code { background: #f0f0f0; padding: 2px 6px; border-radius: 3px; font-size: 13px; }
            .meta { color: #888; font-size: 13px; }
            .status-pass { color: #0d9e0d; font-weight: 600; }
            .status-fail { color: #d93025; font-weight: 600; }
            .status-warn { color: #f9a825; font-weight: 600; }
            ul { list-style: disc; margin: 10px 0 20px 25px; }
            li { margin: 4px 0; }
            footer { margin-top: 30px; padding-top: 15px; border-top: 1px solid #e0e0e0; text-align: center; }
            footer p { color: #aaa; font-size: 12px; }
        """.trimIndent()
    }
}
