package com.kanak.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanak.app.data.backup.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupFilesUiState(
    val folder: String? = null,
    val folderName: String? = null,
    val files: List<BackupFile> = emptyList(),
    val selected: Set<String> = emptySet(),
    val confirmation: List<BackupFile> = emptyList(),
    val busy: Boolean = false,
    val skipped: Int = 0,
    val error: String? = null,
    val message: String? = null
)

class BackupFilesViewModel(
    private val catalog: BackupFileCatalog,
    private val folders: BackupFolderAccess,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val state = MutableStateFlow(BackupFilesUiState())
    val uiState = state.asStateFlow()

    fun refresh() = load(null)
    fun chooseFolder(folder: String) = load(folder)

    private fun load(chosen: String?) {
        if (state.value.busy || state.value.confirmation.isNotEmpty()) return
        state.update { it.copy(busy = true, error = null, message = null, selected = emptySet(), files = emptyList()) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    if (chosen != null) folders.chooseFolder(chosen)
                    val folder = folders.currentFolder()
                    state.update { it.copy(folder = folder, folderName = null, skipped = 0) }
                    if (folder != null) {
                        val name = folders.name(folder)
                        val listing = catalog.scan(folder)
                        state.update { it.copy(folderName = name, files = listing.files, skipped = listing.skipped) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = if (e is SecurityException) "Folder access is unavailable. Choose the folder again and allow access." else e.localizedMessage ?: "Cannot read this folder. Choose it again to grant access.") }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    fun toggle(id: String) {
        val current = state.value
        if (current.busy || current.confirmation.isNotEmpty() || id == current.files.firstOrNull()?.document?.id) return
        if (current.files.none { it.document.id == id && it.document.canDelete }) return
        state.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }
    }

    fun selectOlder() {
        if (state.value.busy || state.value.confirmation.isNotEmpty()) return
        state.update { it.copy(selected = BackupFileCatalog.olderFiles(it.files)) }
    }

    fun requestDeletion() {
        if (state.value.busy) return
        state.update { current -> current.copy(confirmation = current.files.filter { it.document.id in current.selected }) }
    }

    fun cancelDeletion() {
        if (!state.value.busy) state.update { it.copy(confirmation = emptyList()) }
    }

    fun confirmDeletion() {
        val current = state.value
        val folder = current.folder ?: return
        if (current.busy || current.confirmation.isEmpty()) return
        val confirmed = current.confirmation.toList()
        state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    val result = catalog.deleteSelected(folder, confirmed)
                    state.update {
                        it.copy(
                            confirmation = emptyList(), selected = emptySet(), files = emptyList(),
                            message = "Deleted ${result.deleted} backup file(s)." +
                                (if (result.failed > 0) " ${result.failed} could not be deleted or had changed." else "") +
                                (if (result.protected > 0) " The newest backup was kept." else "")
                        )
                    }
                    val listing = catalog.scan(folder)
                    state.update { it.copy(files = listing.files, skipped = listing.skipped) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A provider can fail after some deletes. Do not claim the batch was rolled back.
                state.update { it.copy(confirmation = emptyList(), selected = emptySet(), files = emptyList(), error = "Cleanup could not finish. Some files may have been deleted. Refresh to check the folder.\n\n${e.localizedMessage ?: "Folder unavailable"}") }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    class Factory(private val catalog: BackupFileCatalog, private val folders: BackupFolderAccess) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BackupFilesViewModel(catalog, folders) as T
    }
}
