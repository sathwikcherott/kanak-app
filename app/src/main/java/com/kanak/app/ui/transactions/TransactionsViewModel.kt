package com.kanak.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TransactionsViewModel(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private var allExpensesList: List<Expense> = emptyList()

    init {
        viewModelScope.launch {
            expenseRepository.getDefaultCurrency().collectLatest { currency ->
                _uiState.update { it.copy(currency = currency) }
            }
        }

        viewModelScope.launch {
            expenseRepository.getAllExpenses().collectLatest { expenses ->
                allExpensesList = expenses
                recomputeFilteredState()
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        recomputeFilteredState()
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
        recomputeFilteredState()
    }

    fun onDateFilterSelected(filter: DateRangeFilter) {
        _uiState.update { it.copy(selectedDateFilter = filter) }
        recomputeFilteredState()
    }

    fun onCategoryFilterSelected(category: ExpenseCategory?) {
        _uiState.update { it.copy(selectedCategoryFilter = category) }
        recomputeFilteredState()
    }

    private fun recomputeFilteredState() {
        val current = _uiState.value
        filterAndGroupExpenses(
            expenses = allExpensesList,
            query = current.searchQuery,
            dateFilter = current.selectedDateFilter,
            categoryFilter = current.selectedCategoryFilter
        )
    }

    private fun filterAndGroupExpenses(
        expenses: List<Expense>,
        query: String,
        dateFilter: DateRangeFilter,
        categoryFilter: ExpenseCategory?
    ) {
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_MONTH)
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear = now.get(Calendar.YEAR)

        val weekStart = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val filtered = expenses.filter { exp ->
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                val q = query.lowercase()
                val noteMatch = exp.note?.lowercase()?.contains(q) == true
                val catMatch = exp.category?.displayName?.lowercase()?.contains(q) == true
                noteMatch || catMatch
            }

            val matchesCategory = if (categoryFilter == null) {
                true
            } else {
                exp.category == categoryFilter
            }

            val matchesDate = when (dateFilter) {
                DateRangeFilter.ALL -> true
                DateRangeFilter.TODAY -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = exp.occurredAt }
                    cal.get(Calendar.DAY_OF_MONTH) == currentDay &&
                            cal.get(Calendar.MONTH) == currentMonth &&
                            cal.get(Calendar.YEAR) == currentYear
                }
                DateRangeFilter.THIS_WEEK -> {
                    exp.occurredAt >= weekStart
                }
                DateRangeFilter.THIS_MONTH -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = exp.occurredAt }
                    cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
                }
            }

            matchesQuery && matchesCategory && matchesDate
        }

        val sorted = filtered.sortedByDescending { it.occurredAt }

        val todayCal = Calendar.getInstance()
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        val todayDay = todayCal.get(Calendar.DAY_OF_YEAR)
        val todayYear = todayCal.get(Calendar.YEAR)

        val yesterdayDay = yesterdayCal.get(Calendar.DAY_OF_YEAR)
        val yesterdayYear = yesterdayCal.get(Calendar.YEAR)

        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

        val grouped = sorted.groupBy { exp ->
            val expCal = Calendar.getInstance().apply { timeInMillis = exp.occurredAt }
            val expDay = expCal.get(Calendar.DAY_OF_YEAR)
            val expYear = expCal.get(Calendar.YEAR)

            when {
                expDay == todayDay && expYear == todayYear -> "Today"
                expDay == yesterdayDay && expYear == yesterdayYear -> "Yesterday"
                else -> dateFormat.format(Date(exp.occurredAt))
            }
        }.map { (header, groupExpenses) ->
            DateGroupedTransactions(
                dateHeader = header,
                dateMillis = groupExpenses.firstOrNull()?.occurredAt ?: 0L,
                totalAmountMinor = groupExpenses.sumOf { it.amountMinor },
                expenses = groupExpenses
            )
        }.sortedByDescending { it.dateMillis }

        val totalAmount = filtered.sumOf { it.amountMinor }

        _uiState.update {
            it.copy(
                groupedTransactions = grouped,
                totalFilteredCount = filtered.size,
                totalFilteredAmountMinor = totalAmount
            )
        }
    }

    fun onExpenseClickedForEdit(expense: Expense) {
        _uiState.update {
            it.copy(
                selectedExpenseForEdit = expense,
                editAmountText = expense.amountAsFormattedString(),
                editCategory = expense.category,
                editNote = expense.note ?: "",
                editAmountError = null
            )
        }
    }

    fun onEditAmountChanged(amount: String) {
        val cleaned = amount.filter { it.isDigit() || (it == '.') || (it == ',') }
        _uiState.update { it.copy(editAmountText = cleaned, editAmountError = null) }
    }

    fun onEditCategorySelected(category: ExpenseCategory?) {
        _uiState.update { current ->
            val newCat = if (current.editCategory == category) null else category
            current.copy(editCategory = newCat)
        }
    }

    fun onEditNoteChanged(note: String) {
        _uiState.update { it.copy(editNote = note) }
    }

    fun closeEditSheet() {
        _uiState.update {
            it.copy(
                selectedExpenseForEdit = null,
                editAmountText = "",
                editCategory = null,
                editNote = "",
                editAmountError = null
            )
        }
    }

    fun saveEdit() {
        val current = _uiState.value
        val originalExpense = current.selectedExpenseForEdit ?: return

        val minor = Expense.parseToMinorUnits(current.editAmountText, current.currency)
        if (minor <= 0L) {
            _uiState.update { it.copy(editAmountError = "Amount must be greater than zero") }
            return
        }

        val updatedExpense = originalExpense.copy(
            amountMinor = minor,
            category = current.editCategory,
            note = current.editNote.ifBlank { null },
            categorizationState = if (current.editCategory != null) CategorizationState.CONFIRMED else CategorizationState.UNCATEGORIZED
        )

        _uiState.update { it.copy(isSavingEdit = true, editAmountError = null) }

        viewModelScope.launch {
            try {
                expenseRepository.saveExpense(updatedExpense)
                _uiState.update {
                    it.copy(
                        selectedExpenseForEdit = null,
                        isSavingEdit = false,
                        userMessage = "Transaction updated!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingEdit = false,
                        userMessage = "Failed to update: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun requestDelete(expense: Expense) {
        _uiState.update { it.copy(expenseToDelete = expense) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(expenseToDelete = null) }
    }

    fun confirmDelete() {
        val expense = _uiState.value.expenseToDelete ?: return
        viewModelScope.launch {
            try {
                expenseRepository.deleteExpense(expense.id)
                _uiState.update {
                    it.copy(
                        expenseToDelete = null,
                        selectedExpenseForEdit = if (it.selectedExpenseForEdit?.id == expense.id) null else it.selectedExpenseForEdit,
                        userMessage = "Transaction deleted!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(userMessage = "Failed to delete: ${e.localizedMessage}")
                }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TransactionsViewModel(repository) as T
        }
    }
}
