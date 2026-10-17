package com.sally.launcher.data.versions

import androidx.annotation.StringRes
import com.sally.launcher.R
import com.sally.launcher.data.storage.LauncherPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.ZipFile

enum class PrepareFailure(@StringRes val messageRes: Int) {
    NO_MAIN_LIB(R.string.err_prepare_no_main_lib),
    BAD_ELF(R.string.err_prepare_bad_elf),
    IO(R.string.err_io),
}

class PrepareException(val failure: PrepareFailure, val detail: String? = null) : Exception(detail)

/**
 * Mengekstrak library native arm64-v8a dari APK utama dan semua split-nya ke folder profil.
 *
 * Hasil ditulis ke folder sementara, divalidasi (header ELF AArch64), lalu ditukar ke tempat
 * akhir sekaligus. Penanda `.ready` baru ada setelah semuanya berhasil.
 */
class LibraryExtractor(private val paths: LauncherPaths) {

    private class Item(val apk: File, val entry: String, val name: String, val size: Long)

    fun isPrepared(versionId: String): Boolean {
        val dir = paths.abiLibsDir(versionId, LibraryEntries.ABI)
        return File(dir, READY_MARKER).isFile && File(dir, LibraryEntries.MAIN_LIB).isFile
    }

    suspend fun prepare(version: GameVersion, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val id = version.id
        val apks = listOf(paths.baseApk(id)) +
            paths.splitsDir(id).listFiles { f -> f.isFile }.orEmpty().sortedBy { it.name }

        val libsRoot = paths.libsDir(id).also { it.mkdirs() }
        val tmp = File(libsRoot, ".tmp-${LibraryEntries.ABI}")
        val target = paths.abiLibsDir(id, LibraryEntries.ABI)
        tmp.deleteRecursively()
        tmp.mkdirs()

        try {
            val items = collectItems(apks)
            if (LibraryEntries.MAIN_LIB !in items) throw PrepareException(PrepareFailure.NO_MAIN_LIB)

            val total = items.values.sumOf { it.size }.coerceAtLeast(1L)
            var done = 0L
            var lastReported = -1f

            for ((apk, group) in items.values.groupBy { it.apk }) {
                ZipFile(apk).use { zip ->
                    for (item in group) {
                        val entry = zip.getEntry(item.entry) ?: throw IOException("Entri hilang: ${item.entry}")
                        val out = File(tmp, item.name)
                        zip.getInputStream(entry).use { input ->
                            out.outputStream().use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    done += read
                                    val fraction = (done.toFloat() / total).coerceIn(0f, 1f)
                                    if (fraction - lastReported >= 0.01f) {
                                        lastReported = fraction
                                        onProgress(fraction)
                                    }
                                }
                            }
                        }
                        if (!hasArm64Header(out)) throw PrepareException(PrepareFailure.BAD_ELF, item.name)
                        out.setReadOnly()
                    }
                }
            }

            File(tmp, READY_MARKER).apply {
                writeText("libs=${items.size}\nbytes=$total\n")
                setReadOnly()
            }

            target.deleteRecursively()
            if (!tmp.renameTo(target)) throw IOException("Gagal memindahkan library ke ${target.name}")
            onProgress(1f)
        } catch (e: IOException) {
            throw PrepareException(PrepareFailure.IO, e.message)
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Daftar library per nama; bila sama di beberapa APK, yang pertama dipakai. */
    private fun collectItems(apks: List<File>): Map<String, Item> {
        val items = LinkedHashMap<String, Item>()
        for (apk in apks) {
            ZipFile(apk).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val name = LibraryEntries.libName(entry.name) ?: continue
                    items.putIfAbsent(name, Item(apk, entry.name, name, entry.size.coerceAtLeast(0L)))
                }
            }
        }
        return items
    }

    private fun hasArm64Header(file: File): Boolean = try {
        val header = ByteArray(20)
        RandomAccessFile(file, "r").use { it.readFully(header) }
        ElfCheck.isArm64(header)
    } catch (_: IOException) {
        false
    }

    private companion object {
        const val READY_MARKER = ".ready"
        const val BUFFER_SIZE = 256 * 1024
    }
}
