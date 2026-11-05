package com.kanak.app.ui

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.ui.analytics.AnalyticsViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

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
    fun init_loadsExpensesAndCalculatesSelectedMonthOverview() = runTest {
        val now = Calendar.getInstance()

        val exp1 = Expense(id = 1L, amountMinor = 15000L, occurredAt = now.timeInMillis, category = ExpenseCategory.FOOD)
        val exp2 = Expense(id = 2L, amountMinor = 25000L, occurredAt = now.timeInMillis, category = ExpenseCategory.GROCERIES)
        fakeRepository.setExpenses(listOf(exp1, exp2))

        val viewModel = AnalyticsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(40000L, state.totalSpendingMinor)
        assertEquals(2, state.transactionCount)
        assertTrue(state.averageDailySpendingMinor > 0L)
        assertTrue(state.highestSpendingDayAmountMinor > 0L)
        assertNotNull(state.highestSpendingDayText)
    }

    @Test
    fun categoryBreakdown_filtersOutZeroSpendingAndIncludesGirlfriendCategory() = runTest {
        val now = Calendar.getInstance()

        val exp1 = Expense(id = 1L, amountMinor = 30000L, occurredAt = now.timeInMillis, category = ExpenseCategory.FOOD)
        val exp2 = Expense(id = 2L, amountMinor = 70000L, occurredAt = now.timeInMillis, category = ExpenseCategory.GIRLFRIEND, note = "Swathi gift")
        fakeRepository.setExpenses(listOf(exp1, exp2))

        val viewModel = AnalyticsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val breakdown = viewModel.uiState.value.categoryBreakdown
        assertEquals(2, breakdown.size)
        // Girlfriend category has 70% of total
        assertEquals(ExpenseCategory.GIRLFRIEND, breakdown[0].category)
        assertEquals(70000L, breakdown[0].amountMinor)
        assertEquals(70, breakdown[0].percentage)

        // Food category has 30% of total
        assertEquals(ExpenseCategory.FOOD, breakdown[1].category)
        assertEquals(30000L, breakdown[1].amountMinor)
        assertEquals(30, breakdown[1].percentage)
    }

    @Test
    fun monthNavigation_navigatesPreviousAndNextMonthsAndPreventsFutureMonths() = runTest {
        val viewModel = AnalyticsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially on current month -> canNavigateNext should be false
        assertFalse(viewModel.uiState.value.canNavigateNext)

        // Navigate to previous month
        viewModel.onPreviousMonthClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        // Now on past month -> canNavigateNext should be true
        assertTrue(viewModel.uiState.value.canNavigateNext)

        // Navigate back to current month
        viewModel.onNextMonthClicked()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canNavigateNext)

        // Attempting to navigate further into future should be blocked
        viewModel.onNextMonthClicked()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canNavigateNext)
    }

    @Test
    fun monthlyComparison_case1_previousZeroCurrentPositive_displaysNewSpending() = runTest {
        val currentCal = Calendar.getInstance()
        val expCurrent = Expense(id = 1L, amountMinor = 15000L, occurredAt = currentCal.timeInMillis, category = ExpenseCategory.TRANSPORT)
        fakeRepository.setExpenses(listOf(expCurrent))

        val viewModel = AnalyticsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(15000L, state.totalSpendingMinor)
        assertEquals(0L, state.previousMonthTotalMinor)
        assertEquals(15000L, state.comparisonDiffMinor)
        assertEquals("New spending", state.momPercentText)
    }

    @Test
    fun monthlyComparison_case2_bothMonthsZero_displaysNoSpendingInEitherMonth() = runTest {
        fakeRepository.setExpenses(emptyList())

        val viewModel = AnalyticsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0L, state.totalSpendingMinor)
        assertEquals(0L, state.previousMonthTotalMinor)
        assertEquals(0L, state.comparisonDiffMinor)
        assertNull(state.momPercentText)
        assertEquals("No spending in either month", state.comparisonNote)
    }

    @Test
    fun monthlyComparison_case3_previousPositiveCurrentZero_displaysMinus100Percent() = runTest {
        val prevCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val expPrev = Expense(id = 1L, amountMinor = 20000L, occurredAt = prevCal.timeInMillis, category = ExpenseCategory.BILL)
        fakeRepository.setExpenses(listOf(expPrev))

        val viewModel = AnalyticsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0L, state.totalSpendingMinor)
        assertEquals(20000L, state.previousMonthTotalMinor)
        assertEquals(20000L, state.comparisonDiffMinor)
        assertEquals("-100%", state.momPercentText)
    }

    @Test
    fun monthlyComparison_case4_bothMonthsPositive_calculatesAccuratePercentageChange() = runTest {
        val currentCal = Calendar.getInstance()
        val prevCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }

        val expCurrent = Expense(id = 1L, amountMinor = 30000L, occurredAt = currentCal.timeInMillis, category = ExpenseCategory.FOOD)
        val expPrev = Expense(id = 2L, amountMinor = 20000L, occurredAt = prevCal.timeInMillis, category = ExpenseCategory.FOOD)
        fakeRepository.setExpenses(listOf(expCurrent, expPrev))

        val viewModel = AnalyticsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(30000L, state.totalSpendingMinor)
        assertEquals(20000L, state.previousMonthTotalMinor)
        assertEquals(10000L, state.comparisonDiffMinor)
        assertTrue(state.isIncrease)
        assertEquals("+50%", state.momPercentText)
    }

    @Test
    fun dailyAverage_currentMonthUsesElapsedDaysIncludingToday() = runTest {
        val now = Calendar.getInstance()
        val todayDay = now.get(Calendar.DAY_OF_MONTH)

        // Expense of 15000 minor units
        val exp = Expense(id = 1L, amountMinor = 15000L, occurredAt = now.timeInMillis, category = ExpenseCategory.FOOD)
        fakeRepository.setExpenses(listOf(exp))

        val viewModel = AnalyticsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val expectedAverage = 15000L / todayDay
        assertEquals(expectedAverage, state.averageDailySpendingMinor)
    }

    @Test
    fun dailyAverage_pastMonthUsesFullCalendarDaysInThatMonth() = runTest {
        val pastCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val maxDaysInPastMonth = pastCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val exp = Expense(id = 1L, amountMinor = 31000L, occurredAt = pastCal.timeInMillis, category = ExpenseCategory.FOOD)
        fakeRepository.setExpenses(listOf(exp))

        val viewModel = AnalyticsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Navigate to previous month
        viewModel.onPreviousMonthClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val expectedAverage = 31000L / maxDaysInPastMonth
        assertEquals(expectedAverage, state.averageDailySpendingMinor)
    }

    @Test
    fun dailySpendingTrend_populatesAllDaysInMonth() = runTest {
        val now = Calendar.getInstance()
        val dayMax = now.getActualMaximum(Calendar.DAY_OF_MONTH)

        val exp = Expense(id = 1L, amountMinor = 5000L, occurredAt = now.timeInMillis, category = ExpenseCategory.FOOD)
        fakeRepository.setExpenses(listOf(exp))

        val viewModel = AnalyticsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val dailyList = viewModel.uiState.value.dailySpendingList
        assertEquals(dayMax, dailyList.size)
        val currentDay = now.get(Calendar.DAY_OF_MONTH)
        assertEquals(5000L, dailyList[currentDay - 1].amountMinor)
    }

    @Test
    fun emptyState_handlesNoExpensesGracefully() = runTest {
        fakeRepository.setExpenses(emptyList())

        val viewModel = AnalyticsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0L, state.totalSpendingMinor)
        assertEquals(0, state.transactionCount)
        assertEquals(0L, state.averageDailySpendingMinor)
        assertEquals("N/A", state.highestSpendingDayText)
        assertEquals(0L, state.highestSpendingDayAmountMinor)
        assertTrue(state.categoryBreakdown.isEmpty())
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
        override suspend fun setThemePreference(theme: ThemePreference) {
            themeFlow.value = theme
        }
        override fun getEnrichmentJobForExpense(expenseId: Long): Flow<AiEnrichmentJobEntity?> = flowOf(null)
        override suspend fun replaceAllExpensesInTransaction(expenses: List<Expense>) {
            expensesFlow.value = expenses
        }
    }
}
