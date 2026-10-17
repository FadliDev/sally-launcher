package com.sally.launcher.data.versions

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.util.zip.ZipFile

data class ApkMeta(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val hasActivities: Boolean,
)

/** Membaca informasi dari berkas APK tanpa memasangnya. */
class ApkInspector(private val context: Context) {

    /** Null bila sistem tidak bisa membaca manifest (mis. split murni atau berkas rusak). */
    fun read(file: File): ApkMeta? {
        val info = try {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_ACTIVITIES)
        } catch (_: Exception) {
            null
        }
        if (info == null) return null
        val pkg = info.packageName ?: return null
        return ApkMeta(
            packageName = pkg,
            versionName = info.versionName ?: "unknown",
            versionCode = info.longVersionCode,
            hasActivities = !info.activities.isNullOrEmpty(),
        )
    }

    /** Berkas zip yang punya AndroidManifest.xml di akar = bentuk dasar APK. */
    fun looksLikeApk(file: File): Boolean = hasEntry(file, "AndroidManifest.xml")

    /** Berkas zip tanpa manifest tapi berisi .apk = bundle (.apks/.xapk/.apkm). */
    fun isBundle(file: File): Boolean = try {
        ZipFile(file).use { zip ->
            zip.getEntry("AndroidManifest.xml") == null &&
                zip.entries().asSequence().any { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
        }
    } catch (_: Exception) {
        false
    }

    fun hasEntry(file: File, entryName: String): Boolean = try {
        ZipFile(file).use { it.getEntry(entryName) != null }
    } catch (_: Exception) {
        false
    }

    /** ABI yang punya library native di dalam APK ini (isi folder lib/<abi>/). */
    fun nativeAbis(file: File): Set<String> = try {
        ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("lib/") && it.endsWith(".so") }
                .mapNotNull { it.split('/').getOrNull(1) }
                .toSet()
        }
    } catch (_: Exception) {
        emptySet()
    }
}
