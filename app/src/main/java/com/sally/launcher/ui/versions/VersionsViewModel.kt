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
import com.sally.launcher.data.versions.PrepareException
import com.sally.launcher.data.versions.PrepareFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pesan sekali tampil (snackbar). `id` membedakan dua pesan yang isinya sama. */
data class UiMessage(
    @StringRes val res: Int,
    val args: List<String> = emptyList(),
    val id: Long = System.nanoTime(),
)

data class PrepareProgress(val version: GameVersion, val fraction: Float)

data class VersionsUiState(
    val loading: Boolean = true,
    val versions: List<GameVersion> = emptyList(),
    val preparedIds: Set<String> = emptySet(),
    val selectedId: String? = null,
    val importing: ImportProgress? = null,
    val preparing: PrepareProgress? = null,
    val message: UiMessage? = null,
) {
    val selected: GameVersion? get() = versions.firstOrNull { it.id == selectedId }
    val selectedPrepared: Boolean get() = selected?.id in preparedIds
}

class VersionsViewModel(private val app: SallyApp) : ViewModel() {

    private val repository = app.versions
    private val importer = app.importer

    private val _state = MutableStateFlow(VersionsUiState())
    val state: StateFlow<VersionsUiState> = _state.asStateFlow()

    private var importJob: Job? = null
    private var prepareJob: Job? = null

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
        val prepared = withContext(Dispatchers.IO) {
            list.filter { app.libraries.isPrepared(it.id) }.map { it.id }.toSet()
        }
        _state.update { it.copy(versions = list, preparedIds = prepared, loading = false) }
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

    fun prepare(version: GameVersion) {
        if (prepareJob?.isActive == true) return
        prepareJob = viewModelScope.launch {
            _state.update { it.copy(preparing = PrepareProgress(version, 0f)) }
            try {
                app.libraries.prepare(version) { fraction ->
                    _state.update { it.copy(preparing = PrepareProgress(version, fraction)) }
                }
                reload()
                _state.update {
                    it.copy(message = UiMessage(R.string.msg_prepared, listOf(version.displayName)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PrepareException) {
                _state.update { it.copy(message = UiMessage(e.failure.messageRes, listOfNotNull(e.detail))) }
            } catch (e: Exception) {
                val failure = PrepareFailure.IO
                _state.update { it.copy(message = UiMessage(failure.messageRes, listOfNotNull(e.message))) }
            } finally {
                _state.update { it.copy(preparing = null) }
            }
        }
    }

    fun cancelPrepare() {
        prepareJob?.cancel()
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
