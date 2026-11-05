package com.kanak.app.ui

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.ui.transactions.DateRangeFilter
import com.kanak.app.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepository = FakeExpenseRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsExpensesAndCurrencyFromRepository() = runTest {
        val expense = Expense(id = 1L, amountMinor = 5000L, category = ExpenseCategory.FOOD, note = "Dinner")
        fakeRepository.setExpenses(listOf(expense))

        val viewModel = TransactionsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("INR", state.currency)
        assertEquals(1, state.totalFilteredCount)
        assertEquals(5000L, state.totalFilteredAmountMinor)
        assertEquals(1, state.groupedTransactions.size)
        assertEquals(1, state.groupedTransactions[0].expenses.size)
    }

    @Test
    fun searchQuery_filtersByNoteAndCategoryName() = runTest {
        val exp1 = Expense(id = 1L, amountMinor = 15000L, category = ExpenseCategory.FOOD, note = "Lunch with team")
        val exp2 = Expense(id = 2L, amountMinor = 30000L, category = ExpenseCategory.TRANSPORT, note = "Uber to office")
        val exp3 = Expense(id = 3L, amountMinor = 50000L, category = ExpenseCategory.GIRLFRIEND, note = "Swathi gift")
        fakeRepository.setExpenses(listOf(exp1, exp2, exp3))

        val viewModel = TransactionsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Search note
        viewModel.onSearchQueryChanged("team")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.totalFilteredCount)
        assertEquals("Lunch with team", viewModel.uiState.value.groupedTransactions[0].expenses[0].note)

        // Search category display name
        viewModel.onSearchQueryChanged("Transport")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.totalFilteredCount)
        assertEquals(ExpenseCategory.TRANSPORT, viewModel.uiState.value.groupedTransactions[0].expenses[0].category)

        // Clear search
        viewModel.clearSearch()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.totalFilteredCount)
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun categoryFilter_filtersBySpecificCategoryIncludingGirlfriend() = runTest {
        val exp1 = Expense(id = 1L, amountMinor = 10000L, category = ExpenseCategory.FOOD)
        val exp2 = Expense(id = 2L, amountMinor = 25000L, category = ExpenseCategory.GIRLFRIEND, note = "Dinner with Swathi")
        fakeRepository.setExpenses(listOf(exp1, exp2))

        val viewModel = TransactionsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Filter Girlfriend category
        viewModel.onCategoryFilterSelected(ExpenseCategory.GIRLFRIEND)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.totalFilteredCount)
        assertEquals(ExpenseCategory.GIRLFRIEND, viewModel.uiState.value.selectedCategoryFilter)
        assertEquals(25000L, viewModel.uiState.value.totalFilteredAmountMinor)

        // Deselect category
        viewModel.onCategoryFilterSelected(null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.totalFilteredCount)
        assertNull(viewModel.uiState.value.selectedCategoryFilter)
    }

    @Test
    fun dateFilter_filtersByDateRanges() = runTest {
        val now = Calendar.getInstance()
        val todayMs = now.timeInMillis

        val prevMonthCal = (now.clone() as Calendar).apply { add(Calendar.MONTH, -2) }
        val prevMonthMs = prevMonthCal.timeInMillis

        val expToday = Expense(id = 1L, amountMinor = 1000L, occurredAt = todayMs, category = ExpenseCategory.FOOD)
        val expOld = Expense(id = 2L, amountMinor = 5000L, occurredAt = prevMonthMs, category = ExpenseCategory.BILL)
        fakeRepository.setExpenses(listOf(expToday, expOld))

        val viewModel = TransactionsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Filter Today
        viewModel.onDateFilterSelected(DateRangeFilter.TODAY)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.totalFilteredCount)
        assertEquals(1000L, viewModel.uiState.value.totalFilteredAmountMinor)

        // Filter All
        viewModel.onDateFilterSelected(DateRangeFilter.ALL)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.totalFilteredCount)
    }

    @Test
    fun dateGrouping_sortsNewestFirstAndCalculatesGroupTotals() = runTest {
        val calToday = Calendar.getInstance()
        val calYesterday = (calToday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }

        val exp1 = Expense(id = 1L, amountMinor = 1000L, occurredAt = calToday.timeInMillis, note = "Today 1")
        val exp2 = Expense(id = 2L, amountMinor = 2000L, occurredAt = calToday.timeInMillis, note = "Today 2")
        val exp3 = Expense(id = 3L, amountMinor = 5000L, occurredAt = calYesterday.timeInMillis, note = "Yesterday 1")
        fakeRepository.setExpenses(listOf(exp1, exp2, exp3))

        val viewModel = TransactionsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val grouped = viewModel.uiState.value.groupedTransactions
        assertEquals(2, grouped.size)
        assertEquals("Today", grouped[0].dateHeader)
        assertEquals(3000L, grouped[0].totalAmountMinor)
        assertEquals(2, grouped[0].expenses.size)

        assertEquals("Yesterday", grouped[1].dateHeader)
        assertEquals(5000L, grouped[1].totalAmountMinor)
        assertEquals(1, grouped[1].expenses.size)
    }

    @Test
    fun editExpense_updatesExpenseAndSavesToRepository() = runTest {
        val original = Expense(id = 10L, amountMinor = 2000L, category = ExpenseCategory.FOOD, note = "Old note")
        fakeRepository.setExpenses(listOf(original))

        val viewModel = TransactionsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Open edit
        viewModel.onExpenseClickedForEdit(original)
        assertEquals(original, viewModel.uiState.value.selectedExpenseForEdit)
        assertEquals("20.00", viewModel.uiState.value.editAmountText)

        // Modify values
        viewModel.onEditAmountChanged("50.00")
        viewModel.onEditCategorySelected(ExpenseCategory.SHOPPING)
        viewModel.onEditNoteChanged("Updated note")

        // Save edit
        viewModel.saveEdit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedExpenseForEdit)
        assertEquals("Transaction updated!", viewModel.uiState.value.userMessage)
        val saved = fakeRepository.savedExpenses.last()
        assertEquals(10L, saved.id)
        assertEquals(5000L, saved.amountMinor)
        assertEquals(ExpenseCategory.SHOPPING, saved.category)
        assertEquals("Updated note", saved.note)
        assertEquals(CategorizationState.CONFIRMED, saved.categorizationState)
    }

    @Test
    fun editExpense_invalidAmount_showsErrorAndDoesNotSave() = runTest {
        val original = Expense(id = 10L, amountMinor = 2000L, category = ExpenseCategory.FOOD)
        fakeRepository.setExpenses(listOf(original))

        val viewModel = TransactionsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onExpenseClickedForEdit(original)
        viewModel.onEditAmountChanged("0.00")

        viewModel.saveEdit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.editAmountError)
        assertEquals("Amount must be greater than zero", viewModel.uiState.value.editAmountError)
        assertEquals(0, fakeRepository.savedExpenses.size)
    }

    @Test
    fun deleteExpense_requestsAndConfirmsDeletion() = runTest {
        val exp = Expense(id = 5L, amountMinor = 1500L, category = ExpenseCategory.FOOD)
        fakeRepository.setExpenses(listOf(exp))

        val viewModel = TransactionsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Request delete
        viewModel.requestDelete(exp)
        assertEquals(exp, viewModel.uiState.value.expenseToDelete)

        // Cancel delete
        viewModel.cancelDelete()
        assertNull(viewModel.uiState.value.expenseToDelete)

        // Request again and confirm
        viewModel.requestDelete(exp)
        viewModel.confirmDelete()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.expenseToDelete)
        assertEquals("Transaction deleted!", viewModel.uiState.value.userMessage)
        assertEquals(0, fakeRepository.expensesFlow.value.size)
    }

    @Test
    fun emptyState_handlesEmptyRepositoryGracefully() = runTest {
        fakeRepository.setExpenses(emptyList())

        val viewModel = TransactionsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.totalFilteredCount)
        assertEquals(0L, state.totalFilteredAmountMinor)
        assertTrue(state.groupedTransactions.isEmpty())
    }

    private class FakeExpenseRepository : ExpenseRepository {
        val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        val savedExpenses = mutableListOf<Expense>()
        private val currencyFlow = MutableStateFlow("INR")
        private val themeFlow = MutableStateFlow(ThemePreference.DARK)

        fun setExpenses(list: List<Expense>) {
            expensesFlow.value = list
        }

        override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow
        override fun getExpenseById(id: Long): Flow<Expense?> = flowOf(expensesFlow.value.find { it.id == id })

        override suspend fun saveExpense(expense: Expense): Long {
            savedExpenses.add(expense)
            val current = expensesFlow.value.toMutableList()
            val index = current.indexOfFirst { it.id == expense.id }
            if (index >= 0) {
                current[index] = expense
            } else {
                current.add(expense)
            }
            expensesFlow.value = current
            return expense.id
        }

        override suspend fun deleteExpense(id: Long) {
            expensesFlow.value = expensesFlow.value.filter { it.id != id }
        }

        override fun getDefaultCurrency(): Flow<String> = currencyFlow
        override suspend fun setDefaultCurrency(currency: String) {
            currencyFlow.value = currency
        }

        override fun getThemePreference(): Flow<ThemePreference> = themeFlow
        override suspend fun setThemePreference(theme: ThemePreference) {
            themeFlow.value = theme
        }

        override fun getEnrichmentJobForExpense(expenseId: Long): Flow<AiEnrichmentJobEntity?> = flowOf(null)
        override suspend fun replaceAllExpensesInTransaction(expenses: List<Expense>) {
            expensesFlow.value = expenses
        }
    }
}
