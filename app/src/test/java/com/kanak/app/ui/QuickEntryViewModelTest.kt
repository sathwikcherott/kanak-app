package com.kanak.app.ui

import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.ui.entry.QuickEntryViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class QuickEntryViewModelTest {

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
    fun onAmountChanged_updatesAmountTextAndValidatesInput() = runTest {
        val viewModel = QuickEntryViewModel(fakeRepository)

        // Valid positive amount
        viewModel.onAmountChanged("25.50")
        assertEquals("25.50", viewModel.uiState.value.amountText)
        assertEquals(2550L, viewModel.uiState.value.amountMinor)
        assertTrue(viewModel.uiState.value.isValidInput)
        assertNull(viewModel.uiState.value.amountError)

        // Invalid zero amount
        viewModel.onAmountChanged("0")
        assertEquals("0", viewModel.uiState.value.amountText)
        assertEquals(0L, viewModel.uiState.value.amountMinor)
        assertFalse(viewModel.uiState.value.isValidInput)
        assertNotNull(viewModel.uiState.value.amountError)
    }

    @Test
    fun saveExpense_validInputSavesToRepositoryAndResetsForm() = runTest {
        val viewModel = QuickEntryViewModel(fakeRepository)
        viewModel.onAmountChanged("15.00")
        viewModel.onCategorySelected(ExpenseCategory.FOOD)
        viewModel.onNoteChanged("Lunch meeting")

        viewModel.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()

        // Local write completes immediately; form is reset & success message shown
        assertEquals("", viewModel.uiState.value.amountText)
        assertEquals("", viewModel.uiState.value.note)
        assertNull(viewModel.uiState.value.selectedCategory)
        assertEquals("Expense recorded successfully!", viewModel.uiState.value.userMessage)
        assertEquals(1, fakeRepository.savedExpenses.size)
        val saved = fakeRepository.savedExpenses[0]
        assertEquals(1500L, saved.amountMinor)
        assertEquals(ExpenseCategory.FOOD, saved.category)
        assertEquals("Lunch meeting", saved.note)
    }

    @Test
    fun saveExpense_invalidInputShowsValidationErrorWithoutSaving() = runTest {
        val viewModel = QuickEntryViewModel(fakeRepository)
        viewModel.onAmountChanged("") // Empty amount

        viewModel.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.amountError)
        assertEquals(0, fakeRepository.savedExpenses.size)
    }

    @Test
    fun onCategorySelected_updatesCategoryAndCategorizationState() = runTest {
        val viewModel = QuickEntryViewModel(fakeRepository)
        viewModel.onCategorySelected(ExpenseCategory.SHOPPING)

        assertEquals(ExpenseCategory.SHOPPING, viewModel.uiState.value.selectedCategory)
        assertEquals(CategorizationState.CONFIRMED, viewModel.uiState.value.categorizationState)

        // Deselect category
        viewModel.onCategorySelected(ExpenseCategory.SHOPPING)
        assertNull(viewModel.uiState.value.selectedCategory)
        assertEquals(CategorizationState.UNCATEGORIZED, viewModel.uiState.value.categorizationState)
    }

    @Test
    fun defaultTheme_isDark_andCanBeUpdated() = runTest {
        val viewModel = QuickEntryViewModel(fakeRepository)
        assertEquals(com.kanak.app.data.local.datastore.ThemePreference.DARK, viewModel.uiState.value.themePreference)

        viewModel.onThemePreferenceChanged(com.kanak.app.data.local.datastore.ThemePreference.LIGHT)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(com.kanak.app.data.local.datastore.ThemePreference.LIGHT, viewModel.uiState.value.themePreference)
    }

    private class FakeExpenseRepository : ExpenseRepository {
        val savedExpenses = mutableListOf<Expense>()
        private val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        private val currencyFlow = MutableStateFlow("INR")
        private val themeFlow = MutableStateFlow(com.kanak.app.data.local.datastore.ThemePreference.DARK)

        override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow
        override fun getExpenseById(id: Long): Flow<Expense?> = flowOf(null)
        override suspend fun saveExpense(expense: Expense): Long {
            savedExpenses.add(expense)
            expensesFlow.value = expensesFlow.value + expense
            return savedExpenses.size.toLong()
        }
        override suspend fun deleteExpense(id: Long) {}
        override fun getDefaultCurrency(): Flow<String> = currencyFlow
        override suspend fun setDefaultCurrency(currency: String) {
            currencyFlow.value = currency
        }
        override fun getThemePreference(): Flow<com.kanak.app.data.local.datastore.ThemePreference> = themeFlow
        override suspend fun setThemePreference(theme: com.kanak.app.data.local.datastore.ThemePreference) {
            themeFlow.value = theme
        }
        override fun getEnrichmentJobForExpense(expenseId: Long): Flow<com.kanak.app.data.local.entity.AiEnrichmentJobEntity?> = flowOf(null)
    }
}
