package com.kanak.app.ui

import com.kanak.app.ai.AiConnectionFailure
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
import kotlinx.coroutines.test.advanceTimeBy
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
    fun typingCableCharacterByCharacter_resetsDebounceTimerAndDoesNotMatchCab() = runTest {
        val requests = mutableListOf<String>()
        val ai = object : CloudAiCategorizer {
            override suspend fun categorizeExpense(request: AiCategorizationRequest): Result<AiCategorizationResult> {
                request.note?.let { requests.add(it) }
                return Result.success(AiCategorizationResult(ExpenseCategory.TECH, 1f, null))
            }
        }
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, aiCategorizer = ai)

        // Type "c", "ca", "cab", "cabl", "cable" with 200ms between keystrokes
        val text = "cable"
        for (i in 1..text.length) {
            viewModel.onNoteChanged(text.substring(0, i))
            advanceTimeBy(200L)
            runCurrent()
            assertTrue(requests.isEmpty())
        }

        // Advance full 1,200ms debounce after typing stops
        advanceTimeBy(1200L)
        advanceUntilIdle()

        assertEquals(listOf("cable"), requests)
        assertEquals(ExpenseCategory.TECH, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun typingCabAndStopping_matchesTransportCategory() = runTest {
        val ai = FakeCloudAiCategorizer(
            mockResult = AiCategorizationResult(ExpenseCategory.TRANSPORT, 1f, null)
        )
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, aiCategorizer = ai)

        viewModel.onNoteChanged("cab")
        assertEquals(ExpenseCategory.TRANSPORT, viewModel.uiState.value.selectedCategory)

        advanceTimeBy(1200L)
        advanceUntilIdle()

        assertEquals(ExpenseCategory.TRANSPORT, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun changingCabToCableWhileInFlight_cancelsOldRequestAndUsesLatestInput() = runTest {
        val cabDeferred = CompletableDeferred<Result<AiCategorizationResult>>()
        val cableDeferred = CompletableDeferred<Result<AiCategorizationResult>>()
        val requestedNotes = mutableListOf<String>()

        val ai = object : CloudAiCategorizer {
            override suspend fun categorizeExpense(request: AiCategorizationRequest): Result<AiCategorizationResult> {
                requestedNotes.add(request.note.orEmpty())
                return if (request.note == "cab") cabDeferred.await() else cableDeferred.await()
            }
        }
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, aiCategorizer = ai)

        viewModel.onNoteChanged("cab")
        advanceTimeBy(1200L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isRequestingAi)
        assertEquals(listOf("cab"), requestedNotes)

        viewModel.onNoteChanged("cable")

        cabDeferred.complete(Result.success(AiCategorizationResult(ExpenseCategory.TRANSPORT, 1f, null)))
        runCurrent()

        assertNull(viewModel.uiState.value.selectedCategory)

        advanceTimeBy(1200L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isRequestingAi)

        cableDeferred.complete(Result.success(AiCategorizationResult(ExpenseCategory.TECH, 1f, null)))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRequestingAi)
        assertEquals(ExpenseCategory.TECH, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun receivingStaleCabResponseAfterNewCableRequest_ignoresStaleResponse() = runTest {
        val requests = mutableListOf<String>()
        val ai = object : CloudAiCategorizer {
            override suspend fun categorizeExpense(request: AiCategorizationRequest): Result<AiCategorizationResult> {
                requests.add(request.note.orEmpty())
                if (request.note == "cab") {
                    kotlinx.coroutines.delay(2000L)
                    return Result.success(AiCategorizationResult(ExpenseCategory.TRANSPORT, 1f, null))
                }
                return Result.success(AiCategorizationResult(ExpenseCategory.TECH, 1f, null))
            }
        }
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, aiCategorizer = ai)

        viewModel.onNoteChanged("cab")
        advanceTimeBy(1200L)
        runCurrent()

        viewModel.onNoteChanged("cable")
        advanceTimeBy(1200L)
        advanceUntilIdle()

        assertEquals(ExpenseCategory.TECH, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun rapidTypingAndDeletion_onlyFiresForFinalInputAfterDebounce() = runTest {
        val requests = mutableListOf<String>()
        val ai = object : CloudAiCategorizer {
            override suspend fun categorizeExpense(request: AiCategorizationRequest): Result<AiCategorizationResult> {
                requests.add(request.note.orEmpty())
                return Result.success(AiCategorizationResult(ExpenseCategory.FOOD, 1f, null))
            }
        }
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, aiCategorizer = ai)

        viewModel.onNoteChanged("cable")
        advanceTimeBy(300L)
        viewModel.onNoteChanged("cab")
        advanceTimeBy(300L)
        viewModel.onNoteChanged("c")
        advanceTimeBy(300L)
        viewModel.onNoteChanged("coffee")

        assertTrue(requests.isEmpty())

        advanceTimeBy(1200L)
        advanceUntilIdle()

        assertEquals(listOf("coffee"), requests)
    }

    @Test
    fun manualCategorySelection_duringAiProcessing_preservesManualChoice() = runTest {
        val response = CompletableDeferred<Result<AiCategorizationResult>>()
        val ai = object : CloudAiCategorizer {
            override suspend fun categorizeExpense(request: AiCategorizationRequest) = response.await()
        }
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, aiCategorizer = ai)

        viewModel.onNoteChanged("acetaminophen")
        advanceTimeBy(1200L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isRequestingAi)

        viewModel.onCategorySelected(ExpenseCategory.HEALTH)
        response.complete(Result.success(AiCategorizationResult(ExpenseCategory.FOOD, 1f, null)))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRequestingAi)
        assertEquals(ExpenseCategory.HEALTH, viewModel.uiState.value.selectedCategory)
        assertEquals(CategorizationState.CONFIRMED, viewModel.uiState.value.categorizationState)
    }

    @Test
    fun voiceInputDescription_triggersDebouncedAiCategorization() = runTest {
        val ai = FakeCloudAiCategorizer(
            mockResult = AiCategorizationResult(ExpenseCategory.TECH, 1f, null)
        )
        val fakeVoiceRepo = FakeVoiceInputRepository(
            isAvailable = true,
            mockTranscript = "35.50 for keyboard"
        )
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, fakeVoiceRepo, aiCategorizer = ai)

        viewModel.onMicTapped()
        advanceTimeBy(1200L)
        advanceUntilIdle()

        assertEquals("35.50 for keyboard", viewModel.uiState.value.note)
        assertEquals(ExpenseCategory.TECH, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun backendTimeoutOrFailure_handlesGracefullyWithoutBlockingUser() = runTest {
        val ai = object : CloudAiCategorizer {
            override suspend fun categorizeExpense(request: AiCategorizationRequest): Result<AiCategorizationResult> =
                Result.failure(AiConnectionFailure("The AI connection timed out. Try again."))
        }
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, aiCategorizer = ai)

        viewModel.onAmountChanged("25")
        viewModel.onNoteChanged("keyboard")
        advanceTimeBy(1200L)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRequestingAi)
        assertTrue(viewModel.uiState.value.isValidInput)

        viewModel.saveExpense()
        advanceUntilIdle()
        assertEquals(1, fakeExpenseRepository.savedExpenses.size)
    }

    @Test
    fun voiceDraft_neverInheritsPreviousCategoryFromEarlierTransaction() = runTest {
        val fakeVoiceRepo = FakeVoiceInputRepository(
            isAvailable = true,
            mockTranscript = "120 chicken biryani"
        )
        val viewModel = QuickEntryViewModel(fakeExpenseRepository, fakeVoiceRepo)

        viewModel.onCategorySelected(ExpenseCategory.TRANSPORT)
        assertEquals(ExpenseCategory.TRANSPORT, viewModel.uiState.value.selectedCategory)

        viewModel.onMicTapped()

        assertEquals("120", viewModel.uiState.value.amountText)
        assertEquals(ExpenseCategory.FOOD, viewModel.uiState.value.selectedCategory)
        assertEquals("120 chicken biryani", viewModel.uiState.value.note)
    }

    @Test
    fun onNoteChanged_preservesManuallySelectedCategoryAndDoesNotOverwriteWithAutoInference() = runTest {
        val viewModel = QuickEntryViewModel(fakeExpenseRepository)

        viewModel.onCategorySelected(ExpenseCategory.BILL)
        assertEquals(ExpenseCategory.BILL, viewModel.uiState.value.selectedCategory)
        assertEquals(CategorizationState.CONFIRMED, viewModel.uiState.value.categorizationState)

        viewModel.onNoteChanged("Swiggy bill for office party")

        assertEquals(ExpenseCategory.BILL, viewModel.uiState.value.selectedCategory)
        assertEquals(CategorizationState.CONFIRMED, viewModel.uiState.value.categorizationState)
    }

    @Test
    fun onNoteChanged_reInfersCategoryWhenNotManuallyConfirmed() = runTest {
        val viewModel = QuickEntryViewModel(fakeExpenseRepository)

        viewModel.onNoteChanged("80 bus ticket to metro station")

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

        viewModel.onMicTapped()
        assertEquals(EntrySource.VOICE, viewModel.uiState.value.source)

        viewModel.onNoteChanged("50 dollars shopping at Target - bought shoes")

        viewModel.saveExpense()
        advanceUntilIdle()

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
        private val categoryFlow = MutableStateFlow<List<ExpenseCategory>>(ExpenseCategory.entries)
        override fun getCategories(): Flow<List<ExpenseCategory>> = categoryFlow
        override suspend fun createCategory(name: String): ExpenseCategory {
            val cat = ExpenseCategory.custom("custom:00000000-0000-0000-0000-000000000001", name)
            categoryFlow.value = categoryFlow.value + cat
            return cat
        }
    }
}
