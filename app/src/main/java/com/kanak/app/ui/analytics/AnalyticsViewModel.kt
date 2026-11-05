package com.kanak.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.Expense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class AnalyticsViewModel(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private var allExpensesList: List<Expense> = emptyList()

    private val selectedCal: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    init {
        viewModelScope.launch {
            expenseRepository.getDefaultCurrency().collectLatest { currency ->
                _uiState.update { it.copy(currency = currency) }
            }
        }

        viewModelScope.launch {
            expenseRepository.getAllExpenses().collectLatest { expenses ->
                allExpensesList = expenses
                recomputeAnalyticsState()
            }
        }
    }

    fun onPreviousMonthClicked() {
        selectedCal.add(Calendar.MONTH, -1)
        recomputeAnalyticsState()
    }

    fun onNextMonthClicked() {
        val now = Calendar.getInstance()
        val curYear = now.get(Calendar.YEAR)
        val curMonth = now.get(Calendar.MONTH)

        val selYear = selectedCal.get(Calendar.YEAR)
        val selMonth = selectedCal.get(Calendar.MONTH)

        if (selYear < curYear || (selYear == curYear && selMonth < curMonth)) {
            selectedCal.add(Calendar.MONTH, 1)
            recomputeAnalyticsState()
        }
    }

    private fun recomputeAnalyticsState() {
        val selYear = selectedCal.get(Calendar.YEAR)
        val selMonth = selectedCal.get(Calendar.MONTH)

        val now = Calendar.getInstance()
        val nowYear = now.get(Calendar.YEAR)
        val nowMonth = now.get(Calendar.MONTH)
        val nowDay = now.get(Calendar.DAY_OF_MONTH)

        val canNavigateNext = (selYear < nowYear) || (selYear == nowYear && selMonth < nowMonth)

        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val selectedMonthYearName = monthFormat.format(selectedCal.time)

        val maxDaysInMonth = selectedCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val shortMonthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val monthShort = shortMonthFormat.format(selectedCal.time)
        val dateRangeText = "1 $monthShort - $maxDaysInMonth $monthShort $selYear"

        // Filter current selected month expenses
        val currentMonthExpenses = allExpensesList.filter { exp ->
            val cal = Calendar.getInstance().apply { timeInMillis = exp.occurredAt }
            cal.get(Calendar.YEAR) == selYear && cal.get(Calendar.MONTH) == selMonth
        }

        val totalSpendingMinor = currentMonthExpenses.sumOf { it.amountMinor }
        val transactionCount = currentMonthExpenses.size

        // Average daily spending calculation
        val daysForAverage = if (selYear == nowYear && selMonth == nowMonth) {
            nowDay.coerceAtLeast(1)
        } else {
            maxDaysInMonth.coerceAtLeast(1)
        }
        val averageDailySpendingMinor = if (daysForAverage > 0) totalSpendingMinor / daysForAverage else 0L

        // Daily spending map & list
        val dailyMap = (1..maxDaysInMonth).associateWith { 0L }.toMutableMap()
        currentMonthExpenses.forEach { exp ->
            val day = Calendar.getInstance().apply { timeInMillis = exp.occurredAt }.get(Calendar.DAY_OF_MONTH)
            if (day in 1..maxDaysInMonth) {
                dailyMap[day] = (dailyMap[day] ?: 0L) + exp.amountMinor
            }
        }

        val dailySpendingList = (1..maxDaysInMonth).map { day ->
            val dayCal = (selectedCal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, day)
            }
            DailySpendingItem(
                dayOfMonth = day,
                dateMillis = dayCal.timeInMillis,
                amountMinor = dailyMap[day] ?: 0L
            )
        }

        val maxDailySpendingMinor = dailySpendingList.maxOfOrNull { it.amountMinor } ?: 0L

        // Highest spending day calculation
        val highestDayEntry = dailyMap.entries.maxByOrNull { it.value }
        val (highestSpendingDayText, highestSpendingDayAmountMinor) = if (highestDayEntry != null && highestDayEntry.value > 0L) {
            val day = highestDayEntry.key
            val dayCal = (selectedCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
            val dayFormat = SimpleDateFormat("d MMM", Locale.getDefault())
            Pair(dayFormat.format(dayCal.time), highestDayEntry.value)
        } else {
            Pair("N/A", 0L)
        }

        // Category breakdown calculation (exclude zero spending categories)
        val categoryBreakdown = if (totalSpendingMinor > 0L) {
            currentMonthExpenses.groupBy { it.category }
                .mapNotNull { (cat, catExpenses) ->
                    val catTotal = catExpenses.sumOf { it.amountMinor }
                    if (catTotal > 0L) {
                        val pct = ((catTotal * 100.0) / totalSpendingMinor).roundToInt()
                        AnalyticsCategorySpending(
                            category = cat,
                            amountMinor = catTotal,
                            percentage = pct
                        )
                    } else null
                }
                .sortedByDescending { it.amountMinor }
        } else {
            emptyList()
        }

        // Previous month calculation
        val prevCal = (selectedCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        val prevYear = prevCal.get(Calendar.YEAR)
        val prevMonth = prevCal.get(Calendar.MONTH)

        val previousMonthExpenses = allExpensesList.filter { exp ->
            val cal = Calendar.getInstance().apply { timeInMillis = exp.occurredAt }
            cal.get(Calendar.YEAR) == prevYear && cal.get(Calendar.MONTH) == prevMonth
        }

        val previousMonthTotalMinor = previousMonthExpenses.sumOf { it.amountMinor }
        val comparisonDiffMinor = abs(totalSpendingMinor - previousMonthTotalMinor)
        val isIncrease = totalSpendingMinor > previousMonthTotalMinor

        val prevMonthName = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(prevCal.time)

        val (momPercentText, comparisonNote) = when {
            previousMonthTotalMinor == 0L && totalSpendingMinor > 0L -> {
                Pair("New spending", "vs $prevMonthName")
            }
            previousMonthTotalMinor == 0L && totalSpendingMinor == 0L -> {
                Pair(null, "No spending in either month")
            }
            previousMonthTotalMinor > 0L && totalSpendingMinor == 0L -> {
                Pair("-100%", "vs $prevMonthName")
            }
            else -> {
                val pct = (((totalSpendingMinor - previousMonthTotalMinor) * 100.0) / previousMonthTotalMinor).roundToInt()
                val pctStr = if (pct >= 0) "+$pct%" else "$pct%"
                Pair(pctStr, "vs $prevMonthName")
            }
        }

        _uiState.update {
            it.copy(
                selectedYear = selYear,
                selectedMonth = selMonth,
                selectedMonthYearName = selectedMonthYearName,
                dateRangeText = dateRangeText,
                canNavigateNext = canNavigateNext,
                totalSpendingMinor = totalSpendingMinor,
                transactionCount = transactionCount,
                averageDailySpendingMinor = averageDailySpendingMinor,
                highestSpendingDayText = highestSpendingDayText,
                highestSpendingDayAmountMinor = highestSpendingDayAmountMinor,
                categoryBreakdown = categoryBreakdown,
                dailySpendingList = dailySpendingList,
                maxDailySpendingMinor = maxDailySpendingMinor,
                previousMonthTotalMinor = previousMonthTotalMinor,
                comparisonDiffMinor = comparisonDiffMinor,
                isIncrease = isIncrease,
                momPercentText = momPercentText,
                comparisonNote = comparisonNote
            )
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnalyticsViewModel(repository) as T
        }
    }
}
