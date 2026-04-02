package com.androidutil.core.deeplink

import com.androidutil.sdk.AndroidSdkLocator
import com.androidutil.util.ProcessRunner
import java.nio.file.Path
import java.util.zip.ZipFile

data class DeeplinkInfo(
    val activityName: String,
    val scheme: String,
    val host: String,
    val pathPattern: String,
    val autoVerify: Boolean,
    val fullUrl: String
)

data class DeeplinkAnalysisResult(
    val filePath: String,
    val deeplinks: List<DeeplinkInfo>,
    val appLinks: List<DeeplinkInfo>,       // https/http with autoVerify
    val customSchemes: List<DeeplinkInfo>,   // custom://
    val totalCount: Int
)

class DeeplinkAnalyzer {

    fun analyze(filePath: Path): DeeplinkAnalysisResult {
        val manifestXml = dumpManifest(filePath)
        val deeplinks = parseDeeplinks(manifestXml)

        val appLinks = deeplinks.filter {
            (it.scheme == "https" || it.scheme == "http") && it.autoVerify
        }
        val customSchemes = deeplinks.filter {
            it.scheme != "https" && it.scheme != "http"
        }

        return DeeplinkAnalysisResult(
            filePath = filePath.toString(),
            deeplinks = deeplinks,
            appLinks = appLinks,
            customSchemes = customSchemes,
            totalCount = deeplinks.size
        )
    }

    private fun dumpManifest(filePath: Path): String {
        // For AAB: use bundletool library to render manifest as XML
        if (filePath.toString().endsWith(".aab", ignoreCase = true)) {
            try {
                val xml = ZipFile(filePath.toFile()).use { zip ->
                    val appBundle = com.android.tools.build.bundletool.model.AppBundle.buildFromZip(zip)
                    val manifest = appBundle.baseModule.androidManifest
                    renderManifestXml(manifest.manifestElement)
                }
                if (xml.isNotBlank()) return xml
            } catch (_: Exception) { /* fall through to aapt2 */ }
        }

        // For APK: try aapt2
        val aapt2 = AndroidSdkLocator.locate().aapt2Path
        if (aapt2 != null) {
            val result = ProcessRunner.run(
                listOf(aapt2.toString(), "dump", "xmltree", filePath.toString(), "--file", "AndroidManifest.xml")
            )
            if (result.exitCode == 0 && result.stdout.isNotBlank()) {
                return result.stdout
            }
            val badging = ProcessRunner.run(
                listOf(aapt2.toString(), "dump", "badging", filePath.toString())
            )
            if (badging.exitCode == 0) return badging.stdout
        }

        return ""
    }

    /** Render XmlProtoElement tree as XML string */
    private fun renderManifestXml(
        element: com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement,
        indent: Int = 0
    ): String {
        val sb = StringBuilder()
        val pad = "  ".repeat(indent)
        sb.append("$pad<${element.name}")

        // Render attributes
        element.attributes.forEach { attr ->
            val ns = if (attr.namespaceUri == "http://schemas.android.com/apk/res/android") "android:" else ""
            val value = try { attr.valueAsString } catch (_: Exception) {
                try { attr.valueAsDecimalInteger.toString() } catch (_: Exception) {
                    try { attr.valueAsBoolean.toString() } catch (_: Exception) {
                        try { "0x%08x".format(attr.valueAsRefId) } catch (_: Exception) { "" }
                    }
                }
            }
            sb.append(" $ns${attr.name}=\"$value\"")
        }

        val children = element.getChildrenElements().toList()
        if (children.isEmpty()) {
            sb.appendLine("/>")
        } else {
            sb.appendLine(">")
            for (child in children) {
                sb.append(renderManifestXml(child, indent + 1))
            }
            sb.appendLine("$pad</${element.name}>")
        }
        return sb.toString()
    }

    private fun parseDeeplinks(manifest: String): List<DeeplinkInfo> {
        val deeplinks = mutableListOf<DeeplinkInfo>()

        // Detect format: xmltree (aapt2 dump xmltree) vs xml (bundletool dump manifest)
        if (manifest.contains("<activity") || manifest.contains("<intent-filter")) {
            return parseXmlManifest(manifest)
        }
        return parseXmlTree(manifest)
    }

    private fun parseXmlManifest(xml: String): List<DeeplinkInfo> {
        val deeplinks = mutableListOf<DeeplinkInfo>()

        // Split by activity blocks
        val activityPattern = Regex(
            """<activity[^>]*android:name="([^"]*)"[^>]*>(.*?)</activity>""",
            RegexOption.DOT_MATCHES_ALL
        )

        for (activityMatch in activityPattern.findAll(xml)) {
            val activityName = activityMatch.groupValues[1]
            val activityBody = activityMatch.groupValues[2]

            // Find intent-filters with VIEW action and BROWSABLE category
            val filterPattern = Regex(
                """<intent-filter[^>]*>(.*?)</intent-filter>""",
                RegexOption.DOT_MATCHES_ALL
            )

            for (filterMatch in filterPattern.findAll(activityBody)) {
                val filterBody = filterMatch.groupValues[1]
                val filterTag = filterMatch.groupValues[0]

                val hasViewAction = filterBody.contains("android.intent.action.VIEW")
                val hasBrowsable = filterBody.contains("android.intent.category.BROWSABLE")
                val hasAutoVerify = filterTag.contains("android:autoVerify=\"true\"")

                if (!hasViewAction || !hasBrowsable) continue

                // Collect all <data> attributes across multiple tags
                // Bundletool may split scheme, host, pathPrefix into separate <data> tags
                val dataPattern = Regex("""<data\s+(.*?)/>""")
                val schemes = mutableListOf<String>()
                val hosts = mutableListOf<String>()
                val paths = mutableListOf<String>()

                for (dataMatch in dataPattern.findAll(filterBody)) {
                    val attrs = dataMatch.groupValues[1]
                    extractAttr(attrs, "scheme")?.let { schemes.add(it) }
                    extractAttr(attrs, "host")?.let { host ->
                        val path = extractAttr(attrs, "path")
                            ?: extractAttr(attrs, "pathPrefix")
                            ?: extractAttr(attrs, "pathPattern")
                            ?: ""
                        hosts.add(host)
                        if (path.isNotEmpty()) paths.add(path)
                    }
                    // Handle data tag with only path (no host)
                    if (extractAttr(attrs, "host") == null) {
                        val path = extractAttr(attrs, "path")
                            ?: extractAttr(attrs, "pathPrefix")
                            ?: extractAttr(attrs, "pathPattern")
                        if (path != null) paths.add(path)
                    }
                }

                if (schemes.isEmpty()) continue

                // Combine: each scheme x each host x each path
                for (scheme in schemes) {
                    val hostList = if (hosts.isEmpty()) listOf("") else hosts
                    val pathList = if (paths.isEmpty()) listOf("") else paths
                    for (host in hostList) {
                        for (path in pathList) {
                            deeplinks.add(DeeplinkInfo(
                                activityName = activityName,
                                scheme = scheme,
                                host = host,
                                pathPattern = path,
                                autoVerify = hasAutoVerify,
                                fullUrl = buildUrl(scheme, host, path)
                            ))
                        }
                    }
                }
            }
        }

        return deeplinks
    }

    private fun parseXmlTree(xmlTree: String): List<DeeplinkInfo> {
        val deeplinks = mutableListOf<DeeplinkInfo>()
        val lines = xmlTree.lines()

        var currentActivity = ""
        var inIntentFilter = false
        var hasViewAction = false
        var hasBrowsable = false
        var hasAutoVerify = false
        var schemes = mutableListOf<String>()
        var hosts = mutableListOf<String>()
        var paths = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()

            // Track activity name
            if (trimmed.startsWith("E: activity") || trimmed.contains("activity (line=")) {
                currentActivity = ""
            }
            if (trimmed.contains("A: android:name") && currentActivity.isEmpty()) {
                currentActivity = trimmed.substringAfter("=\"").substringBefore("\"")
                if (currentActivity.isEmpty()) {
                    currentActivity = trimmed.substringAfterLast("=").trim().removeSurrounding("\"")
                }
            }

            // Track intent-filter
            if (trimmed.startsWith("E: intent-filter")) {
                inIntentFilter = true
                hasViewAction = false
                hasBrowsable = false
                hasAutoVerify = trimmed.contains("autoVerify") && trimmed.contains("0xffffffff")
                schemes.clear()
                hosts.clear()
                paths.clear()
            }

            if (inIntentFilter) {
                if (trimmed.contains("android.intent.action.VIEW")) hasViewAction = true
                if (trimmed.contains("android.intent.category.BROWSABLE")) hasBrowsable = true

                if (trimmed.contains("A: android:scheme")) {
                    val scheme = extractXmlTreeValue(trimmed)
                    if (scheme.isNotEmpty()) schemes.add(scheme)
                }
                if (trimmed.contains("A: android:host")) {
                    val host = extractXmlTreeValue(trimmed)
                    if (host.isNotEmpty()) hosts.add(host)
                }
                if (trimmed.contains("A: android:path") ||
                    trimmed.contains("A: android:pathPrefix") ||
                    trimmed.contains("A: android:pathPattern")) {
                    val path = extractXmlTreeValue(trimmed)
                    if (path.isNotEmpty()) paths.add(path)
                }
            }

            // End of intent-filter block (next element at same or higher level)
            if (inIntentFilter && trimmed.startsWith("E: ") && !trimmed.startsWith("E: intent-filter") &&
                !trimmed.startsWith("E: action") && !trimmed.startsWith("E: category") &&
                !trimmed.startsWith("E: data")) {

                if (hasViewAction && hasBrowsable && schemes.isNotEmpty()) {
                    for (scheme in schemes) {
                        val hostList = if (hosts.isEmpty()) listOf("") else hosts
                        val pathList = if (paths.isEmpty()) listOf("") else paths
                        for (host in hostList) {
                            for (path in pathList) {
                                deeplinks.add(DeeplinkInfo(
                                    activityName = currentActivity,
                                    scheme = scheme,
                                    host = host,
                                    pathPattern = path,
                                    autoVerify = hasAutoVerify,
                                    fullUrl = buildUrl(scheme, host, path)
                                ))
                            }
                        }
                    }
                }
                inIntentFilter = false
            }
        }

        // Flush last intent-filter if file ended
        if (inIntentFilter && hasViewAction && hasBrowsable && schemes.isNotEmpty()) {
            for (scheme in schemes) {
                val hostList = if (hosts.isEmpty()) listOf("") else hosts
                val pathList = if (paths.isEmpty()) listOf("") else paths
                for (host in hostList) {
                    for (path in pathList) {
                        deeplinks.add(DeeplinkInfo(
                            activityName = currentActivity,
                            scheme = scheme,
                            host = host,
                            pathPattern = path,
                            autoVerify = hasAutoVerify,
                            fullUrl = buildUrl(scheme, host, path)
                        ))
                    }
                }
            }
        }

        return deeplinks
    }

    private fun extractAttr(attrs: String, name: String): String? {
        val pattern = Regex("""android:$name="([^"]*)"""")
        return pattern.find(attrs)?.groupValues?.get(1)
    }

    private fun extractXmlTreeValue(line: String): String {
        // Format: A: android:scheme(0x...)="value" or A: android:scheme="value"
        return line.substringAfterLast("=\"").removeSuffix("\"").trim()
            .ifEmpty { line.substringAfterLast("=").trim().removeSurrounding("\"") }
    }

    private fun buildUrl(scheme: String, host: String, path: String): String {
        return if (host.isNotEmpty()) {
            "$scheme://$host$path"
        } else {
            "$scheme://$path"
        }
    }
}
