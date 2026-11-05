package com.kanak.app.ui.analytics

import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory

data class AnalyticsCategorySpending(
    val category: ExpenseCategory?,
    val amountMinor: Long,
    val percentage: Int,
)

data class DailySpendingItem(
    val dayOfMonth: Int,
    val dateMillis: Long,
    val amountMinor: Long
)

data class AnalyticsUiState(
    val selectedYear: Int = 2026,
    val selectedMonth: Int = 0,
    val selectedMonthYearName: String = "",
    val dateRangeText: String = "",
    val canNavigateNext: Boolean = false,
    val totalSpendingMinor: Long = 0L,
    val transactionCount: Int = 0,
    val averageDailySpendingMinor: Long = 0L,
    val highestSpendingDayText: String = "N/A",
    val highestSpendingDayAmountMinor: Long = 0L,
    val categoryBreakdown: List<AnalyticsCategorySpending> = emptyList(),
    val dailySpendingList: List<DailySpendingItem> = emptyList(),
    val maxDailySpendingMinor: Long = 0L,
    val previousMonthTotalMinor: Long = 0L,
    val comparisonDiffMinor: Long = 0L,
    val isIncrease: Boolean = false,
    val momPercentText: String? = null,
    val comparisonNote: String = "",
    val currency: String = "INR"
) {
    val totalSpendingFormatted: String
        get() = Expense(amountMinor = totalSpendingMinor, currency = currency).amountWithSymbol()

    val averageDailySpendingFormatted: String
        get() = Expense(amountMinor = averageDailySpendingMinor, currency = currency).amountWithSymbol()

    val highestSpendingDayAmountFormatted: String
        get() = Expense(amountMinor = highestSpendingDayAmountMinor, currency = currency).amountWithSymbol()

    val previousMonthTotalFormatted: String
        get() = Expense(amountMinor = previousMonthTotalMinor, currency = currency).amountWithSymbol()

    val comparisonDiffFormatted: String
        get() = Expense(amountMinor = comparisonDiffMinor, currency = currency).amountWithSymbol()
}
