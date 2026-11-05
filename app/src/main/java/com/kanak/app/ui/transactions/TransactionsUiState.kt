package com.kanak.app.ui.transactions

import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory

enum class DateRangeFilter(val displayName: String) {
    ALL("All"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}

data class DateGroupedTransactions(
    val dateHeader: String,
    val dateMillis: Long,
    val totalAmountMinor: Long,
    val expenses: List<Expense>
)

data class TransactionsUiState(
    val searchQuery: String = "",
    val selectedDateFilter: DateRangeFilter = DateRangeFilter.ALL,
    val selectedCategoryFilter: ExpenseCategory? = null,
    val groupedTransactions: List<DateGroupedTransactions> = emptyList(),
    val totalFilteredCount: Int = 0,
    val totalFilteredAmountMinor: Long = 0L,
    val currency: String = "INR",
    val selectedExpenseForEdit: Expense? = null,
    val expenseToDelete: Expense? = null,
    val editAmountText: String = "",
    val editCategory: ExpenseCategory? = null,
    val editNote: String = "",
    val editAmountError: String? = null,
    val isSavingEdit: Boolean = false,
    val userMessage: String? = null
) {
    val totalFilteredAmountFormatted: String
        get() = Expense(amountMinor = totalFilteredAmountMinor, currency = currency).amountWithSymbol()
}
