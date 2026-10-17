package com.sally.launcher.data.versions

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import com.sally.launcher.R
import com.sally.launcher.data.storage.LauncherPaths
import com.sally.launcher.data.storage.ProfileId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

enum class ImportFailure(@StringRes val messageRes: Int) {
    NO_FILES(R.string.err_no_files),
    NOT_APK(R.string.err_not_apk),
    WRONG_PACKAGE(R.string.err_wrong_package),
    MIXED_VERSIONS(R.string.err_mixed_versions),
    NO_BASE(R.string.err_no_base),
    MULTIPLE_BASE(R.string.err_multiple_base),
    NO_ARM64(R.string.err_no_arm64),
    NOT_INSTALLED(R.string.err_not_installed),
    IO(R.string.err_io),
}

class ImportException(val failure: ImportFailure, val detail: String? = null) : Exception(detail)

enum class ImportStage { COPY, VERIFY, FINISH }

/** `fraction` null berarti belum bisa dihitung. */
data class ImportProgress(val stage: ImportStage, val fraction: Float? = null)

sealed interface ApkSource {
    data class Content(val uri: Uri) : ApkSource
    data class Disk(val file: File) : ApkSource
}

/**
 * Mengimpor APK Minecraft ke profil baru.
 *
 * Alur: salin ke staging → buka bundle bila perlu → verifikasi → pindahkan ke profil → tulis version.json.
 * Semua kegagalan membersihkan staging dan profil setengah jadi.
 */
class ApkImporter(
    private val context: Context,
    private val paths: LauncherPaths,
    private val repository: VersionRepository,
) {
    private val inspector = ApkInspector(context)

    private class Checked(val file: File, val meta: ApkMeta?)

    /** Sumber dari Minecraft yang terpasang: APK utama + semua split-nya. */
    fun installedSources(): List<ApkSource> {
        val info = try {
            context.packageManager.getApplicationInfo(MC_PACKAGE, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            throw ImportException(ImportFailure.NOT_INSTALLED)
        }
        val files = buildList {
            add(info.sourceDir)
            info.splitSourceDirs?.let { addAll(it) }
        }
        return files.map { ApkSource.Disk(File(it)) }
    }

    suspend fun import(sources: List<ApkSource>, onProgress: (ImportProgress) -> Unit): GameVersion =
        withContext(Dispatchers.IO) {
            if (sources.isEmpty()) throw ImportException(ImportFailure.NO_FILES)
            paths.ensureBase()
            val staging = paths.newStagingDir()
            try {
                val files = copyAll(sources, staging, onProgress)
                onProgress(ImportProgress(ImportStage.VERIFY))
                val (base, splits) = verify(files)
                onProgress(ImportProgress(ImportStage.FINISH))
                commit(base, splits)
            } catch (e: IOException) {
                throw ImportException(ImportFailure.IO, e.message)
            } finally {
                staging.deleteRecursively()
            }
        }

    // ---- 1. salin ----

    private suspend fun copyAll(sources: List<ApkSource>, staging: File, onProgress: (ImportProgress) -> Unit): List<File> {
        val total = sources.sumOf { sizeOf(it) }.coerceAtLeast(1L)
        var done = 0L
        var lastReported = -1f
        val result = mutableListOf<File>()

        for ((index, source) in sources.withIndex()) {
            val name = FileNames.safe(nameOf(source), "file$index.apk")
            val target = File(staging, FileNames.unique(name) { File(staging, it).exists() })

            openStream(source).use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val fraction = (done.toFloat() / total).coerceIn(0f, 1f)
                        if (fraction - lastReported >= 0.01f || fraction >= 1f) {
                            lastReported = fraction
                            onProgress(ImportProgress(ImportStage.COPY, fraction))
                        }
                    }
                }
            }

            if (inspector.isBundle(target)) {
                unpackBundle(target, staging, result)
                target.delete()
            } else {
                result += target
            }
        }
        return result
    }

    private suspend fun unpackBundle(bundle: File, staging: File, out: MutableList<File>) {
        ZipFile(bundle).use { zip ->
            for (entry in zip.entries()) {
                currentCoroutineContext().ensureActive()
                if (entry.isDirectory || !entry.name.endsWith(".apk", ignoreCase = true)) continue
                // Hanya nama berkas yang dipakai, path di dalam zip diabaikan (cegah zip-slip).
                val name = FileNames.safe(entry.name, "split.apk")
                val target = File(staging, FileNames.unique(name) { File(staging, it).exists() })
                zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                out += target
            }
        }
    }

    private fun openStream(source: ApkSource): InputStream = when (source) {
        is ApkSource.Disk -> source.file.inputStream()
        is ApkSource.Content -> context.contentResolver.openInputStream(source.uri)
            ?: throw IOException("Tidak bisa membuka ${source.uri}")
    }

    private fun nameOf(source: ApkSource): String? = when (source) {
        is ApkSource.Disk -> source.file.name
        is ApkSource.Content -> queryColumn(source.uri, OpenableColumns.DISPLAY_NAME) ?: source.uri.lastPathSegment
    }

    private fun sizeOf(source: ApkSource): Long = when (source) {
        is ApkSource.Disk -> source.file.length()
        is ApkSource.Content -> queryColumn(source.uri, OpenableColumns.SIZE)?.toLongOrNull() ?: 0L
    }

    private fun queryColumn(uri: Uri, column: String): String? = try {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    // ---- 2. verifikasi ----

    /** Mengembalikan (APK utama, daftar split). */
    private fun verify(files: List<File>): Pair<Checked, List<Checked>> {
        if (files.isEmpty()) throw ImportException(ImportFailure.NO_FILES)

        val checked = files.map { file ->
            val meta = inspector.read(file)
            // Split murni kadang tidak terbaca PackageManager; boleh asal bentuknya APK.
            if (meta == null && !inspector.looksLikeApk(file)) {
                throw ImportException(ImportFailure.NOT_APK, file.name)
            }
            Checked(file, meta)
        }

        checked.mapNotNull { it.meta }.firstOrNull { it.packageName != MC_PACKAGE }?.let {
            throw ImportException(ImportFailure.WRONG_PACKAGE, it.packageName)
        }
        if (checked.mapNotNull { it.meta?.versionCode }.distinct().size > 1) {
            throw ImportException(ImportFailure.MIXED_VERSIONS)
        }

        var candidates = checked.filter { it.meta?.hasActivities == true }
        if (candidates.size > 1) candidates = candidates.filter { it.file.name == "base.apk" }
        val base = when (candidates.size) {
            1 -> candidates.first()
            0 -> throw ImportException(ImportFailure.NO_BASE)
            else -> throw ImportException(ImportFailure.MULTIPLE_BASE)
        }

        if (files.none { inspector.hasEntry(it, MAIN_LIB_ENTRY) }) {
            throw ImportException(ImportFailure.NO_ARM64)
        }
        return base to checked.filter { it !== base }
    }

    // ---- 3. simpan ----

    private fun commit(base: Checked, splits: List<Checked>): GameVersion {
        val meta = checkNotNull(base.meta)
        val id = pickId(ProfileId.sanitize(meta.versionName))
        paths.ensureProfile(id)
        try {
            moveTo(base.file, paths.baseApk(id))
            for (split in splits) {
                moveTo(split.file, File(paths.splitsDir(id), split.file.name))
            }

            val stored = listOf(paths.baseApk(id)) + (paths.splitsDir(id).listFiles()?.toList().orEmpty())
            stored.forEach { it.setReadOnly() } // wajib read-only untuk dimuat Android 14+

            val version = GameVersion(
                id = id,
                displayName = "Minecraft ${meta.versionName}",
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                packageName = meta.packageName,
                abis = stored.flatMap { inspector.nativeAbis(it) }.distinct().sorted(),
                sizeBytes = stored.sumOf { it.length() },
                splitCount = splits.size,
                importedAt = System.currentTimeMillis(),
            )
            repository.write(version)
            return version
        } catch (e: Throwable) {
            paths.profileDir(id).deleteRecursively()
            throw e
        }
    }

    private fun pickId(base: String): String {
        if (!paths.profileExists(base)) return base
        var n = 2
        while (paths.profileExists("$base-$n")) n++
        return "$base-$n"
    }

    private fun moveTo(from: File, to: File) {
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }

    companion object {
        const val MC_PACKAGE = "com.mojang.minecraftpe"
        const val MAIN_LIB_ENTRY = "lib/arm64-v8a/libminecraftpe.so"
        private const val BUFFER_SIZE = 256 * 1024
    }
}
