package com.kanak.app.ui.entry

import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory

/**
 * Immutable UI State for the Quick Entry screen shell.
 */
data class QuickEntryUiState(
    val amountText: String = "",
    val currency: String = "USD",
    val selectedCategory: ExpenseCategory? = null,
    val note: String = "",
    val source: EntrySource = EntrySource.TYPED,
    val categorizationState: CategorizationState = CategorizationState.UNCATEGORIZED,
    val isSaving: Boolean = false,
    val isListening: Boolean = false,
    val voiceInputAvailable: Boolean = true,
    val amountError: String? = null,
    val userMessage: String? = null,
    val recentExpenses: List<Expense> = emptyList()
) {
    val amountMinor: Long
        get() = Expense.parseToMinorUnits(amountText, currency)

    val isValidInput: Boolean
        get() = amountText.isNotBlank() && amountMinor > 0L
}
