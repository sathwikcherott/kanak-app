package com.kanak.app.ui

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.ui.dashboard.HomeViewModel
import com.kanak.app.ui.navigation.KanakTab
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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
    fun computeDashboardState_calculatesCurrentMonthTotalsAndCategoryBreakdown() = runTest {
        val now = Calendar.getInstance()
        val expense1 = Expense(id = 1L, amountMinor = 500000L, occurredAt = now.timeInMillis, category = ExpenseCategory.FOOD)
        val expense2 = Expense(id = 2L, amountMinor = 500000L, occurredAt = now.timeInMillis, category = ExpenseCategory.TRANSPORT)
        fakeRepository.setExpenses(listOf(expense1, expense2))

        val viewModel = HomeViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1000000L, state.currentMonthTotalMinor)
        assertEquals(2, state.categoryBreakdown.size)
        assertEquals(50, state.categoryBreakdown[0].percentage)
        assertEquals(50, state.categoryBreakdown[1].percentage)
    }

    @Test
    fun computeDashboardState_calculatesMonthOverMonthComparison() = runTest {
        val currentCal = Calendar.getInstance()
        val prevCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }

        val currentExp = Expense(id = 1L, amountMinor = 120000L, occurredAt = currentCal.timeInMillis, category = ExpenseCategory.FOOD)
        val prevExp = Expense(id = 2L, amountMinor = 100000L, occurredAt = prevCal.timeInMillis, category = ExpenseCategory.FOOD)
        fakeRepository.setExpenses(listOf(currentExp, prevExp))

        val viewModel = HomeViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(120000L, state.currentMonthTotalMinor)
        assertEquals(100000L, state.previousMonthTotalMinor)
        assertEquals(20, state.monthOverMonthPercent)
    }

    @Test
    fun navigationAndQuickEntryState_updatesCorrectly() = runTest {
        val viewModel = HomeViewModel(fakeRepository)

        viewModel.selectTab(KanakTab.TRANSACTIONS)
        assertEquals(KanakTab.TRANSACTIONS, viewModel.uiState.value.activeTab)

        viewModel.openQuickEntry(triggerVoice = true)
        assertTrue(viewModel.uiState.value.showQuickEntry)
        assertTrue(viewModel.uiState.value.triggerVoiceOnEntry)

        viewModel.closeQuickEntry()
        assertEquals(false, viewModel.uiState.value.showQuickEntry)
    }

    private class FakeExpenseRepository : ExpenseRepository {
        private val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        private val currencyFlow = MutableStateFlow("INR")
        private val themeFlow = MutableStateFlow(ThemePreference.DARK)

        fun setExpenses(list: List<Expense>) {
            expensesFlow.value = list
        }

        override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow
        override fun getExpenseById(id: Long): Flow<Expense?> = flowOf(null)
        override suspend fun saveExpense(expense: Expense): Long = 1L
        override suspend fun deleteExpense(id: Long) {}
        override fun getDefaultCurrency(): Flow<String> = currencyFlow
        override suspend fun setDefaultCurrency(currency: String) {}
        override fun getThemePreference(): Flow<ThemePreference> = themeFlow
        override suspend fun setThemePreference(theme: ThemePreference) {}
        override fun getEnrichmentJobForExpense(expenseId: Long): Flow<AiEnrichmentJobEntity?> = flowOf(null)
    }
}
