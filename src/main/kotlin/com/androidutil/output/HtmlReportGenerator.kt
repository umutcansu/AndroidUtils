package com.androidutil.output

import com.androidutil.core.analyzer.AabAnalysisResult
import com.androidutil.core.analyzer.ApkAnalysisResult
import com.androidutil.core.nativelib.NativeLibReport
import com.androidutil.core.playcompat.PlayCompatResult
import com.androidutil.core.playcompat.CheckStatus
import com.androidutil.core.resources.ResourceReport
import com.androidutil.i18n.Messages
import com.androidutil.util.FileSize
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.writeText

class HtmlReportGenerator(private val msg: Messages) {

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
            appendLine("<html lang='${if (msg["common.yes"] == "Yes") "en" else "tr"}'><head><meta charset='UTF-8'>")
            appendLine("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
            appendLine("<title>${msg.get("html.title", filePath)}</title>")
            appendLine("<style>")
            appendLine(CSS)
            appendLine("</style></head><body>")
            appendLine("<div class='container'>")

            // Header
            appendLine("<h1>${msg["html.header"]}</h1>")
            appendLine("<p class='meta'>${msg.get("html.createdAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))}</p>")
            appendLine("<p class='meta'>${msg.get("html.file", "<code>$filePath</code>")}</p>")

            // Manifest section
            val manifest = apkResult?.manifestInfo ?: aabResult?.manifestInfo
            if (manifest != null) {
                appendLine("<h2>${msg["html.manifest"]}</h2>")
                appendLine("<table><tbody>")
                appendLine("<tr><td>${msg["html.package"]}</td><td><strong>${manifest.packageName}</strong></td></tr>")
                appendLine("<tr><td>${msg["html.version"]}</td><td>${manifest.versionName} (${manifest.versionCode})</td></tr>")
                appendLine("<tr><td>Min SDK</td><td>${manifest.minSdk}</td></tr>")
                appendLine("<tr><td>Target SDK</td><td>${manifest.targetSdk}</td></tr>")
                if (manifest.compileSdk != null) appendLine("<tr><td>Compile SDK</td><td>${manifest.compileSdk}</td></tr>")
                appendLine("<tr><td>${msg["html.components"]}</td><td>${manifest.activities} activity, ${manifest.services} service, ${manifest.receivers} receiver, ${manifest.providers} provider</td></tr>")
                appendLine("</tbody></table>")
            }

            // Size breakdown (APK only)
            if (apkResult != null) {
                appendLine("<h2>${msg["html.sizeBreakdown"]}</h2>")
                appendLine("<p>${msg.get("html.total", "<strong>${FileSize.formatDetailed(apkResult.fileSizeBytes)}</strong>")}</p>")
                appendLine("<table><thead><tr><th>${msg["html.component"]}</th><th>${msg["common.size"]}</th><th>%</th></tr></thead><tbody>")
                val bd = apkResult.sizeBreakdown
                appendLine("<tr><td>DEX</td><td>${FileSize.format(bd.dexBytes)}</td><td>${FileSize.percentage(bd.dexBytes, bd.totalBytes)}</td></tr>")
                appendLine("<tr><td>${msg["html.resources"]}</td><td>${FileSize.format(bd.resourceBytes)}</td><td>${FileSize.percentage(bd.resourceBytes, bd.totalBytes)}</td></tr>")
                appendLine("<tr><td>${msg["html.nativeLibraries"]}</td><td>${FileSize.format(bd.nativeLibBytes)}</td><td>${FileSize.percentage(bd.nativeLibBytes, bd.totalBytes)}</td></tr>")
                appendLine("<tr><td>Asset</td><td>${FileSize.format(bd.assetBytes)}</td><td>${FileSize.percentage(bd.assetBytes, bd.totalBytes)}</td></tr>")
                appendLine("<tr><td>${msg["html.other"]}</td><td>${FileSize.format(bd.otherBytes)}</td><td>${FileSize.percentage(bd.otherBytes, bd.totalBytes)}</td></tr>")
                appendLine("</tbody></table>")
            }

            // Play compatibility
            if (playCompat != null) {
                appendLine("<h2>${msg["html.playCompat"]}</h2>")
                val readyClass = if (playCompat.isReady) "pass" else "fail"
                val readyText = if (playCompat.isReady) msg["html.ready"] else msg["html.notReady"]
                appendLine("<p class='status-$readyClass'><strong>$readyText</strong> (${msg.get("html.playStats", playCompat.passCount, playCompat.failCount, playCompat.warnCount)})</p>")
                appendLine("<table><thead><tr><th>${msg["html.check"]}</th><th>${msg["html.status"]}</th><th>${msg["html.detail"]}</th></tr></thead><tbody>")
                playCompat.checks.forEach { check ->
                    val statusClass = when (check.status) {
                        CheckStatus.PASS -> "pass"
                        CheckStatus.FAIL -> "fail"
                        CheckStatus.WARN -> "warn"
                    }
                    val statusText = when (check.status) {
                        CheckStatus.PASS -> "OK"
                        CheckStatus.FAIL -> msg["terminal.error"]
                        CheckStatus.WARN -> msg["terminal.warning"]
                    }
                    appendLine("<tr><td>${check.name}</td><td class='status-$statusClass'>$statusText</td><td>${check.detail}</td></tr>")
                }
                appendLine("</tbody></table>")
            }

            // Native libraries
            if (nativeLibReport != null && nativeLibReport.totalLibs > 0) {
                appendLine("<h2>${msg.get("html.nativeLibs", nativeLibReport.totalLibs)}</h2>")
                appendLine("<p>${msg.get("html.nativeLibSize", FileSize.format(nativeLibReport.totalSizeBytes), nativeLibReport.abiSummary.entries.joinToString(", ") { "${it.key} (${it.value})" })}</p>")
                appendLine("<table><thead><tr><th>${msg["terminal.library"]}</th><th>ABI</th><th>${msg["common.size"]}</th><th>${msg["html.strip"]}</th><th>16KB</th></tr></thead><tbody>")
                nativeLibReport.libraries.forEach { lib ->
                    val alignText = when {
                        lib.elfClass == 32 -> "<span class='meta'>—</span>"
                        lib.pageAligned -> "<span class='status-pass'>OK</span>"
                        else -> "<span class='status-fail'>${msg["terminal.failed"]}</span>"
                    }
                    appendLine("<tr><td>${lib.path.substringAfterLast('/')}</td><td>${lib.abi}</td><td>${FileSize.format(lib.sizeBytes)}</td><td>${if (lib.isStripped) msg["common.yes"] else "<span class='status-warn'>${msg["common.no"]}</span>"}</td><td>$alignText</td></tr>")
                }
                appendLine("</tbody></table>")
            }

            // Resources
            if (resourceReport != null) {
                appendLine("<h2>${msg.get("html.resources.title", resourceReport.totalResources, FileSize.format(resourceReport.totalBytes))}</h2>")
                appendLine("<table><thead><tr><th>${msg["html.category"]}</th><th>${msg["html.fileCount"]}</th><th>${msg["common.size"]}</th></tr></thead><tbody>")
                resourceReport.categories.forEach { cat ->
                    appendLine("<tr><td>${cat.name}</td><td>${cat.fileCount}</td><td>${FileSize.format(cat.totalBytes)}</td></tr>")
                }
                appendLine("</tbody></table>")

                appendLine("<h3>${msg["html.largestFiles"]}</h3>")
                appendLine("<table><thead><tr><th>${msg["html.fileCount"]}</th><th>${msg["html.compressed"]}</th><th>${msg["html.uncompressed"]}</th></tr></thead><tbody>")
                resourceReport.largestFiles.forEach { entry ->
                    appendLine("<tr><td><code>${entry.path}</code></td><td>${FileSize.format(entry.compressedBytes)}</td><td>${FileSize.format(entry.uncompressedBytes)}</td></tr>")
                }
                appendLine("</tbody></table>")
            }

            // Permissions
            if (manifest != null && manifest.permissions.isNotEmpty()) {
                appendLine("<h2>${msg.get("html.permissions", manifest.permissions.size)}</h2>")
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
                appendLine("<h2>${msg["html.pageAlignment"]}</h2>")
                val allOk = results64bit.isEmpty() || results64bit.all { it.isCompatible }
                val statusClass = if (allOk) "pass" else "fail"
                appendLine("<p class='status-$statusClass'>${if (allOk) msg["html.allCompatible"] else msg["html.incompatibleFound"]}</p>")
                if (results64bit.isNotEmpty()) {
                    appendLine("<table><thead><tr><th>${msg["terminal.library"]}</th><th>ABI</th><th>ELF</th><th>${msg["html.minAlignment"]}</th><th>${msg["html.status"]}</th></tr></thead><tbody>")
                    results64bit.forEach { r ->
                        val minAlign = r.ptLoadSegments.minOfOrNull { it.pAlign } ?: 0L
                        val cls = if (r.isCompatible) "pass" else "fail"
                        appendLine("<tr><td>${r.libraryPath.substringAfterLast('/')}</td><td>${r.abi}</td><td>${r.elfClass}-bit</td><td>$minAlign</td><td class='status-$cls'>${if (r.isCompatible) "OK" else msg["terminal.failed"]}</td></tr>")
                    }
                    appendLine("</tbody></table>")
                }
                val results32bit = alignmentResults.filter { it.elfClass == 32 }
                if (results32bit.isNotEmpty()) {
                    appendLine("<p class='meta'>${msg.get("html.32bitNote", results32bit.size)}</p>")
                }
            }

            appendLine("<footer><p>${msg["html.footer"]}</p></footer>")
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
