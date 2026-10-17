package com.sally.launcher.runtime

import android.annotation.SuppressLint
import android.os.SystemClock
import java.io.File

enum class LoadStatus { LOADED, FAILED, BLOCKED, DEFERRED }

data class LoadResult(val name: String, val status: LoadStatus, val detail: String?, val millis: Long)

/** Memuat library hasil ekstrak sesuai rencana. Dipanggil hanya di proses game. */
object NativeLoader {

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun loadAll(
        dir: File,
        plan: LoadPlan,
        deferred: Set<String> = emptySet(),
        onBeforeLoad: (String) -> Unit = {},
        onResult: (LoadResult) -> Unit = {},
    ): List<LoadResult> {
        val results = ArrayList<LoadResult>()
        val unusable = HashSet<String>() // gagal atau terblokir
        val directlyBlocked = plan.blockedByUnresolved

        for (name in plan.order) {
            val started = SystemClock.elapsedRealtime()
            val dependsOnUnusable = plan.requires[name].orEmpty().any { it in unusable }
            val result = when {
                name in deferred -> LoadResult(name, LoadStatus.DEFERRED, "ditunda sampai glue game siap (3b-2)", 0L)
                name in directlyBlocked || dependsOnUnusable -> {
                    val missing = plan.unresolved.filterValues { name in it }.keys
                    val detail = if (missing.isNotEmpty()) "butuh ${missing.joinToString()}" else "butuh library yang gagal"
                    LoadResult(name, LoadStatus.BLOCKED, detail, 0L)
                }
                else -> try {
                    onBeforeLoad(name)
                    System.load(File(dir, name).absolutePath)
                    LoadResult(name, LoadStatus.LOADED, null, SystemClock.elapsedRealtime() - started)
                } catch (e: UnsatisfiedLinkError) {
                    LoadResult(name, LoadStatus.FAILED, e.message, SystemClock.elapsedRealtime() - started)
                } catch (e: Exception) {
                    LoadResult(name, LoadStatus.FAILED, e.message ?: e.javaClass.simpleName, SystemClock.elapsedRealtime() - started)
                }
            }
            if (result.status != LoadStatus.LOADED) unusable.add(name)
            results.add(result)
            onResult(result)
        }
        return results
    }
}
