package com.sally.launcher.data.storage

import android.content.Context
import java.io.File

/**
 * Satu-satunya tempat yang tahu tata letak folder launcher.
 *
 * filesDir/launcher/
 *   profiles/<id>/
 *     game/    APK dan split hasil impor
 *     libs/    library native hasil ekstrak
 *     mods/    mod native milik profil
 *     data/    data game (terisolasi per profil)
 *     cache/
 */
class LauncherPaths(context: Context) {

    val root: File = File(context.applicationContext.filesDir, "launcher")
    val profilesRoot: File = File(root, "profiles")

    fun profileDir(profileId: String): File = File(profilesRoot, ProfileId.sanitize(profileId))

    fun gameDir(profileId: String): File = File(profileDir(profileId), "game")
    fun libsDir(profileId: String): File = File(profileDir(profileId), "libs")
    fun modsDir(profileId: String): File = File(profileDir(profileId), "mods")
    fun dataDir(profileId: String): File = File(profileDir(profileId), "data")
    fun cacheDir(profileId: String): File = File(profileDir(profileId), "cache")

    /** Buat folder dasar. Aman dipanggil berulang. */
    fun ensureBase() {
        profilesRoot.mkdirs()
    }

    /** Buat seluruh folder sebuah profil. */
    fun ensureProfile(profileId: String): File {
        val dir = profileDir(profileId)
        listOf(gameDir(profileId), libsDir(profileId), modsDir(profileId), dataDir(profileId), cacheDir(profileId))
            .forEach { it.mkdirs() }
        return dir
    }

    fun listProfileIds(): List<String> =
        profilesRoot.listFiles { f -> f.isDirectory }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
}
