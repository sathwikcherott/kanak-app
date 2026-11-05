package com.kanak.app.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kanak.app.ui.settings.SettingsScreen
import com.kanak.app.ui.settings.SettingsUiState

@Composable
fun SettingsPlaceholderScreen(
    currency: String,
    modifier: Modifier = Modifier
) {
    SettingsScreen(
        uiState = SettingsUiState(currency = currency),
        onThemeSelected = {},
        onOpenCurrencyDialog = {},
        onCloseCurrencyDialog = {},
        onCurrencySelected = {},
        onExportBackup = { _, _ -> },
        onRestoreFileSelected = { _, _ -> },
        onConfirmRestore = {},
        onCancelRestore = {},
        onClearBackupError = {},
        onClearUserMessage = {},
        modifier = modifier
    )
}
