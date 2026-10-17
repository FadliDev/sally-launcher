package com.sally.launcher

import android.app.Application
import com.sally.launcher.data.settings.SettingsRepository
import com.sally.launcher.data.storage.LauncherPaths

class SallyApp : Application() {

    lateinit var paths: LauncherPaths
        private set
    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        paths = LauncherPaths(this).also { it.ensureBase() }
        settings = SettingsRepository(this)
    }
}
