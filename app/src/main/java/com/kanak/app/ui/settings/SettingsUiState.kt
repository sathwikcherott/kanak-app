package com.kanak.app.ui.settings

import com.kanak.app.data.backup.BackupSummary
import com.kanak.app.data.local.datastore.ThemePreference

data class SettingsUiState(
    val themePreference: ThemePreference = ThemePreference.DARK,
    val currency: String = "INR",
    val appVersion: String = "1.0 (Build 1)",
    val showCurrencyDialog: Boolean = false,
    val lastBackupExportedTime: Long? = null,
    val pendingRestoreSummary: BackupSummary? = null,
    val isExportingBackup: Boolean = false,
    val isRestoringBackup: Boolean = false,
    val backupError: String? = null,
    val restoreWarning: String? = null,
    val userMessage: String? = null
) {
    val backupBusy: Boolean get() = isExportingBackup || isRestoringBackup
}
