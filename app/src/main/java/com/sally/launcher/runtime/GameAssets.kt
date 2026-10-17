package com.sally.launcher.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import com.sally.launcher.data.storage.LauncherPaths
import com.sally.launcher.data.versions.GameVersion
import java.io.File

/** AssetManager yang membaca langsung dari APK utama dan split milik versi yang dipilih. */
class GameAssets(private val context: Context, private val paths: LauncherPaths) {

    fun apkFiles(version: GameVersion): List<File> =
        listOf(paths.baseApk(version.id)) +
            paths.splitsDir(version.id).listFiles { f -> f.isFile }.orEmpty().sortedBy { it.name }

    fun create(version: GameVersion): AssetManager {
        val apks = apkFiles(version).filter { it.isFile }
        require(apks.isNotEmpty()) { "APK game tidak ditemukan" }

        val info = ApplicationInfo().apply {
            packageName = version.packageName
            sourceDir = apks.first().absolutePath
            publicSourceDir = sourceDir
            if (apks.size > 1) {
                splitSourceDirs = apks.drop(1).map { it.absolutePath }.toTypedArray()
                splitPublicSourceDirs = splitSourceDirs
            }
        }
        // Jalur resmi lebih dulu; refleksi hanya cadangan untuk perangkat yang menolaknya.
        return try {
            context.packageManager.getResourcesForApplication(info).assets
        } catch (_: Exception) {
            viaReflection(apks)
        }
    }

    @Suppress("DiscouragedPrivateApi")
    private fun viaReflection(apks: List<File>): AssetManager {
        val assets = AssetManager::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val add = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
        for (apk in apks) {
            val cookie = add.invoke(assets, apk.absolutePath) as Int
            check(cookie != 0) { "Gagal menambah ${apk.name} ke AssetManager" }
        }
        return assets
    }
}
