package com.sally.launcher.data.storage

/**
 * Nama folder profil yang aman dipakai di filesystem.
 * Hanya huruf kecil, angka, titik, strip, dan underscore.
 */
object ProfileId {
    const val DEFAULT = "default"
    private const val MAX_LENGTH = 64

    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return DEFAULT
        val cleaned = buildString {
            for (ch in raw.trim().lowercase()) {
                append(if (ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '-' || ch == '_') ch else '_')
            }
        }
            .trim('.', '_')
            .take(MAX_LENGTH)
            .trim('.', '_')
        return cleaned.ifEmpty { DEFAULT }
    }
}
