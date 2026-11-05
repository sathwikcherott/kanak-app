package com.kanak.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
        _uiState.update { it.copy(themePreference = theme) }
        viewModelScope.launch {
            expenseRepository.setThemePreference(theme)
        }
    }

    fun onCurrencySelected(currencyCode: String) {
        _uiState.update { it.copy(currency = currencyCode, showCurrencyDialog = false) }
        viewModelScope.launch {
            expenseRepository.setDefaultCurrency(currencyCode)
        }
    }

    fun openCurrencyDialog() {
        _uiState.update { it.copy(showCurrencyDialog = true) }
    }

    fun dismissCurrencyDialog() {
        _uiState.update { it.copy(showCurrencyDialog = false) }
    }

    fun exportBackup(context: Context, uri: Uri) {
        _uiState.update { it.copy(isExportingBackup = true) }
        viewModelScope.launch {
            val result = backupRepository.exportBackupToUri(context, uri)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isExportingBackup = false,
                        userMessage = "Backup exported successfully!"
                    )
                } else {
                    it.copy(
                        isExportingBackup = false,
                        backupError = result.exceptionOrNull()?.localizedMessage ?: "Failed to export backup"
                    )
                }
            }
        }
    }

    fun onRestoreFileSelected(context: Context, uri: Uri) {
        _uiState.update { it.copy(isRestoringBackup = true) }
        viewModelScope.launch {
            val result = backupRepository.parseAndValidateBackupFromUri(context, uri)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isRestoringBackup = false,
                        pendingRestoreSummary = result.getOrNull()
                    )
                } else {
                    it.copy(
                        isRestoringBackup = false,
                        backupError = result.exceptionOrNull()?.localizedMessage ?: "Failed to parse backup file"
                    )
                }
            }
        }
    }

    fun confirmRestore() {
        val summary = _uiState.value.pendingRestoreSummary ?: return
        _uiState.update { it.copy(isRestoringBackup = true) }
        viewModelScope.launch {
            val result = backupRepository.restoreBackup(summary.backup)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isRestoringBackup = false,
                        pendingRestoreSummary = null,
                        userMessage = "Backup restored successfully!"
                    )
                } else {
                    it.copy(
                        isRestoringBackup = false,
                        pendingRestoreSummary = null,
                        backupError = result.exceptionOrNull()?.localizedMessage ?: "Failed to restore backup"
                    )
                }
            }
        }
    }

    fun cancelRestore() {
        _uiState.update { it.copy(pendingRestoreSummary = null) }
    }

    fun clearBackupError() {
        _uiState.update { it.copy(backupError = null) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
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
