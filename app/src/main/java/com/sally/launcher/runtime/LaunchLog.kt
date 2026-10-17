package com.sally.launcher.runtime

import java.io.File
import java.io.FileOutputStream

/**
 * Log peluncuran yang ditulis ke disk (fsync) baris demi baris, supaya jejaknya tetap ada
 * walau proses game mati mendadak karena crash native.
 */
class LaunchLog(private val file: File) {

    /** Baris terakhir log sebelumnya bila peluncuran itu tidak selesai; null bila normal. */
    fun previousCrash(): String? {
        if (!file.isFile) return null
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        if (lines.isEmpty() || lines.any { it == DONE }) return null
        return lines.lastOrNull { it.isNotBlank() }
    }

    fun reset() {
        file.parentFile?.mkdirs()
        runCatching { file.writeText("") }
    }

    fun append(line: String) {
        try {
            FileOutputStream(file, true).use {
                it.write((line + "\n").toByteArray())
                it.fd.sync()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val DONE = "== SELESAI =="
    }
}
