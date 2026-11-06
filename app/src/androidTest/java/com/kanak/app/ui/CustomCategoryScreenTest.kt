package com.kanak.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kanak.app.designsystem.theme.KanakAppTheme
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.ui.entry.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CustomCategoryScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun customCategoryIsSelectableAndNewCategoryActionIsVisible() {
        val custom = ExpenseCategory.custom("custom:12345678-1234-1234-1234-123456789012", "Pet care")
        var selected: ExpenseCategory? = null
        var opened = false
        compose.setContent {
            KanakAppTheme {
                QuickEntryScreen(
                    uiState = QuickEntryUiState(amountText = "15", availableCategories = listOf(custom)),
                    onAmountChanged = {}, onCategorySelected = { selected = it }, onNoteChanged = {},
                    onDateOptionSelected = {}, onDateChosenFromPicker = {}, onDismissDatePicker = {},
                    onMicTapped = {}, onVoicePermissionDenied = {}, onSaveClicked = {}, onClearUserMessage = {},
                    onOpenNewCategory = { opened = true }
                )
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Pet care"))
        compose.onNodeWithText("Pet care").performClick()
        assertEquals(custom, selected)
        compose.onNodeWithText("+ New category").performClick()
        assertTrue(opened)
    }
    @Test fun invalidNameShowsErrorAndPendingCreationDisablesActions() {
        compose.setContent {
            KanakAppTheme { NewCategoryDialog("Pets", "This category already exists", true, {}, {}, {}) }
        }
        compose.onNodeWithText("This category already exists").assertIsDisplayed()
        compose.onNodeWithText("Creating...").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }
    @Test fun blankNameCannotBeCreatedAndCancelIsAvailable() {
        var cancelled = false
        compose.setContent {
            KanakAppTheme { NewCategoryDialog("", null, false, {}, {}, { cancelled = true }) }
        }
        compose.onNodeWithText("Create category").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
    }
}
