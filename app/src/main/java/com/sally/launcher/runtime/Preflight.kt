package com.sally.launcher.runtime

import android.os.Build
import androidx.annotation.StringRes
import com.sally.launcher.R
import com.sally.launcher.data.storage.LauncherPaths
import com.sally.launcher.data.versions.GameVersion
import com.sally.launcher.data.versions.LibraryEntries
import com.sally.launcher.data.versions.LibraryExtractor
import java.io.File

enum class PreflightIssue(@StringRes val messageRes: Int) {
    NOT_ARM64(R.string.err_preflight_not_arm64),
    NOT_PREPARED(R.string.err_preflight_not_prepared),
    BAD_ELF(R.string.err_preflight_bad_elf),
    CYCLE(R.string.err_preflight_cycle),
}

data class PreflightReport(
    val issue: PreflightIssue? = null,
    val detail: String? = null,
    val libsDir: File? = null,
    val plan: LoadPlan? = null,
    val skipped: List<String> = emptyList(),
) {
    val ok: Boolean get() = issue == null && plan != null
}

/** Pemeriksaan sebelum game dimuat: perangkat, library siap, dan urutan muatnya. */
class Preflight(private val paths: LauncherPaths, private val libraries: LibraryExtractor) {

    fun run(version: GameVersion): PreflightReport {
        if (!Build.SUPPORTED_64_BIT_ABIS.contains(LibraryEntries.ABI)) {
            return PreflightReport(PreflightIssue.NOT_ARM64)
        }
        if (!libraries.isPrepared(version.id)) {
            return PreflightReport(PreflightIssue.NOT_PREPARED)
        }

        val dir = paths.abiLibsDir(version.id, LibraryEntries.ABI)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".so") && !f.name.startsWith(".") }
            .orEmpty()
        val infos = LinkedHashMap<String, ElfInfo>()
        for (file in files.sortedBy { it.name }) {
            try {
                infos[file.name] = ElfReader.read(file)
            } catch (e: Exception) {
                return PreflightReport(PreflightIssue.BAD_ELF, file.name, dir)
            }
        }

        // Hanya muat yang dibutuhkan library utama; sisanya (mis. proteksi Play di sisi Java) dilewati.
        val keep = LoadOrder.reachable(infos, LibraryEntries.MAIN_LIB)
        val used = infos.filterKeys { it in keep }
        val skipped = infos.keys.filter { it !in keep }.sorted()

        return try {
            PreflightReport(
                libsDir = dir,
                plan = LoadOrder.compute(used) { name -> isSystemLibrary(name) },
                skipped = skipped,
            )
        } catch (e: LoadCycleException) {
            PreflightReport(PreflightIssue.CYCLE, e.message, dir)
        }
    }

    companion object {
        /** Library yang selalu disediakan Android (NDK stable) atau ada di folder sistem. */
        private val NDK_STABLE = setOf(
            "libc.so", "libm.so", "libdl.so", "liblog.so", "libz.so", "libstdc++.so",
            "libandroid.so", "libjnigraphics.so", "libmediandk.so", "libOpenSLES.so",
            "libaaudio.so", "libamidi.so", "libvulkan.so", "libnativewindow.so",
            "libEGL.so", "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so",
            "libcamera2ndk.so", "libsync.so", "libbinder_ndk.so",
        )

        private val SYSTEM_DIRS = listOf(
            "/system/lib64",
            "/apex/com.android.runtime/lib64/bionic",
            "/apex/com.android.art/lib64",
            "/vendor/lib64",
        )

        fun isSystemLibrary(name: String): Boolean =
            name in NDK_STABLE || SYSTEM_DIRS.any { File(it, name).exists() }
    }
}
