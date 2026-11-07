package com.kanak.app.ui.entry

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExpenseDateOption {
    TODAY,
    YESTERDAY,
    CHOSEN_DATE
}

/**
 * Immutable UI State for the Quick Entry screen shell.
 */
data class QuickEntryUiState(
    val amountText: String = "",
    val currency: String = "INR",
    val selectedCategory: ExpenseCategory? = null,
    val availableCategories: List<ExpenseCategory> = ExpenseCategory.entries,
    val showNewCategoryDialog: Boolean = false,
    val newCategoryName: String = "",
    val categoryError: String? = null,
    val isCreatingCategory: Boolean = false,
    val note: String = "",
    val source: EntrySource = EntrySource.TYPED,
    val categorizationState: CategorizationState = CategorizationState.UNCATEGORIZED,
    val selectedDateOption: ExpenseDateOption = ExpenseDateOption.TODAY,
    val selectedDateMillis: Long = System.currentTimeMillis(),
    val showDatePicker: Boolean = false,
    val dateError: String? = null,
    val isSaving: Boolean = false,
    val isListening: Boolean = false,
    val voiceInputAvailable: Boolean = true,
    val amountError: String? = null,
    val userMessage: String? = null,
    val themePreference: ThemePreference = ThemePreference.DARK,
    val recentExpenses: List<Expense> = emptyList()
) {
    val amountMinor: Long
        get() = Expense.parseToMinorUnits(amountText, currency)

    val isValidInput: Boolean
        get() = amountText.isNotBlank() && (amountMinor > 0L) && (dateError == null) && (amountError == null)

    val selectedDateFormatted: String
        get() = when (selectedDateOption) {
            ExpenseDateOption.TODAY -> "Today"
            ExpenseDateOption.YESTERDAY -> "Yesterday"
            ExpenseDateOption.CHOSEN_DATE -> {
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                dateFormat.format(Date(selectedDateMillis))
            }
        }
}
