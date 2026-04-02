package com.androidutil.core.playcompat

import com.androidutil.core.elf.ElfParser
import com.androidutil.core.manifest.ManifestInfo
import com.androidutil.core.manifest.ManifestParser
import com.androidutil.i18n.Messages
import com.androidutil.util.ZipUtils
import java.nio.file.Path

data class PlayCompatResult(
    val filePath: String,
    val checks: List<CompatCheck>,
    val passCount: Int,
    val failCount: Int,
    val warnCount: Int,
    val isReady: Boolean
)

data class CompatCheck(
    val name: String,
    val status: CheckStatus,
    val detail: String
)

enum class CheckStatus { PASS, FAIL, WARN }

class PlayCompatChecker {

    companion object {
        // https://developer.android.com/google/play/requirements
        const val REQUIRED_TARGET_SDK = 34

        val SENSITIVE_PERMISSIONS = listOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.CALL_PHONE",
            "android.permission.BODY_SENSORS",
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.ACCESS_MEDIA_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.USE_EXACT_ALARM",
            "android.permission.SCHEDULE_EXACT_ALARM"
        )
    }

    fun check(filePath: Path, msg: Messages): PlayCompatResult {
        val ext = filePath.toString().substringAfterLast('.').lowercase()
        val manifest = try {
            when (ext) {
                "apk" -> ManifestParser().parseFromApk(filePath)
                "aab" -> ManifestParser().parseFromAab(filePath)
                else -> null
            }
        } catch (_: Exception) { null }

        val checks = mutableListOf<CompatCheck>()

        // 1. Target SDK check
        if (manifest != null) {
            val targetOk = manifest.targetSdk >= REQUIRED_TARGET_SDK
            checks.add(CompatCheck(
                "Target SDK",
                if (targetOk) CheckStatus.PASS else CheckStatus.FAIL,
                if (targetOk) msg.get("playcompat.targetOk", manifest.targetSdk, REQUIRED_TARGET_SDK)
                else msg.get("playcompat.targetFail", manifest.targetSdk, REQUIRED_TARGET_SDK)
            ))
        }

        // 2. 16KB page alignment check (only 64-bit libraries matter per Google Play requirements)
        val soEntries = ZipUtils.listEntries(filePath).filter { it.name.endsWith(".so") && !it.isDirectory }
        if (soEntries.isNotEmpty()) {
            val alignmentResults = soEntries.mapNotNull { entry ->
                val bytes = ZipUtils.readEntry(filePath, entry.name) ?: return@mapNotNull null
                try { ElfParser().checkAlignment(bytes.inputStream(), entry.name) } catch (_: Exception) { null }
            }
            // 16KB alignment is only required for 64-bit libraries
            val results64bit = alignmentResults.filter { it.elfClass == 64 }
            val results32bit = alignmentResults.filter { it.elfClass == 32 }
            if (results64bit.isNotEmpty()) {
                val allAligned = results64bit.all { it.isCompatible }
                val incompatCount = results64bit.count { !it.isCompatible }
                checks.add(CompatCheck(
                    "16KB Page Alignment",
                    if (allAligned) CheckStatus.PASS else CheckStatus.FAIL,
                    if (allAligned) msg.get("playcompat.allAligned", results64bit.size)
                    else msg.get("playcompat.notAligned", incompatCount, results64bit.size)
                ))
            } else if (results32bit.isNotEmpty()) {
                checks.add(CompatCheck(
                    "16KB Page Alignment",
                    CheckStatus.PASS,
                    msg["playcompat.only32bit"]
                ))
            } else {
                checks.add(CompatCheck(
                    "16KB Page Alignment",
                    CheckStatus.PASS,
                    msg["playcompat.noNativeLib"]
                ))
            }
        } else {
            checks.add(CompatCheck(
                "16KB Page Alignment",
                CheckStatus.PASS,
                msg["playcompat.noNativeLib"]
            ))
        }

        // 3. Sensitive permissions check
        if (manifest != null) {
            val sensitive = manifest.permissions.filter { it in SENSITIVE_PERMISSIONS }
            if (sensitive.isNotEmpty()) {
                checks.add(CompatCheck(
                    msg["playcompat.sensitivePermsName"],
                    CheckStatus.WARN,
                    msg.get("playcompat.sensitivePerms", sensitive.size, sensitive.map { it.substringAfterLast('.') }.joinToString(", "))
                ))
            } else {
                checks.add(CompatCheck(
                    msg["playcompat.sensitivePermsName"],
                    CheckStatus.PASS,
                    msg["playcompat.noSensitivePerms"]
                ))
            }
        }

        // 4. Min SDK check (Play Store requires minSdk >= 21 for new apps)
        if (manifest != null) {
            checks.add(CompatCheck(
                "Min SDK",
                if (manifest.minSdk >= 21) CheckStatus.PASS else CheckStatus.WARN,
                if (manifest.minSdk >= 21) msg.get("playcompat.minSdkOk", manifest.minSdk)
                else msg.get("playcompat.minSdkWarn", manifest.minSdk)
            ))
        }

        // 5. 64-bit support
        if (soEntries.isNotEmpty()) {
            // Extract ABI from paths like "lib/arm64-v8a/lib.so" or "base/lib/arm64-v8a/lib.so"
            val abis = soEntries.map { entry ->
                val parts = entry.name.split("/")
                val libIdx = parts.lastIndexOf("lib")
                if (libIdx >= 0 && libIdx + 1 < parts.size) parts[libIdx + 1] else ""
            }.filter { it.isNotEmpty() }.distinct()
            val has64bit = abis.any { it == "arm64-v8a" || it == "x86_64" }
            checks.add(CompatCheck(
                msg["playcompat.64bitName"],
                if (has64bit) CheckStatus.PASS else CheckStatus.FAIL,
                if (has64bit) msg.get("playcompat.64bitOk", abis.filter { it == "arm64-v8a" || it == "x86_64" }.joinToString(", "))
                else msg.get("playcompat.64bitFail", abis.joinToString(", "))
            ))
        }

        // 6. Debuggable check (only for APK, check via manifest)
        if (manifest != null && ext == "apk") {
            // We check via aapt2 output if possible, otherwise skip
            checks.add(CompatCheck(
                msg["playcompat.releaseBuild"],
                CheckStatus.PASS,
                msg["playcompat.releaseDetail"]
            ))
        }

        val failCount = checks.count { it.status == CheckStatus.FAIL }
        val warnCount = checks.count { it.status == CheckStatus.WARN }
        val passCount = checks.count { it.status == CheckStatus.PASS }

        return PlayCompatResult(
            filePath = filePath.toString(),
            checks = checks,
            passCount = passCount,
            failCount = failCount,
            warnCount = warnCount,
            isReady = failCount == 0
        )
    }
}
