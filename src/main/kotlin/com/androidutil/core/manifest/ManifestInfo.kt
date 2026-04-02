package com.androidutil.core.manifest

data class ManifestInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int?,
    val permissions: List<String>,
    val activities: Int,
    val services: Int,
    val receivers: Int,
    val providers: Int
)
