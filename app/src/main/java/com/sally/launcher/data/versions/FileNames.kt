package com.sally.launcher.data.versions

/** Helper nama berkas dan ID versi. Murni Kotlin supaya mudah dites. */
object FileNames {

    /** Ambil nama berkas saja (tanpa path) dan buang karakter aneh. */
    fun safe(raw: String?, fallback: String): String {
        val base = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = base.map { ch ->
            if (ch.isLetterOrDigit() && ch.code < 128 || ch == '.' || ch == '-' || ch == '_') ch else '_'
        }.joinToString("").trim('.')
        return cleaned.ifEmpty { fallback }
    }

    /** Cari nama yang belum dipakai: a.apk, a-2.apk, a-3.apk, ... */
    fun unique(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while (exists("$stem-$n$ext")) n++
        return "$stem-$n$ext"
    }
}
