package com.sally.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sally.launcher.data.settings.AppSettings
import com.sally.launcher.ui.LauncherRoot
import com.sally.launcher.ui.theme.SallyTheme
import com.sally.launcher.ui.versions.VersionsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val versionsViewModel: VersionsViewModel by viewModels {
        VersionsViewModel.Factory(application as SallyApp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as SallyApp

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val scope = rememberCoroutineScope()

            SallyTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                LauncherRoot(
                    settings = settings,
                    storageRoot = app.paths.root.absolutePath,
                    versionsViewModel = versionsViewModel,
                    onThemeMode = { mode -> scope.launch { app.settings.setThemeMode(mode) } },
                    onDynamicColor = { on -> scope.launch { app.settings.setDynamicColor(on) } },
                )
            }
        }
    }
}
