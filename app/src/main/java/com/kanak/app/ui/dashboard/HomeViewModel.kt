package com.kanak.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.Expense
import com.kanak.app.ui.navigation.KanakTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeViewModel(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            expenseRepository.getDefaultCurrency().collectLatest { currency ->
                _uiState.update { it.copy(currency = currency) }
            }
        }
        viewModelScope.launch {
            expenseRepository.getAllExpenses().collectLatest { expenses ->
                computeDashboardState(expenses)
            }
        }
    }

    private fun computeDashboardState(expenses: List<Expense>) {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear = now.get(Calendar.YEAR)

        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val monthYearName = monthFormat.format(now.time)

        val prevCal = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
        }
        val prevMonth = prevCal.get(Calendar.MONTH)
        val prevYear = prevCal.get(Calendar.YEAR)

        val currentMonthExpenses = expenses.filter { exp ->
            val cal = Calendar.getInstance().apply { timeInMillis = exp.occurredAt }
            cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
        }

        val previousMonthExpenses = expenses.filter { exp ->
            val cal = Calendar.getInstance().apply { timeInMillis = exp.occurredAt }
            cal.get(Calendar.MONTH) == prevMonth && cal.get(Calendar.YEAR) == prevYear
        }

        val currentMonthTotal = currentMonthExpenses.sumOf { it.amountMinor }
        val previousMonthTotal = previousMonthExpenses.sumOf { it.amountMinor }

        val momChangePercent = if (previousMonthTotal > 0L) {
            val diff = currentMonthTotal - previousMonthTotal
            ((diff * 100) / previousMonthTotal).toInt()
        } else {
            null
        }

        val breakdownSource = if (currentMonthExpenses.isNotEmpty()) currentMonthExpenses else expenses
        val breakdownTotal = if (currentMonthExpenses.isNotEmpty()) currentMonthTotal else expenses.sumOf { it.amountMinor }

        val categoryBreakdown = if (breakdownTotal > 0L) {
            breakdownSource.groupBy { it.category }
                .map { (cat, catExpenses) ->
                    val catTotal = catExpenses.sumOf { it.amountMinor }
                    val percent = ((catTotal * 100) / breakdownTotal).toInt()
                    CategorySpending(category = cat, amountMinor = catTotal, percentage = percent)
                }
                .sortedByDescending { it.amountMinor }
        } else {
            emptyList()
        }

        val recent = expenses.take(10)

        _uiState.update {
            it.copy(
                currentMonthYearName = monthYearName,
                currentMonthTotalMinor = currentMonthTotal,
                previousMonthTotalMinor = previousMonthTotal,
                monthOverMonthPercent = momChangePercent,
                categoryBreakdown = categoryBreakdown,
                recentTransactions = recent,
                allExpenses = expenses
            )
        }
    }

    fun selectTab(tab: KanakTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun openQuickEntry(triggerVoice: Boolean = false) {
        _uiState.update { it.copy(showQuickEntry = true, triggerVoiceOnEntry = triggerVoice) }
    }

    fun closeQuickEntry() {
        _uiState.update { it.copy(showQuickEntry = false, triggerVoiceOnEntry = false) }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
