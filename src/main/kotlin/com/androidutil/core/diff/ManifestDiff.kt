package com.androidutil.core.diff

import com.androidutil.core.manifest.ManifestInfo
import com.androidutil.core.manifest.ManifestParser
import java.nio.file.Path
import kotlin.io.path.name

data class ManifestDiffResult(
    val oldFile: String,
    val newFile: String,
    val oldManifest: ManifestInfo,
    val newManifest: ManifestInfo,
    val changes: List<ManifestChange>,
    val permissionChanges: PermissionChanges
)

data class ManifestChange(
    val field: String,
    val oldValue: String,
    val newValue: String
)

data class PermissionChanges(
    val added: List<String>,
    val removed: List<String>,
    val unchanged: List<String>
)

class ManifestDiff {

    fun compare(oldFile: Path, newFile: Path): ManifestDiffResult {
        val parser = ManifestParser()
        val oldExt = oldFile.name.substringAfterLast('.').lowercase()
        val newExt = newFile.name.substringAfterLast('.').lowercase()

        val oldManifest = if (oldExt == "apk") parser.parseFromApk(oldFile) else parser.parseFromAab(oldFile)
        val newManifest = if (newExt == "apk") parser.parseFromApk(newFile) else parser.parseFromAab(newFile)

        val changes = mutableListOf<ManifestChange>()

        // Compare basic fields
        if (oldManifest.packageName != newManifest.packageName) {
            changes.add(ManifestChange("Package Name", oldManifest.packageName, newManifest.packageName))
        }
        if (oldManifest.versionCode != newManifest.versionCode) {
            changes.add(ManifestChange("Version Code", oldManifest.versionCode.toString(), newManifest.versionCode.toString()))
        }
        if (oldManifest.versionName != newManifest.versionName) {
            changes.add(ManifestChange("Version Name", oldManifest.versionName, newManifest.versionName))
        }
        if (oldManifest.minSdk != newManifest.minSdk) {
            changes.add(ManifestChange("Min SDK", oldManifest.minSdk.toString(), newManifest.minSdk.toString()))
        }
        if (oldManifest.targetSdk != newManifest.targetSdk) {
            changes.add(ManifestChange("Target SDK", oldManifest.targetSdk.toString(), newManifest.targetSdk.toString()))
        }
        if (oldManifest.compileSdk != newManifest.compileSdk) {
            changes.add(ManifestChange("Compile SDK", (oldManifest.compileSdk ?: 0).toString(), (newManifest.compileSdk ?: 0).toString()))
        }
        if (oldManifest.activities != newManifest.activities) {
            changes.add(ManifestChange("Activities", oldManifest.activities.toString(), newManifest.activities.toString()))
        }
        if (oldManifest.services != newManifest.services) {
            changes.add(ManifestChange("Services", oldManifest.services.toString(), newManifest.services.toString()))
        }
        if (oldManifest.receivers != newManifest.receivers) {
            changes.add(ManifestChange("Receivers", oldManifest.receivers.toString(), newManifest.receivers.toString()))
        }
        if (oldManifest.providers != newManifest.providers) {
            changes.add(ManifestChange("Providers", oldManifest.providers.toString(), newManifest.providers.toString()))
        }

        // Permission diff
        val oldPerms = oldManifest.permissions.toSet()
        val newPerms = newManifest.permissions.toSet()
        val permissionChanges = PermissionChanges(
            added = (newPerms - oldPerms).sorted(),
            removed = (oldPerms - newPerms).sorted(),
            unchanged = (oldPerms.intersect(newPerms)).sorted()
        )

        return ManifestDiffResult(
            oldFile = oldFile.name,
            newFile = newFile.name,
            oldManifest = oldManifest,
            newManifest = newManifest,
            changes = changes,
            permissionChanges = permissionChanges
        )
    }
}
