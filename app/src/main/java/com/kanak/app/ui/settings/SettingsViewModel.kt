package com.kanak.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanak.app.data.backup.RestoreOutcome
import kotlinx.coroutines.CancellationException
import com.kanak.app.data.backup.BackupRepository
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val expenseRepository: ExpenseRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            expenseRepository.getThemePreference().collectLatest { theme ->
                _uiState.update { it.copy(themePreference = theme) }
            }
        }

        viewModelScope.launch {
            expenseRepository.getDefaultCurrency().collectLatest { currency ->
                _uiState.update { it.copy(currency = currency) }
            }
        }

        viewModelScope.launch {
            backupRepository.getLastBackupExportedTime().collectLatest { time ->
                _uiState.update { it.copy(lastBackupExportedTime = time) }
            }
        }
    }

    fun onThemeSelected(theme: ThemePreference) {
        if (_uiState.value.backupBusy || _uiState.value.pendingRestoreSummary != null) return
        _uiState.update { it.copy(themePreference = theme) }
        viewModelScope.launch {
            expenseRepository.setThemePreference(theme)
        }
    }

    fun onCurrencySelected(currencyCode: String) {
        if (_uiState.value.backupBusy || _uiState.value.pendingRestoreSummary != null) return
        _uiState.update { it.copy(currency = currencyCode, showCurrencyDialog = false) }
        viewModelScope.launch {
            expenseRepository.setDefaultCurrency(currencyCode)
        }
    }

    fun openCurrencyDialog() {
        if (_uiState.value.backupBusy || _uiState.value.pendingRestoreSummary != null) return
        _uiState.update { it.copy(showCurrencyDialog = true) }
    }

    fun dismissCurrencyDialog() {
        _uiState.update { it.copy(showCurrencyDialog = false) }
    }

    fun exportBackup(context: Context, uri: Uri) {
        if (_uiState.value.backupBusy || _uiState.value.pendingRestoreSummary != null) return
        _uiState.update { it.copy(isExportingBackup = true, backupError = null, userMessage = null) }
        viewModelScope.launch {
            try {
                backupRepository.exportBackupToUri(context.applicationContext, uri).getOrThrow()
                _uiState.update { it.copy(userMessage = "Backup exported successfully!") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(backupError = e.localizedMessage ?: "Failed to export backup") }
            } finally {
                _uiState.update { it.copy(isExportingBackup = false) }
            }
        }
    }

    fun onRestoreFileSelected(context: Context, uri: Uri) {
        if (_uiState.value.backupBusy || _uiState.value.pendingRestoreSummary != null) return
        _uiState.update { it.copy(isRestoringBackup = true, backupError = null, userMessage = null, restoreWarning = null) }
        viewModelScope.launch {
            try {
                val summary = backupRepository.parseAndValidateBackupFromUri(context.applicationContext, uri).getOrThrow()
                _uiState.update { it.copy(pendingRestoreSummary = summary) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(backupError = "Could not read backup. Your current data has not changed.\n\n${e.localizedMessage ?: "Invalid backup file"}") }
            } finally {
                _uiState.update { it.copy(isRestoringBackup = false) }
            }
        }
    }

    fun confirmRestore() {
        if (_uiState.value.backupBusy) return
        val summary = _uiState.value.pendingRestoreSummary ?: return
        _uiState.update { it.copy(isRestoringBackup = true, backupError = null) }
        viewModelScope.launch {
            try {
                // Apply exactly the validated data previewed; never reopen the external URI.
                val outcome = backupRepository.restoreBackup(summary.backup).getOrThrow()
                _uiState.update {
                    it.copy(
                        pendingRestoreSummary = null,
                        userMessage = if (outcome == RestoreOutcome.COMPLETE) "Backup restored successfully!" else null,
                        restoreWarning = if (outcome == RestoreOutcome.EXPENSES_RESTORED_PREFERENCES_FAILED)
                            "Your expenses were restored successfully, but currency and theme preferences could not be restored. Your previous settings were kept. You can change them in Settings." else null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(pendingRestoreSummary = null, backupError = "Restore failed. Your current data has not changed.\n\n${e.localizedMessage ?: "Database replacement failed"}")
                }
            } finally {
                _uiState.update { it.copy(isRestoringBackup = false) }
            }
        }
    }

    fun cancelRestore() {
        if (!_uiState.value.backupBusy) _uiState.update { it.copy(pendingRestoreSummary = null) }
    }

    fun clearBackupError() {
        _uiState.update { it.copy(backupError = null) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null, restoreWarning = null) }
    }

    class Factory(
        private val expenseRepository: ExpenseRepository,
        private val backupRepository: BackupRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(expenseRepository, backupRepository) as T
        }
    }
}
