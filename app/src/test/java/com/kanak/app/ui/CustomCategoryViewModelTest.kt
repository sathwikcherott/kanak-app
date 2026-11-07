package com.kanak.app.ui

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.*
import com.kanak.app.ui.entry.QuickEntryViewModel
import com.kanak.app.ui.transactions.TransactionsViewModel
import com.kanak.app.ui.analytics.AnalyticsViewModel
import com.kanak.app.voice.FakeVoiceInputRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CustomCategoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = Repository()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun creatingCategorySelectsItAndPreservesExpenseDraft() = runTest(dispatcher) {
        val vm = QuickEntryViewModel(repo)
        runCurrent()
        vm.onAmountChanged("25.50")
        vm.onNoteChanged("My pet")
        vm.openNewCategoryDialog()
        vm.onNewCategoryNameChanged("Pet care")
        vm.createCategory()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showNewCategoryDialog)
        assertEquals("Pet care", vm.uiState.value.selectedCategory!!.displayName)
        assertEquals("25.50", vm.uiState.value.amountText)
        assertEquals("My pet", vm.uiState.value.note)
        vm.saveExpense()
        advanceUntilIdle()
        assertEquals(2550L, repo.expenses.value.single().amountMinor)
        assertEquals("Pet care", repo.expenses.value.single().category!!.displayName)
        vm.discardDraft()
        assertTrue(vm.uiState.value.availableCategories.any { it.displayName == "Pet care" })
    }
    @Test fun cancellationCreatesNothingAndFailureKeepsDialogEditable() = runTest(dispatcher) {
        val vm = QuickEntryViewModel(repo)
        vm.openNewCategoryDialog(); vm.onNewCategoryNameChanged("Pets"); vm.cancelNewCategory()
        assertEquals(0, repo.createCalls)
        vm.openNewCategoryDialog(); vm.onNewCategoryNameChanged("Pets")
        repo.failCreate = true
        vm.createCategory(); advanceUntilIdle()
        assertTrue(vm.uiState.value.showNewCategoryDialog)
        assertNotNull(vm.uiState.value.categoryError)
        assertFalse(vm.uiState.value.isCreatingCategory)
    }
    @Test fun repeatedTapCreatesOnlyOneCategoryAndCancellationCannotHidePendingWrite() = runTest(dispatcher) {
        val vm = QuickEntryViewModel(repo)
        vm.openNewCategoryDialog(); vm.onNewCategoryNameChanged("Pets")
        repo.gate = CompletableDeferred()
        vm.createCategory(); vm.createCategory(); vm.cancelNewCategory()
        runCurrent()
        assertTrue(vm.uiState.value.showNewCategoryDialog)
        assertEquals(1, repo.createCalls)
        repo.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(11, repo.categories.value.size)
        assertFalse(vm.uiState.value.showNewCategoryDialog)
    }
    @Test fun voiceDictationPreservesExplicitCustomCategoryAndDate() = runTest(dispatcher) {
        val vm = QuickEntryViewModel(repo, FakeVoiceInputRepository(mockTranscript = "50 for lunch"))
        runCurrent()
        val custom = repo.createCategory("Pet care")
        vm.onCategorySelected(custom)
        vm.onDateOptionSelected(com.kanak.app.ui.entry.ExpenseDateOption.YESTERDAY)
        vm.onMicTapped()
        assertEquals(custom, vm.uiState.value.selectedCategory)
        assertEquals("50", vm.uiState.value.amountText)
        assertEquals(com.kanak.app.ui.entry.ExpenseDateOption.YESTERDAY, vm.uiState.value.selectedDateOption)
    }
    @Test fun customCategoriesAreSeparateInFiltersEditingAndAnalytics() = runTest(dispatcher) {
        val custom = repo.createCategory("Pet care")
        repo.expenses.value = listOf(
            Expense(id = 1, amountMinor = 3500, category = custom),
            Expense(id = 2, amountMinor = 2000, category = ExpenseCategory.OTHER)
        )
        val transactions = TransactionsViewModel(repo)
        val analytics = AnalyticsViewModel(repo)
        advanceUntilIdle()
        assertTrue(transactions.uiState.value.availableCategories.contains(custom))
        transactions.onCategoryFilterSelected(custom)
        assertEquals(1, transactions.uiState.value.totalFilteredCount)
        assertEquals(3500L, transactions.uiState.value.totalFilteredAmountMinor)
        transactions.onExpenseClickedForEdit(repo.expenses.value.first())
        assertEquals(custom, transactions.uiState.value.editCategory)
        assertEquals(3500L, analytics.uiState.value.categoryBreakdown.single { it.category == custom }.amountMinor)
        assertEquals(5500L, analytics.uiState.value.totalSpendingMinor)
    }

    private class Repository : ExpenseRepository {
        val categories = MutableStateFlow(ExpenseCategory.entries)
        val expenses = MutableStateFlow<List<Expense>>(emptyList())
        var createCalls = 0
        var failCreate = false
        var gate: CompletableDeferred<Unit>? = null
        override fun getCategories(): Flow<List<ExpenseCategory>> = categories
        override suspend fun createCategory(name: String): ExpenseCategory {
            createCalls++
            gate?.await()
            if (failCreate) error("Category already exists")
            val category = ExpenseCategory.custom("custom:12345678-1234-1234-1234-123456789012", name)
            categories.value = categories.value + category
            return category
        }
        override fun getAllExpenses(): Flow<List<Expense>> = expenses
        override fun getExpenseById(id: Long): Flow<Expense?> = flowOf(expenses.value.find { it.id == id })
        override suspend fun saveExpense(expense: Expense): Long {
            expenses.value = expenses.value.filter { it.id != expense.id } + expense
            return 1
        }
        override suspend fun deleteExpense(id: Long) {}
        override fun getDefaultCurrency(): Flow<String> = flowOf("INR")
        override suspend fun setDefaultCurrency(currency: String) {}
        override fun getThemePreference(): Flow<ThemePreference> = flowOf(ThemePreference.DARK)
        override suspend fun setThemePreference(theme: ThemePreference) {}
        override fun getEnrichmentJobForExpense(expenseId: Long): Flow<AiEnrichmentJobEntity?> = flowOf(null)
        override suspend fun replaceAllExpensesInTransaction(expenses: List<Expense>) {}
    }
}
