package com.sally.launcher

import android.app.Application
import com.sally.launcher.data.settings.SettingsRepository
import com.sally.launcher.data.storage.LauncherPaths
import com.sally.launcher.data.versions.ApkImporter
import com.sally.launcher.data.versions.LibraryExtractor
import com.sally.launcher.data.versions.VersionRepository

class SallyApp : Application() {

    lateinit var paths: LauncherPaths
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var versions: VersionRepository
        private set
    lateinit var importer: ApkImporter
        private set
    lateinit var libraries: LibraryExtractor
        private set

    override fun onCreate() {
        super.onCreate()
        paths = LauncherPaths(this).also {
            it.ensureBase()
            // Proses :game (Tahap 3b) ikut membuat SallyApp; jangan hapus area kerja impor
            // yang sedang dipakai proses utama.
            if (Application.getProcessName() == packageName) it.clearStaging()
        }
        settings = SettingsRepository(this)
        versions = VersionRepository(paths)
        importer = ApkImporter(this, paths, versions)
        libraries = LibraryExtractor(paths)
    }
}
