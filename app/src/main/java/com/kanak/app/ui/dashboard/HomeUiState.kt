package com.kanak.app.ui.dashboard

import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.ui.navigation.KanakTab

data class CategorySpending(
    val category: ExpenseCategory?,
    val amountMinor: Long,
    val percentage: Int
)

data class HomeUiState(
    val currentMonthYearName: String = "",
    val currentMonthTotalMinor: Long = 0L,
    val previousMonthTotalMinor: Long = 0L,
    val monthOverMonthPercent: Int? = null,
    val currency: String = "INR",
    val categoryBreakdown: List<CategorySpending> = emptyList(),
    val recentTransactions: List<Expense> = emptyList(),
    val allExpenses: List<Expense> = emptyList(),
    val activeTab: KanakTab = KanakTab.HOME,
    val showQuickEntry: Boolean = false,
    val triggerVoiceOnEntry: Boolean = false
) {
    val totalFormatted: String
        get() = Expense(amountMinor = currentMonthTotalMinor, currency = currency).amountWithSymbol()

    val previousTotalFormatted: String
        get() = Expense(amountMinor = previousMonthTotalMinor, currency = currency).amountWithSymbol()
}
