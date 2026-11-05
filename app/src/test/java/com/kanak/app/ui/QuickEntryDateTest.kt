package com.kanak.app.ui

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.Expense
import com.kanak.app.model.EntrySource
import com.kanak.app.voice.VoiceInputRepository
import com.kanak.app.voice.VoiceInputError
import org.junit.Assert.assertTrue
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.ui.dashboard.HomeViewModel
import com.kanak.app.ui.entry.ExpenseDateOption
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
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class QuickEntryDateTest {

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
    fun todayDefault_initializesViewModelWithTodayOption() = runTest {
        val fixedTime = 1716500000000L
        val viewModel = QuickEntryViewModel(
            expenseRepository = fakeRepository,
            timeProvider = { fixedTime }
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ExpenseDateOption.TODAY, state.selectedDateOption)
        assertEquals(fixedTime, state.selectedDateMillis)
        assertEquals("Today", state.selectedDateFormatted)
    }

    @Test
    fun yesterdayAcrossYearBoundary_calculatesDec31FromJan1() = runTest {
        // Jan 1, 2024 10:00:00 UTC
        val jan1Cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.JANUARY, 1, 10, 0, 0)
        }
        val viewModel = QuickEntryViewModel(
            expenseRepository = fakeRepository,
            timeProvider = { jan1Cal.timeInMillis },
            timeZone = TimeZone.getTimeZone("UTC")
        )

        viewModel.onDateOptionSelected(ExpenseDateOption.YESTERDAY)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ExpenseDateOption.YESTERDAY, state.selectedDateOption)

        val resultCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = state.selectedDateMillis
        }
        assertEquals(2023, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, resultCal.get(Calendar.MONTH))
        assertEquals(31, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun yesterdayAcrossLeapYear_calculatesFeb29FromMarch1() = runTest {
        // March 1, 2024 (leap year)
        val march1Leap = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.MARCH, 1, 12, 0, 0)
        }
        val viewModelLeap = QuickEntryViewModel(
            expenseRepository = fakeRepository,
            timeProvider = { march1Leap.timeInMillis },
            timeZone = TimeZone.getTimeZone("UTC")
        )
        viewModelLeap.onDateOptionSelected(ExpenseDateOption.YESTERDAY)
        testDispatcher.scheduler.advanceUntilIdle()

        val leapCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = viewModelLeap.uiState.value.selectedDateMillis
        }
        assertEquals(2024, leapCal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, leapCal.get(Calendar.MONTH))
        assertEquals(29, leapCal.get(Calendar.DAY_OF_MONTH))

        // March 1, 2023 (non-leap year)
        val march1NonLeap = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2023, Calendar.MARCH, 1, 12, 0, 0)
        }
        val viewModelNonLeap = QuickEntryViewModel(
            expenseRepository = fakeRepository,
            timeProvider = { march1NonLeap.timeInMillis },
            timeZone = TimeZone.getTimeZone("UTC")
        )
        viewModelNonLeap.onDateOptionSelected(ExpenseDateOption.YESTERDAY)
        testDispatcher.scheduler.advanceUntilIdle()

        val nonLeapCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = viewModelNonLeap.uiState.value.selectedDateMillis
        }
        assertEquals(2023, nonLeapCal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, nonLeapCal.get(Calendar.MONTH))
        assertEquals(28, nonLeapCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun chosenDateInPreviousMonth_updatesStateAndFormattedString() = runTest {
        // Current: May 24, 2024
        val currentCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.MAY, 24, 15, 0, 0)
        }
        val viewModel = QuickEntryViewModel(
            expenseRepository = fakeRepository,
            timeProvider = { currentCal.timeInMillis },
            timeZone = TimeZone.getTimeZone("UTC")
        )

        // Select Oct 15, 2023 in DatePicker (UTC millis)
        val oct15UtcPickerMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2023, Calendar.OCTOBER, 15, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        viewModel.onDateChosenFromPicker(oct15UtcPickerMillis)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ExpenseDateOption.CHOSEN_DATE, state.selectedDateOption)

        val selectedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = state.selectedDateMillis
        }
        assertEquals(2023, selectedCal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, selectedCal.get(Calendar.MONTH))
        assertEquals(15, selectedCal.get(Calendar.DAY_OF_MONTH))
        assertNull(state.dateError)
    }

    @Test
    fun futureDateRejection_setsErrorAndPreventsSave() = runTest {
        // Today: May 24, 2024
        val nowCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.MAY, 24, 10, 0, 0)
        }
        val viewModel = QuickEntryViewModel(
            expenseRepository = fakeRepository,
            timeProvider = { nowCal.timeInMillis },
            timeZone = TimeZone.getTimeZone("UTC")
        )

        // Tomorrow: May 25, 2024
        val tomorrowUtcPickerMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.MAY, 25, 0, 0, 0)
        }.timeInMillis

        viewModel.onDateChosenFromPicker(tomorrowUtcPickerMillis)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.dateError)
        assertEquals("Future dates are not allowed", viewModel.uiState.value.dateError)
        assertFalse(viewModel.uiState.value.isValidInput)

        // Attempt save
        viewModel.onAmountChanged("50.00")
        viewModel.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify not saved to repository
        assertEquals(0, fakeRepository.savedExpenses.size)
    }

    @Test
    fun datePickerConversion_asiaKolkataAndNewYorkTimeZones() = runTest {
        val kolkataTz = TimeZone.getTimeZone("Asia/Kolkata") // UTC+5:30
        val newYorkTz = TimeZone.getTimeZone("America/New_York") // UTC-5:00

        // DatePicker UTC millis for May 24, 2024 (00:00:00 UTC)
        val datePickerUtcMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.MAY, 24, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Fixed reference local time: 14:30:00
        val refTimeKolkata = Calendar.getInstance(kolkataTz).apply { set(2024, Calendar.MAY, 24, 14, 30, 0) }.timeInMillis
        val refTimeNY = Calendar.getInstance(newYorkTz).apply { set(2024, Calendar.MAY, 24, 14, 30, 0) }.timeInMillis

        val kolkataLocalMillis = QuickEntryViewModel.convertPickerUtcToLocalMillis(
            utcMillis = datePickerUtcMillis,
            timeProvider = { refTimeKolkata },
            timeZone = kolkataTz
        )

        val kolkataCal = Calendar.getInstance(kolkataTz).apply { timeInMillis = kolkataLocalMillis }
        assertEquals(2024, kolkataCal.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, kolkataCal.get(Calendar.MONTH))
        assertEquals(24, kolkataCal.get(Calendar.DAY_OF_MONTH))

        val newYorkLocalMillis = QuickEntryViewModel.convertPickerUtcToLocalMillis(
            utcMillis = datePickerUtcMillis,
            timeProvider = { refTimeNY },
            timeZone = newYorkTz
        )

        val nyCal = Calendar.getInstance(newYorkTz).apply { timeInMillis = newYorkLocalMillis }
        assertEquals(2024, nyCal.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, nyCal.get(Calendar.MONTH))
        assertEquals(24, nyCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun daylightSavingBoundary_preservesExactCalendarDay() = runTest {
        // US Daylight Saving transition: Nov 3, 2024 (25-hour day)
        val nyTz = TimeZone.getTimeZone("America/New_York")
        val nov3Cal = Calendar.getInstance(nyTz).apply {
            set(2024, Calendar.NOVEMBER, 3, 12, 0, 0)
        }

        val viewModel = QuickEntryViewModel(
            expenseRepository = fakeRepository,
            timeProvider = { nov3Cal.timeInMillis },
            timeZone = nyTz
        )

        viewModel.onDateOptionSelected(ExpenseDateOption.YESTERDAY)
        testDispatcher.scheduler.advanceUntilIdle()

        val nov2Cal = Calendar.getInstance(nyTz).apply {
            timeInMillis = viewModel.uiState.value.selectedDateMillis
        }
        assertEquals(2024, nov2Cal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, nov2Cal.get(Calendar.MONTH))
        assertEquals(2, nov2Cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun selectedDate_reachesPersistedExpenseThroughTypedAndVoicePaths() = runTest {
        val nowCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.MAY, 24, 10, 0, 0)
        }
        val viewModel = QuickEntryViewModel(
            expenseRepository = fakeRepository,
            timeProvider = { nowCal.timeInMillis },
            timeZone = TimeZone.getTimeZone("UTC")
        )

        // 1. Typed entry with Yesterday
        viewModel.onDateOptionSelected(ExpenseDateOption.YESTERDAY)
        viewModel.onAmountChanged("25.00")
        viewModel.onCategorySelected(ExpenseCategory.FOOD)
        viewModel.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.savedExpenses.size)
        val savedTyped = fakeRepository.savedExpenses[0]

        val yesterdayCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = savedTyped.occurredAt
        }
        assertEquals(23, yesterdayCal.get(Calendar.DAY_OF_MONTH))

        // 2. Exercise the real recognizer callback and shared save path.
        val voiceViewModel = QuickEntryViewModel(fakeRepository, FakeVoiceRepository(),
            { nowCal.timeInMillis }, TimeZone.getTimeZone("UTC"))
        voiceViewModel.onDateOptionSelected(ExpenseDateOption.YESTERDAY)
        voiceViewModel.onMicTapped()
        assertEquals(EntrySource.VOICE, voiceViewModel.uiState.value.source)
        voiceViewModel.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, fakeRepository.savedExpenses.size)
        val savedVoice = fakeRepository.savedExpenses[1]
        assertEquals(EntrySource.VOICE, savedVoice.source)

        val savedVoiceCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = savedVoice.occurredAt
        }
        assertEquals(23, savedVoiceCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun resetAfterSuccess_andPreservationAfterFailure() = runTest {
        val nowCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.MAY, 24, 10, 0, 0)
        }
        val viewModel = QuickEntryViewModel(
            expenseRepository = fakeRepository,
            timeProvider = { nowCal.timeInMillis },
            timeZone = TimeZone.getTimeZone("UTC")
        )

        viewModel.onDateOptionSelected(ExpenseDateOption.YESTERDAY)

        // Attempt save with invalid zero amount
        viewModel.onAmountChanged("0.00")
        viewModel.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()

        // Save fails -> Yesterday remains preserved
        assertEquals(ExpenseDateOption.YESTERDAY, viewModel.uiState.value.selectedDateOption)

        // Fix amount and save successfully
        viewModel.onAmountChanged("30.00")
        viewModel.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()

        // Save succeeds -> resets date option back to TODAY
        assertEquals(ExpenseDateOption.TODAY, viewModel.uiState.value.selectedDateOption)
    }

    @Test
    fun backdatedExpense_includedInMonthTotalButExcludedFromTodayTotal() = runTest {
        val todayCal = Calendar.getInstance()
        val yesterdayCal = (todayCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val todayExpense = Expense(id = 1L, amountMinor = 10000L, occurredAt = todayCal.timeInMillis, category = ExpenseCategory.FOOD)
        val yesterdayExpense = Expense(id = 2L, amountMinor = 20000L, occurredAt = yesterdayCal.timeInMillis, category = ExpenseCategory.GROCERIES)

        fakeRepository.setExpenses(listOf(todayExpense, yesterdayExpense))

        val homeViewModel = HomeViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val homeState = homeViewModel.uiState.value

        // Verify Today total includes ONLY today's expense (10000L), excluding Yesterday
        assertEquals(10000L, homeState.todayTotalMinor)

        // Verify month total includes today's expense and yesterday's expense if in the same month
        val sameMonth = (todayCal.get(Calendar.MONTH) == yesterdayCal.get(Calendar.MONTH)) &&
                (todayCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR))
        val expectedMonthTotal = if (sameMonth) 30000L else 10000L
        assertEquals(expectedMonthTotal, homeState.currentMonthTotalMinor)
    }

    private fun instant(text: String): Long = java.time.Instant.parse(text).toEpochMilli()

    @Test fun pickerPreselectionUsesLocalCalendarDateNearMidnight() {
        val zone = TimeZone.getTimeZone("Asia/Kolkata")
        assertEquals(instant("2026-11-06T00:00:00Z"), QuickEntryViewModel.localMillisToPickerUtc(
            instant("2026-11-05T19:00:00Z"), zone))
    }

    @Test fun pickerFutureLimitUsesLocalTodayInBothOffsetDirections() {
        val now = instant("2026-11-05T19:00:00Z")
        assertTrue(QuickEntryViewModel.isPickerDateSelectable(
            instant("2026-11-06T00:00:00Z"), now, TimeZone.getTimeZone("Asia/Kolkata")))
        assertFalse(QuickEntryViewModel.isPickerDateSelectable(
            instant("2026-11-06T00:00:00Z"), now, TimeZone.getTimeZone("America/New_York")))
    }

    @Test fun yesterdayResolvesAtSaveAfterDraftCrossesMidnight() = runTest {
        var now = instant("2026-11-05T23:59:00Z")
        val vm = QuickEntryViewModel(fakeRepository, timeProvider = { now }, timeZone = TimeZone.getTimeZone("UTC"))
        vm.onDateOptionSelected(ExpenseDateOption.YESTERDAY)
        vm.onAmountChanged("10")
        now = instant("2026-11-06T00:01:00Z")
        vm.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(instant("2026-11-05T00:01:00Z"), fakeRepository.savedExpenses.single().occurredAt)
    }

    @Test fun chosenDateIsFixedButUsesTimeAtSave() = runTest {
        var now = instant("2026-11-06T12:00:00Z")
        val vm = QuickEntryViewModel(fakeRepository, timeProvider = { now }, timeZone = TimeZone.getTimeZone("UTC"))
        vm.onDateChosenFromPicker(instant("2026-10-15T00:00:00Z"))
        vm.onAmountChanged("10")
        now = instant("2026-11-07T13:30:00Z")
        vm.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(instant("2026-10-15T13:30:00Z"), fakeRepository.savedExpenses.single().occurredAt)
    }

    @Test fun failedRepositorySavePreservesDraftAndDoesNotCloseSheet() = runTest {
        fakeRepository.failSave = true
        var closed = false
        val vm = QuickEntryViewModel(fakeRepository)
        vm.onDateOptionSelected(ExpenseDateOption.YESTERDAY)
        vm.onAmountChanged("10")
        vm.saveExpense { closed = true }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(closed)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals("10", vm.uiState.value.amountText)
        assertEquals(ExpenseDateOption.YESTERDAY, vm.uiState.value.selectedDateOption)
        fakeRepository.failSave = false
        vm.saveExpense { closed = true }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(closed)
    }

    @Test fun discardingDraftResetsDateAndContent() = runTest {
        val vm = QuickEntryViewModel(fakeRepository)
        vm.onDateOptionSelected(ExpenseDateOption.YESTERDAY)
        vm.onAmountChanged("10")
        vm.onNoteChanged("Gift for Swathi")
        vm.discardDraft()
        assertEquals(ExpenseDateOption.TODAY, vm.uiState.value.selectedDateOption)
        assertEquals("", vm.uiState.value.amountText)
        assertEquals("", vm.uiState.value.note)
        assertNull(vm.uiState.value.selectedCategory)
    }

    private class FakeVoiceRepository : VoiceInputRepository {
        override val isAvailable = true
        override val isListening = MutableStateFlow(false)
        override fun startListening(onResult: (String) -> Unit, onError: (VoiceInputError) -> Unit) {
            onResult("50 bus ticket")
        }
        override fun stopListening() { }
        override fun destroy() { }
    }

    private class FakeExpenseRepository : ExpenseRepository {
        var failSave = false
        val savedExpenses = mutableListOf<Expense>()
        val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        private val currencyFlow = MutableStateFlow("INR")
        private val themeFlow = MutableStateFlow(ThemePreference.DARK)

        fun setExpenses(list: List<Expense>) {
            expensesFlow.value = list
        }

        override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow
        override fun getExpenseById(id: Long): Flow<Expense?> = flowOf(null)
        override suspend fun saveExpense(expense: Expense): Long {
            check(!failSave) { "Simulated storage failure" }
            savedExpenses.add(expense)
            expensesFlow.value = expensesFlow.value + expense
            return savedExpenses.size.toLong()
        }
        override suspend fun deleteExpense(id: Long) {}
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
