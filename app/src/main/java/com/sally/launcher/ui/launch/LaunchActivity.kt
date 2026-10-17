package com.sally.launcher.ui.launch

import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sally.launcher.R
import com.sally.launcher.SallyApp
import com.sally.launcher.data.settings.ThemeMode
import com.sally.launcher.runtime.GameAssets
import com.sally.launcher.runtime.LaunchLog
import com.sally.launcher.runtime.LoadStatus
import com.sally.launcher.runtime.NativeLoader
import com.sally.launcher.runtime.Preflight
import com.sally.launcher.ui.theme.SallyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Phase { WORKING, DONE, PARTIAL, FAILED }

/**
 * Layar uji muat (Tahap 3b-1). Berjalan di proses `:game` terpisah karena library native yang
 * sudah dimuat tidak bisa dibongkar; proses ini dimatikan saat layar ditutup, jadi launcher utama
 * tidak ikut terkontaminasi.
 */
class LaunchActivity : ComponentActivity() {

    private val log = mutableStateListOf<String>()
    private var phase by mutableStateOf(Phase.WORKING)
    private var summary by mutableStateOf<String?>(null)
    private var launchLog: LaunchLog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Proses :game yang dipulihkan sistem tidak punya status; kembali ke launcher saja.
        if (savedInstanceState != null) {
            finishAndRemoveTask()
            return
        }

        setContent {
            SallyTheme(themeMode = ThemeMode.SYSTEM, dynamicColor = true) {
                LaunchScreen(
                    phase = phase,
                    summary = summary,
                    log = log,
                    onBack = { finishAndRemoveTask() },
                )
            }
        }

        val versionId = intent.getStringExtra(EXTRA_VERSION_ID)
        lifecycleScope.launch { run(versionId) }
    }

    private suspend fun run(versionId: String?) {
        val app = application as SallyApp
        launchLog = LaunchLog(java.io.File(app.paths.root, "last-launch.log")).also { l ->
            val crashed = l.previousCrash()
            l.reset()
            if (crashed != null) addLog("Peluncuran sebelumnya berhenti mendadak setelah: $crashed")
        }
        val version = versionId?.let { withContext(Dispatchers.IO) { app.versions.read(it) } }
        if (version == null) {
            fail(getString(R.string.err_launch_no_version))
            return
        }
        addLog("Versi: ${version.displayName} (${version.versionName})")

        val report = withContext(Dispatchers.IO) { Preflight(app.paths, app.libraries).run(version) }
        val issue = report.issue
        val plan = report.plan
        if (issue != null || plan == null || report.libsDir == null) {
            fail(getString(issue?.messageRes ?: R.string.err_io, report.detail.orEmpty()))
            return
        }
        addLog("Urutan muat: ${plan.order.joinToString()}")
        plan.unresolved.forEach { (dep, askers) ->
            addLog("Belum tersedia: $dep (dibutuhkan ${askers.joinToString()})")
        }

        val assetsOk = withContext(Dispatchers.IO) {
            try {
                val assets = GameAssets(this@LaunchActivity, app.paths).create(version)
                val count = assets.list("")?.size ?: 0
                addLogLater("Aset game terbaca ($count entri di akar)")
                true
            } catch (e: Exception) {
                addLogLater("Aset game gagal dibuka: ${e.message}")
                false
            }
        }

        val results = withContext(Dispatchers.IO) {
            NativeLoader.loadAll(
                report.libsDir,
                plan,
                deferred = setOf(MAIN_LIB),
                onBeforeLoad = { name -> addLogLater("MEMUAT  $name") },
            ) { r ->
                val line = when (r.status) {
                    LoadStatus.DEFERRED -> "DITUNDA  ${r.name}: ${r.detail}"
                    LoadStatus.LOADED -> "OK      ${r.name} (${r.millis} ms)"
                    LoadStatus.FAILED -> "GAGAL   ${r.name}: ${r.detail}"
                    LoadStatus.BLOCKED -> "TERTAHAN ${r.name}: ${r.detail}"
                }
                addLogLater(line)
            }
        }

        launchLog?.append(LaunchLog.DONE)
        val failed = results.count { it.status == LoadStatus.FAILED }
        val blocked = results.count { it.status == LoadStatus.BLOCKED }
        when {
            failed > 0 || !assetsOk -> {
                phase = Phase.FAILED
                summary = getString(R.string.launch_failed)
            }
            blocked > 0 -> {
                phase = Phase.PARTIAL
                summary = getString(R.string.launch_partial, blocked)
            }
            results.any { it.status == LoadStatus.DEFERRED } -> {
                phase = Phase.DONE
                summary = getString(R.string.launch_deferred)
            }
            else -> {
                phase = Phase.DONE
                summary = getString(R.string.launch_done)
            }
        }
    }

    private fun fail(message: String) {
        addLog(message)
        phase = Phase.FAILED
        summary = message
    }

    private fun addLog(line: String) {
        launchLog?.append(line)
        log.add(line)
    }

    private fun addLogLater(line: String) {
        launchLog?.append(line)
        runOnUiThread { log.add(line) }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Library native tidak bisa di-unload: matikan seluruh proses :game saat layar ditutup.
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    companion object {
        const val EXTRA_VERSION_ID = "com.sally.launcher.extra.VERSION_ID"
        private const val MAIN_LIB = "libminecraftpe.so"
    }
}

@Composable
private fun LaunchScreen(phase: Phase, summary: String?, log: List<String>, onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.launch_title), style = MaterialTheme.typography.headlineSmall)
            if (phase == Phase.WORKING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.launch_running), style = MaterialTheme.typography.bodyMedium)
            } else if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (phase == Phase.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }

            val listState = rememberLazyListState()
            LaunchedEffect(log.size) {
                if (log.isNotEmpty()) listState.animateScrollToItem(log.lastIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(log) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }

            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.launch_back))
            }
        }
    }
}
