package com.sally.launcher.data.versions

/** Aturan memilih entri library di dalam APK. Murni Kotlin supaya mudah dites. */
object LibraryEntries {
    const val ABI = "arm64-v8a"
    const val MAIN_LIB = "libminecraftpe.so"

    private val NAME = Regex("^[A-Za-z0-9_.+-]+\\.so$")

    /**
     * Nama berkas library bila entri zip adalah `lib/arm64-v8a/<nama>.so` langsung
     * (tanpa subfolder dan tanpa trik path), selain itu null.
     */
    fun libName(entry: String): String? {
        val parts = entry.split('/')
        if (parts.size != 3 || parts[0] != "lib" || parts[1] != ABI) return null
        val name = parts[2]
        if (name.startsWith(".") || name.contains("..")) return null
        return if (NAME.matches(name)) name else null
    }
}

/** Pemeriksaan header ELF: harus 64-bit, little-endian, mesin AArch64. */
object ElfCheck {
    private const val EM_AARCH64 = 0xB7

    fun isArm64(header: ByteArray): Boolean =
        header.size >= 20 &&
            header[0] == 0x7F.toByte() &&
            header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[4] == 2.toByte() && // ELFCLASS64
            header[5] == 1.toByte() && // little-endian
            (header[18].toInt() and 0xFF) == EM_AARCH64 &&
            header[19].toInt() == 0
}
