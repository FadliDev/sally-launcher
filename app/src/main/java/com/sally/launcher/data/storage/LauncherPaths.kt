package com.sally.launcher.data.storage

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Satu-satunya tempat yang tahu tata letak folder launcher.
 *
 * filesDir/launcher/
 *   staging/<uuid>/        area kerja sementara saat impor
 *   profiles/<id>/
 *     version.json         metadata versi
 *     game/base.apk        APK utama
 *     game/splits/*.apk    split APK
 *     libs/                library native hasil ekstrak (Tahap 3)
 *     mods/                mod native milik profil (Tahap 4)
 *     data/                data game, terisolasi per profil
 *     cache/
 */
class LauncherPaths(context: Context) {

    val root: File = File(context.applicationContext.filesDir, "launcher")
    val profilesRoot: File = File(root, "profiles")
    private val stagingRoot: File = File(root, "staging")

    fun profileDir(profileId: String): File = File(profilesRoot, ProfileId.sanitize(profileId))

    fun versionFile(profileId: String): File = File(profileDir(profileId), "version.json")
    fun gameDir(profileId: String): File = File(profileDir(profileId), "game")
    fun baseApk(profileId: String): File = File(gameDir(profileId), "base.apk")
    fun splitsDir(profileId: String): File = File(gameDir(profileId), "splits")
    fun libsDir(profileId: String): File = File(profileDir(profileId), "libs")
    fun modsDir(profileId: String): File = File(profileDir(profileId), "mods")
    fun dataDir(profileId: String): File = File(profileDir(profileId), "data")
    fun cacheDir(profileId: String): File = File(profileDir(profileId), "cache")

    fun profileExists(profileId: String): Boolean = profileDir(profileId).exists()

    /** Buat folder dasar dan bersihkan sisa area kerja dari proses yang terputus. */
    fun ensureBase() {
        profilesRoot.mkdirs()
    }

    fun clearStaging() {
        stagingRoot.deleteRecursively()
    }

    fun newStagingDir(): File = File(stagingRoot, UUID.randomUUID().toString()).also { it.mkdirs() }

    /** Buat seluruh folder sebuah profil. */
    fun ensureProfile(profileId: String): File {
        listOf(gameDir(profileId), splitsDir(profileId), libsDir(profileId), modsDir(profileId), dataDir(profileId), cacheDir(profileId))
            .forEach { it.mkdirs() }
        return profileDir(profileId)
    }

    fun listProfileIds(): List<String> =
        profilesRoot.listFiles { f -> f.isDirectory }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
}
