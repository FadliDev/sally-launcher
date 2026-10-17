package com.sally.launcher.data.versions

/** Satu versi Minecraft yang sudah diimpor. `id` juga nama folder profilnya. */
data class GameVersion(
    val id: String,
    val displayName: String,
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
    val abis: List<String>,
    val sizeBytes: Long,
    val splitCount: Int,
    val importedAt: Long,
)
