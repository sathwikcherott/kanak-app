package com.kanak.app.ui

import com.kanak.app.ai.AiDescription
import com.kanak.app.ai.CloudAiCategorizer
import com.kanak.app.ai.FakeCloudAiCategorizer
import com.kanak.app.ai.model.AiCategorizationRequest
import com.kanak.app.ai.model.AiCategorizationResult
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.ui.entry.QuickEntryViewModel
import com.kanak.app.voice.FakeVoiceInputRepository
import com.kanak.app.voice.VoiceInputError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    fun aiSuggestion_sendsOnlyPreviewAndRequiresAcceptance() = runTest {
        val ai = FakeCloudAiCategorizer()
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, aiCategorizer = ai)
        val note = "₹250.50 acetaminophen"
        viewModel.onAmountChanged("250.50")
        viewModel.onNoteChanged(note)
        val preview = AiDescription.forSuggestion(note)
        assertEquals("acetaminophen", preview)
        assertNull(ai.lastRequest)

        viewModel.requestAiSuggestion(preview)
        advanceUntilIdle()

        assertEquals(AiCategorizationRequest(preview), ai.lastRequest)
        assertNull(viewModel.uiState.value.selectedCategory)
        assertEquals(ExpenseCategory.FOOD, viewModel.uiState.value.aiSuggestion)
        viewModel.applyAiSuggestion()
        viewModel.saveExpense()
        advanceUntilIdle()

        val expense = fakeExpenseRepository.savedExpenses.single()
        assertEquals(25050L, expense.amountMinor)
        assertEquals(note, expense.note)
        assertEquals(ExpenseCategory.FOOD, expense.category)
        assertEquals(CategorizationState.CONFIRMED, expense.categorizationState)
    }

    @Test
    fun aiSuggestion_manualCategoryCancelsPendingResult() = runTest {
        val response = CompletableDeferred<Result<AiCategorizationResult>>()
        val ai = object : CloudAiCategorizer {
            override suspend fun categorizeExpense(request: AiCategorizationRequest) = response.await()
        }
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, aiCategorizer = ai)
        viewModel.onNoteChanged("acetaminophen")
        viewModel.requestAiSuggestion("acetaminophen")
        runCurrent()
        assertTrue(viewModel.uiState.value.isRequestingAi)

        viewModel.onCategorySelected(ExpenseCategory.HEALTH)
        response.complete(Result.success(AiCategorizationResult(ExpenseCategory.FOOD, 1f, null)))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRequestingAi)
        assertNull(viewModel.uiState.value.aiSuggestion)
        assertEquals(ExpenseCategory.HEALTH, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun aiFailure_doesNotBlockLocalSave() = runTest {
        val ai = FakeCloudAiCategorizer(shouldSucceed = false)
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, aiCategorizer = ai)
        viewModel.onAmountChanged("25")
        viewModel.onNoteChanged("acetaminophen")
        viewModel.requestAiSuggestion("acetaminophen")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.aiMessage)
        assertTrue(viewModel.uiState.value.isValidInput)
        viewModel.saveExpense()
        advanceUntilIdle()
        assertNull(fakeExpenseRepository.savedExpenses.single().category)
    }

    @Test
    fun voiceCalculation_savesFullTotalAndOriginalDescription() = runTest {
        val transcript = "100 / (2 + 3) × 3 bus"
        val voice = FakeVoiceInputRepository(isAvailable = true, mockTranscript = transcript)
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, voice)
        viewModel.onMicTapped()
        viewModel.saveExpense()
        advanceUntilIdle()

        val saved = fakeExpenseRepository.savedExpenses.single()
        assertEquals(6000L, saved.amountMinor)
        assertEquals(transcript, saved.note)
        assertEquals(ExpenseCategory.TRANSPORT, saved.category)
    }

    @Test
    fun incompleteCalculation_clearsPreviousAmountAndBlocksSave() = runTest {
        val voice = FakeVoiceInputRepository(isAvailable = true, mockTranscript = "25 50 bus")
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, voice)
        viewModel.onAmountChanged("50")
        viewModel.onMicTapped()
        viewModel.saveExpense()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.amountText)
        assertEquals("25 50 bus", viewModel.uiState.value.note)
        assertTrue(fakeExpenseRepository.savedExpenses.isEmpty())
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
    fun onMicTapped_spokenArithmeticExpression_calculatesTotalAndInfersCategory() = runTest {
        val fakeVoiceRepo = FakeVoiceInputRepository(
            isAvailable = true,
            mockTranscript = "25 plus 50 bus"
        )
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, fakeVoiceRepo)

        viewModel.onMicTapped()

        assertEquals("75", viewModel.uiState.value.amountText)
        assertEquals(ExpenseCategory.TRANSPORT, viewModel.uiState.value.selectedCategory)
        assertEquals("25 plus 50 bus", viewModel.uiState.value.note)
        assertEquals(7500L, viewModel.uiState.value.amountMinor)
        assertTrue(viewModel.uiState.value.isValidInput)
    }

    @Test
    fun spokenAndTypedEquivalentExpenses_produceIdenticalResults() = runTest {
        // Voice entry: "25 plus 50 bus"
        val fakeVoiceRepo = FakeVoiceInputRepository(
            isAvailable = true,
            mockTranscript = "25 plus 50 bus"
        )
        val voiceVm = QuickEntryViewModel(fakeExpenseRepository, fakeVoiceRepo)
        voiceVm.onMicTapped()
        val voiceMinor = voiceVm.uiState.value.amountMinor

        // Typed expression: "25 + 50"
        val typedExprVm = QuickEntryViewModel(fakeExpenseRepository)
        typedExprVm.onAmountChanged("25 + 50")
        val typedExprMinor = typedExprVm.uiState.value.amountMinor

        // Typed total: "75"
        val typedTotalVm = QuickEntryViewModel(fakeExpenseRepository)
        typedTotalVm.onAmountChanged("75")
        val typedTotalMinor = typedTotalVm.uiState.value.amountMinor

        assertEquals(7500L, voiceMinor)
        assertEquals(voiceMinor, typedExprMinor)
        assertEquals(typedExprMinor, typedTotalMinor)
    }

    @Test
    fun onMicTapped_ambiguousMultiTransaction_asksForClarification() = runTest {
        val fakeVoiceRepo = FakeVoiceInputRepository(
            isAvailable = true,
            mockTranscript = "25 for lunch and 50 for dinner"
        )
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, fakeVoiceRepo)

        viewModel.onMicTapped()

        assertFalse(viewModel.uiState.value.isValidInput)
        assertNotNull(viewModel.uiState.value.amountError)
        assertEquals("Multiple expenses detected. Please clarify amount.", viewModel.uiState.value.amountError)
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
        override suspend fun replaceAllExpensesInTransaction(expenses: List<Expense>) {
            expensesFlow.value = expenses
        }
    }
}
