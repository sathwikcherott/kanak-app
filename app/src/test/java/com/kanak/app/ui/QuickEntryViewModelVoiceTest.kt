package com.kanak.app.ui

import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.ui.entry.QuickEntryViewModel
import com.kanak.app.voice.FakeVoiceInputRepository
import com.kanak.app.voice.VoiceInputError
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickEntryViewModelVoiceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeExpenseRepository = FakeExpenseRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onMicTapped_populatesVoiceDraftFieldsAndSetsVoiceSource() = runTest {
        val fakeVoiceRepo = FakeVoiceInputRepository(
            isAvailable = true,
            mockTranscript = "35.50 for dinner with friends"
        )
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, fakeVoiceRepo)

        viewModel.onMicTapped()

        assertEquals("35.50", viewModel.uiState.value.amountText)
        assertEquals(ExpenseCategory.FOOD, viewModel.uiState.value.selectedCategory)
        assertEquals("35.50 for dinner with friends", viewModel.uiState.value.note)
        assertEquals(EntrySource.VOICE, viewModel.uiState.value.source)
        assertEquals(CategorizationState.CONFIRMED, viewModel.uiState.value.categorizationState)
        assertTrue(viewModel.uiState.value.isValidInput)
        assertTrue(fakeVoiceRepo.wasStarted)
    }

    @Test
    fun voiceDraft_neverInheritsPreviousCategoryFromEarlierTransaction() = runTest {
        val fakeVoiceRepo = FakeVoiceInputRepository(
            isAvailable = true,
            mockTranscript = "120 chicken biryani"
        )
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, fakeVoiceRepo)

        // 1. User previously had TRANSPORT selected
        viewModel.onCategorySelected(ExpenseCategory.TRANSPORT)
        assertEquals(ExpenseCategory.TRANSPORT, viewModel.uiState.value.selectedCategory)

        // 2. Speak new voice draft
        viewModel.onMicTapped()

        // 3. Category MUST NOT inherit TRANSPORT; it must independently infer FOOD
        assertEquals("120", viewModel.uiState.value.amountText)
        assertEquals(ExpenseCategory.FOOD, viewModel.uiState.value.selectedCategory)
        assertEquals("120 chicken biryani", viewModel.uiState.value.note)
    }

    @Test
    fun onNoteChanged_preservesManuallySelectedCategoryAndDoesNotOverwriteWithAutoInference() = runTest {
        val viewModel = QuickEntryViewModel(fakeExpenseRepository)

        // User explicitly taps & confirms BILL
        viewModel.onCategorySelected(ExpenseCategory.BILL)
        assertEquals(ExpenseCategory.BILL, viewModel.uiState.value.selectedCategory)
        assertEquals(CategorizationState.CONFIRMED, viewModel.uiState.value.categorizationState)

        // User edits note with words containing food keywords ("swiggy")
        viewModel.onNoteChanged("Swiggy bill for office party")

        // User's manually confirmed category BILL MUST BE PRESERVED
        assertEquals(ExpenseCategory.BILL, viewModel.uiState.value.selectedCategory)
        assertEquals(CategorizationState.CONFIRMED, viewModel.uiState.value.categorizationState)
    }

    @Test
    fun onNoteChanged_reInfersCategoryWhenNotManuallyConfirmed() = runTest {
        val viewModel = QuickEntryViewModel(fakeExpenseRepository)

        // User types description without selecting category
        viewModel.onNoteChanged("80 bus ticket to metro station")

        // Category is dynamically auto-inferred as TRANSPORT
        assertEquals(ExpenseCategory.TRANSPORT, viewModel.uiState.value.selectedCategory)
        assertEquals(CategorizationState.AUTO_CATEGORIZED, viewModel.uiState.value.categorizationState)
    }

    @Test
    fun voiceDraft_usesSameValidationAndSavePathAsTypedInput() = runTest {
        val fakeVoiceRepo = FakeVoiceInputRepository(
            isAvailable = true,
            mockTranscript = "50 dollars shopping at Target"
        )
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, fakeVoiceRepo)

        // 1. Populate voice draft
        viewModel.onMicTapped()
        assertEquals(EntrySource.VOICE, viewModel.uiState.value.source)

        // 2. User edits draft note manually
        viewModel.onNoteChanged("50 dollars shopping at Target - bought shoes")

        // 3. Save draft using the exact same saveExpense() path
        viewModel.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeExpenseRepository.savedExpenses.size)
        val saved = fakeExpenseRepository.savedExpenses[0]
        assertEquals(5000L, saved.amountMinor)
        assertEquals(ExpenseCategory.SHOPPING, saved.category)
        assertEquals("50 dollars shopping at Target - bought shoes", saved.note)
    }

    @Test
    fun onMicTapped_handlesPermissionDeniedGracefullyAndKeepsTypingFunctional() = runTest {
        val fakeVoiceRepo = FakeVoiceInputRepository(
            isAvailable = true,
            mockError = VoiceInputError.PermissionDenied
        )
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, fakeVoiceRepo)

        viewModel.onMicTapped()

        assertFalse(viewModel.uiState.value.isListening)
        assertNotNull(viewModel.uiState.value.userMessage)
        assertEquals("Microphone permission is required for voice input", viewModel.uiState.value.userMessage)

        // Typing remains 100% functional
        viewModel.onAmountChanged("20.00")
        viewModel.onCategorySelected(ExpenseCategory.BILL)
        assertEquals("20.00", viewModel.uiState.value.amountText)
        assertTrue(viewModel.uiState.value.isValidInput)
    }

    @Test
    fun onMicTapped_handlesUnavailableVoiceRecognizerGracefully() = runTest {
        val fakeVoiceRepo = FakeVoiceInputRepository(isAvailable = false)
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, fakeVoiceRepo)

        viewModel.onMicTapped()

        assertFalse(viewModel.uiState.value.isListening)
        assertEquals("Voice recognition is not available on this device", viewModel.uiState.value.userMessage)
    }

    private class FakeExpenseRepository : ExpenseRepository {
        val savedExpenses = mutableListOf<Expense>()
        private val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        private val currencyFlow = MutableStateFlow("INR")

        override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow
        override fun getExpenseById(id: Long): Flow<Expense?> = flowOf(null)
        override suspend fun saveExpense(expense: Expense): Long {
            savedExpenses.add(expense)
            expensesFlow.value = expensesFlow.value + expense
            return savedExpenses.size.toLong()
        }
        override suspend fun deleteExpense(id: Long) {}
        override fun getDefaultCurrency(): Flow<String> = currencyFlow
        override suspend fun setDefaultCurrency(currency: String) {}
        private val themeFlow = MutableStateFlow(com.kanak.app.data.local.datastore.ThemePreference.DARK)
        override fun getThemePreference(): Flow<com.kanak.app.data.local.datastore.ThemePreference> = themeFlow
        override suspend fun setThemePreference(theme: com.kanak.app.data.local.datastore.ThemePreference) {
            themeFlow.value = theme
        }
        override fun getEnrichmentJobForExpense(expenseId: Long): Flow<com.kanak.app.data.local.entity.AiEnrichmentJobEntity?> = flowOf(null)
    }
}
