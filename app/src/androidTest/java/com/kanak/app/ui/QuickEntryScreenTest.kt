package com.kanak.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanak.app.designsystem.theme.KanakAppTheme
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.ui.entry.QuickEntryScreen
import com.kanak.app.ui.entry.QuickEntryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuickEntryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun saveButton_isDisabledWhenAmountIsInvalid() {
        composeTestRule.setContent {
            KanakAppTheme {
                QuickEntryScreen(
                    uiState = QuickEntryUiState(amountText = ""),
                    onAmountChanged = {},
                    onCategorySelected = {},
                    onNoteChanged = {},
                    onMicTapped = {},
                    onVoicePermissionDenied = {},
                    onSaveClicked = {},
                    onClearUserMessage = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Save Expense").assertIsNotEnabled()
    }

    @Test
    fun saveButton_isEnabledWhenAmountIsValid() {
        composeTestRule.setContent {
            KanakAppTheme {
                QuickEntryScreen(
                    uiState = QuickEntryUiState(amountText = "12.50"),
                    onAmountChanged = {},
                    onCategorySelected = {},
                    onNoteChanged = {},
                    onMicTapped = {},
                    onVoicePermissionDenied = {},
                    onSaveClicked = {},
                    onClearUserMessage = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Save Expense").assertIsEnabled()
    }

    @Test
    fun validationError_displaysWhenAmountErrorSet() {
        composeTestRule.setContent {
            KanakAppTheme {
                QuickEntryScreen(
                    uiState = QuickEntryUiState(
                        amountText = "0",
                        amountError = "Amount must be greater than zero"
                    ),
                    onAmountChanged = {},
                    onCategorySelected = {},
                    onNoteChanged = {},
                    onMicTapped = {},
                    onVoicePermissionDenied = {},
                    onSaveClicked = {},
                    onClearUserMessage = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Amount must be greater than zero").assertIsDisplayed()
    }

    @Test
    fun categorySelection_triggersCallbackOnTap() {
        var selectedCategory: ExpenseCategory? = null

        composeTestRule.setContent {
            KanakAppTheme {
                QuickEntryScreen(
                    uiState = QuickEntryUiState(amountText = "15.00"),
                    onAmountChanged = {},
                    onCategorySelected = { selectedCategory = it },
                    onNoteChanged = {},
                    onMicTapped = {},
                    onVoicePermissionDenied = {},
                    onSaveClicked = {},
                    onClearUserMessage = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Food & Dining").performClick()
        assertEquals(ExpenseCategory.FOOD, selectedCategory)
    }

    @Test
    fun saveClick_triggersOnSaveClicked() {
        var saveClicked = false

        composeTestRule.setContent {
            KanakAppTheme {
                QuickEntryScreen(
                    uiState = QuickEntryUiState(amountText = "20.00"),
                    onAmountChanged = {},
                    onCategorySelected = {},
                    onNoteChanged = {},
                    onMicTapped = {},
                    onVoicePermissionDenied = {},
                    onSaveClicked = { saveClicked = true },
                    onClearUserMessage = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Save Expense").performClick()
        assertTrue(saveClicked)
    }
}
