package com.androidutil.core.stacktrace

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.io.path.bufferedReader

data class DecodedStackTrace(
    val originalLines: List<String>,
    val decodedLines: List<String>,
    val mappingsApplied: Int
)

/**
 * Decodes obfuscated (ProGuard/R8) stack traces using mapping.txt files.
 * Supports the standard ProGuard mapping format.
 */
class StackTraceDecoder {

    fun decode(stackTrace: String, mappingFile: Path): DecodedStackTrace {
        val mappings = parseMappingFile(mappingFile)
        val originalLines = stackTrace.lines()
        var mappingsApplied = 0

        val decodedLines = originalLines.map { line ->
            val decoded = decodeLine(line, mappings)
            if (decoded != line) mappingsApplied++
            decoded
        }

        return DecodedStackTrace(
            originalLines = originalLines,
            decodedLines = decodedLines,
            mappingsApplied = mappingsApplied
        )
    }

    private fun decodeLine(line: String, mappings: MappingData): String {
        var result = line

        // Match patterns like: at a.b.c.d(SourceFile:123)
        val stackPattern = Regex("""(\s*at\s+)(\S+)\.(\S+)\(([^)]*)\)""")
        val match = stackPattern.find(line)
        if (match != null) {
            val prefix = match.groupValues[1]
            val className = match.groupValues[2]
            val methodName = match.groupValues[3]
            val source = match.groupValues[4]

            val originalClass = mappings.classMap[className] ?: className
            val methodKey = "$className.$methodName"
            val originalMethod = mappings.methodMap[methodKey] ?: methodName

            // Also try to resolve the line number context
            val decodedClass = originalClass
            val decodedMethod = originalMethod

            result = "${prefix}${decodedClass}.${decodedMethod}($source)"
        }

        // Match exception class names: com.example.a -> com.example.RealException
        val exceptionPattern = Regex("""^(\S+Exception|\S+Error):\s*(.*)$""")
        val exMatch = exceptionPattern.find(line.trim())
        if (exMatch != null) {
            val exClass = exMatch.groupValues[1]
            val message = exMatch.groupValues[2]
            val originalEx = mappings.classMap[exClass] ?: exClass
            result = "$originalEx: $message"
        }

        // Also handle "Caused by:" lines
        val causedByPattern = Regex("""^(Caused by:\s*)(\S+)(.*)$""")
        val cbMatch = causedByPattern.find(line.trim())
        if (cbMatch != null) {
            val prefix = cbMatch.groupValues[1]
            val exClass = cbMatch.groupValues[2].removeSuffix(":")
            val rest = cbMatch.groupValues[3]
            val originalEx = mappings.classMap[exClass] ?: exClass
            result = "$prefix$originalEx$rest"
        }

        return result
    }

    private fun parseMappingFile(path: Path): MappingData {
        val classMap = mutableMapOf<String, String>()
        val methodMap = mutableMapOf<String, String>()

        var currentObfuscatedClass = ""
        var currentOriginalClass = ""

        path.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.startsWith("#")) continue // comment

                if (!line.startsWith(" ") && line.contains(" -> ") && line.endsWith(":")) {
                    // Class mapping: com.example.OriginalClass -> a.b.c:
                    val parts = line.removeSuffix(":").split(" -> ")
                    if (parts.size == 2) {
                        val original = parts[0].trim()
                        val obfuscated = parts[1].trim()
                        classMap[obfuscated] = original
                        currentOriginalClass = original
                        currentObfuscatedClass = obfuscated
                    }
                } else if (line.startsWith("    ") && line.contains(" -> ")) {
                    // Member mapping:     returnType originalName(params) -> obfuscatedName
                    val trimmed = line.trim()
                    val arrowIdx = trimmed.lastIndexOf(" -> ")
                    if (arrowIdx > 0) {
                        val leftPart = trimmed.substring(0, arrowIdx)
                        val obfuscatedName = trimmed.substring(arrowIdx + 4)

                        // Extract original method/field name
                        // Format: [linerange:]returnType originalName(params)
                        val withoutLineRange = if (leftPart.contains(":")) {
                            leftPart.substringAfter(":")
                        } else {
                            leftPart
                        }

                        // Remove another line range if present (format: startLine:endLine:type name)
                        val cleaned = if (withoutLineRange.contains(":")) {
                            withoutLineRange.substringAfter(":")
                        } else {
                            withoutLineRange
                        }

                        val namePart = cleaned.trim().split(" ").getOrNull(1)?.substringBefore("(") ?: continue
                        val isMethod = cleaned.contains("(")

                        if (isMethod) {
                            methodMap["$currentObfuscatedClass.$obfuscatedName"] = namePart
                        }
                    }
                }
            }
        }

        return MappingData(classMap, methodMap)
    }

    private data class MappingData(
        val classMap: Map<String, String>,   // obfuscated -> original class name
        val methodMap: Map<String, String>   // obfuscatedClass.obfuscatedMethod -> original method
    )
}
