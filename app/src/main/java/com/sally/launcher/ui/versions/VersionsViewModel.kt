package com.sally.launcher.ui.versions

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sally.launcher.R
import com.sally.launcher.SallyApp
import com.sally.launcher.data.versions.ApkSource
import com.sally.launcher.data.versions.GameVersion
import com.sally.launcher.data.versions.ImportException
import com.sally.launcher.data.versions.ImportFailure
import com.sally.launcher.data.versions.ImportProgress
import com.sally.launcher.data.versions.ImportStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pesan sekali tampil (snackbar). `id` membedakan dua pesan yang isinya sama. */
data class UiMessage(
    @StringRes val res: Int,
    val args: List<String> = emptyList(),
    val id: Long = System.nanoTime(),
)

data class VersionsUiState(
    val loading: Boolean = true,
    val versions: List<GameVersion> = emptyList(),
    val selectedId: String? = null,
    val importing: ImportProgress? = null,
    val message: UiMessage? = null,
) {
    val selected: GameVersion? get() = versions.firstOrNull { it.id == selectedId }
}

class VersionsViewModel(private val app: SallyApp) : ViewModel() {

    private val repository = app.versions
    private val importer = app.importer

    private val _state = MutableStateFlow(VersionsUiState())
    val state: StateFlow<VersionsUiState> = _state.asStateFlow()

    private var importJob: Job? = null

    init {
        viewModelScope.launch {
            app.settings.settings
                .map { it.selectedVersionId }
                .distinctUntilChanged()
                .collect { id -> _state.update { it.copy(selectedId = id) } }
        }
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val list = repository.list()
        _state.update { it.copy(versions = list, loading = false) }
    }

    fun select(id: String) {
        viewModelScope.launch { app.settings.setSelectedVersion(id) }
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.rename(id, trimmed)
            reload()
        }
    }

    fun delete(version: GameVersion) {
        viewModelScope.launch {
            repository.delete(version.id)
            reload()
            if (_state.value.selectedId == version.id) {
                app.settings.setSelectedVersion(_state.value.versions.firstOrNull()?.id)
            }
            _state.update { it.copy(message = UiMessage(R.string.msg_deleted, listOf(version.displayName))) }
        }
    }

    fun importFromUris(uris: List<Uri>) = startImport(uris.map { ApkSource.Content(it) })

    fun importFromInstalled() {
        try {
            startImport(importer.installedSources())
        } catch (e: ImportException) {
            _state.update { it.copy(message = e.toMessage()) }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    fun consumeMessage(id: Long) {
        _state.update { if (it.message?.id == id) it.copy(message = null) else it }
    }

    private fun startImport(sources: List<ApkSource>) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            _state.update { it.copy(importing = ImportProgress(ImportStage.COPY, 0f)) }
            try {
                val version = importer.import(sources) { progress ->
                    _state.update { it.copy(importing = progress) }
                }
                app.settings.setSelectedVersion(version.id)
                reload()
                _state.update {
                    it.copy(message = UiMessage(R.string.msg_import_done, listOf(version.displayName)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                _state.update { it.copy(message = e.toMessage()) }
            } catch (e: Exception) {
                _state.update { it.copy(message = ImportException(ImportFailure.IO, e.message).toMessage()) }
            } finally {
                _state.update { it.copy(importing = null) }
            }
        }
    }

    private fun ImportException.toMessage() =
        UiMessage(failure.messageRes, listOfNotNull(detail))

    class Factory(private val app: SallyApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = VersionsViewModel(app) as T
    }
}
