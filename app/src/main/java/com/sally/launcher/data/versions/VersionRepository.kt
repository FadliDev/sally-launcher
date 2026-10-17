package com.sally.launcher.data.versions

import com.sally.launcher.data.storage.LauncherPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Menyimpan dan membaca metadata versi (`version.json` di tiap folder profil). */
class VersionRepository(private val paths: LauncherPaths) {

    suspend fun list(): List<GameVersion> = withContext(Dispatchers.IO) {
        paths.listProfileIds()
            .mapNotNull { read(it) }
            .sortedByDescending { it.importedAt }
    }

    fun read(id: String): GameVersion? {
        val file = paths.versionFile(id)
        if (!file.isFile) return null
        return runCatching {
            val o = JSONObject(file.readText())
            val abis = o.optJSONArray("abis")
            GameVersion(
                id = o.getString("id"),
                displayName = o.getString("displayName"),
                versionName = o.getString("versionName"),
                versionCode = o.getLong("versionCode"),
                packageName = o.getString("packageName"),
                abis = List(abis?.length() ?: 0) { abis!!.getString(it) },
                sizeBytes = o.optLong("sizeBytes"),
                splitCount = o.optInt("splitCount"),
                importedAt = o.optLong("importedAt"),
            )
        }.getOrNull()
    }

    /** Tulis lewat berkas sementara lalu rename, supaya tidak pernah setengah tertulis. */
    fun write(version: GameVersion) {
        val json = JSONObject()
            .put("schema", 1)
            .put("id", version.id)
            .put("displayName", version.displayName)
            .put("versionName", version.versionName)
            .put("versionCode", version.versionCode)
            .put("packageName", version.packageName)
            .put("abis", JSONArray(version.abis))
            .put("sizeBytes", version.sizeBytes)
            .put("splitCount", version.splitCount)
            .put("importedAt", version.importedAt)
        val target = paths.versionFile(version.id)
        val tmp = File(target.parentFile, "version.json.tmp")
        tmp.writeText(json.toString(2))
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "Gagal menyimpan version.json" }
        }
    }

    suspend fun rename(id: String, displayName: String) = withContext(Dispatchers.IO) {
        read(id)?.let { write(it.copy(displayName = displayName)) }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        paths.profileDir(id).deleteRecursively()
    }
}
