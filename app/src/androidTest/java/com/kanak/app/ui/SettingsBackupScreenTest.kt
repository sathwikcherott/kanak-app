package com.kanak.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kanak.app.data.backup.*
import com.kanak.app.designsystem.theme.KanakAppTheme
import com.kanak.app.ui.settings.SettingsScreen
import com.kanak.app.ui.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsBackupScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun render(state: SettingsUiState, confirm: () -> Unit = {}, cancel: () -> Unit = {}) {
        compose.setContent {
            KanakAppTheme {
                SettingsScreen(state, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, confirm, cancel, {}, {})
            }
        }
    }

    @Test fun validatedPreviewRequiresExplicitConfirmationAndAllowsCancel() {
        var confirmed = 0
        var cancelled = 0
        val summary = KanakBackupSerializer.summarize(KanakBackup())
        render(SettingsUiState(pendingRestoreSummary = summary), { confirmed++ }, { cancelled++ })
        compose.onNodeWithText("Restore Backup?").assertIsDisplayed()
        compose.onNodeWithText("• Expenses Count: 0 records").assertIsDisplayed()
        assertEquals(0, confirmed)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
        assertEquals(0, confirmed)
    }

    @Test fun activeRestoreDisablesConfirmationAndCancellation() {
        val summary = KanakBackupSerializer.summarize(KanakBackup())
        render(SettingsUiState(pendingRestoreSummary = summary, isRestoringBackup = true))
        compose.onNodeWithText("Restoring...").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test fun partialSuccessHasPersistentDistinctWarning() {
        render(SettingsUiState(restoreWarning = "Expenses restored; previous settings kept."))
        compose.onNodeWithText("Expenses restored").assertIsDisplayed()
        compose.onNodeWithText("Expenses restored; previous settings kept.").assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsEnabled()
    }
    @Test fun backupManagementStaysBehindAdvanced() {
        render(SettingsUiState())
        compose.onNodeWithText("Manage backup files").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Advanced"))
        compose.onNodeWithText("Advanced").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Manage backup files"))
        compose.onNodeWithText("Manage backup files").assertIsDisplayed()
    }

}
