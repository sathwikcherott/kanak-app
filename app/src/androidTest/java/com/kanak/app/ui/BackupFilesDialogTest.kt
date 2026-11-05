package com.kanak.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kanak.app.data.backup.*
import com.kanak.app.designsystem.theme.KanakAppTheme
import com.kanak.app.ui.settings.BackupFilesDialog
import com.kanak.app.ui.settings.BackupFilesUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupFilesDialogTest {
    @get:Rule val compose = createComposeRule()
    private fun file(id: String, time: Long) = BackupFile(BackupDocument(id, "kanak_backup_$id.json", true), time, 100, "test")
    private fun render(state: BackupFilesUiState, confirm: () -> Unit = {}, cancel: () -> Unit = {}, toggle: (String) -> Unit = {}) {
        compose.setContent {
            KanakAppTheme {
                BackupFilesDialog(state, {}, {}, {}, toggle, {}, {}, confirm, cancel)
            }
        }
    }
    @Test fun newestCannotBeSelectedAndDeletionStartsDisabled() {
        var toggles = 0
        render(BackupFilesUiState(folder = "folder", folderName = "Kanak", files = listOf(file("new", 2000), file("old", 1000))), toggle = { toggles++ })
        compose.onNodeWithText("Newest · kept").assertIsDisplayed()
        compose.onNodeWithText("kanak_backup_new.json").assertIsNotEnabled()
        assertEquals(0, toggles)
        compose.onNodeWithText("Delete selected (0)").assertIsNotEnabled()
    }
    @Test fun confirmationListsExactFilesAndDoesNotDeleteOnCancel() {
        var confirms = 0
        var cancels = 0
        val old = file("old", 1000)
        render(BackupFilesUiState(confirmation = listOf(old)), { confirms++ }, { cancels++ })
        compose.onNodeWithText("Delete 1 backup file(s)?").assertIsDisplayed()
        compose.onNodeWithText("kanak_backup_old.json").assertIsDisplayed()
        assertEquals(0, confirms)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancels)
        assertEquals(0, confirms)
    }
    @Test fun activeDeletionDisablesConfirmAndCancel() {
        render(BackupFilesUiState(busy = true, confirmation = listOf(file("old", 1000))))
        compose.onNodeWithText("Deleting...").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }
}
