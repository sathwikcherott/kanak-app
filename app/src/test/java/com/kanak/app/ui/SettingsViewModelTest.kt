package com.kanak.app.ui

import android.content.Context
import android.net.Uri
import com.kanak.app.data.backup.BackupRepository
import com.kanak.app.data.backup.BackupSummary
import com.kanak.app.data.backup.KanakBackup
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.Expense
import com.kanak.app.ui.settings.SettingsViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepository = FakeExpenseRepository()
    private val fakeBackupRepository = FakeBackupRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsThemeAndCurrencyFromRepository() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeBackupRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ThemePreference.DARK, state.themePreference)
        assertEquals("INR", state.currency)
        assertFalse(state.showCurrencyDialog)
    }

    @Test
    fun onThemeSelected_updatesStateAndSavesToRepository() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeBackupRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Select SYSTEM theme
        viewModel.onThemeSelected(ThemePreference.SYSTEM)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ThemePreference.SYSTEM, viewModel.uiState.value.themePreference)
        assertEquals(ThemePreference.SYSTEM, fakeRepository.themeFlow.value)

        // Select LIGHT theme
        viewModel.onThemeSelected(ThemePreference.LIGHT)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ThemePreference.LIGHT, viewModel.uiState.value.themePreference)
        assertEquals(ThemePreference.LIGHT, fakeRepository.themeFlow.value)
    }

    @Test
    fun onCurrencySelected_updatesCurrencyAndDismissesDialog() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeBackupRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openCurrencyDialog()
        assertTrue(viewModel.uiState.value.showCurrencyDialog)

        viewModel.onCurrencySelected("USD")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("USD", viewModel.uiState.value.currency)
        assertFalse(viewModel.uiState.value.showCurrencyDialog)
        assertEquals("USD", fakeRepository.currencyFlow.value)
    }

    @Test
    fun currencyDialog_opensAndDismissesCorrectly() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeBackupRepository)

        viewModel.openCurrencyDialog()
        assertTrue(viewModel.uiState.value.showCurrencyDialog)

        viewModel.dismissCurrencyDialog()
        assertFalse(viewModel.uiState.value.showCurrencyDialog)
    }

    private class FakeExpenseRepository : ExpenseRepository {
        val currencyFlow = MutableStateFlow("INR")
        val themeFlow = MutableStateFlow(ThemePreference.DARK)

        override fun getAllExpenses(): Flow<List<Expense>> = flowOf(emptyList())
        override fun getExpenseById(id: Long): Flow<Expense?> = flowOf(null)
        override suspend fun saveExpense(expense: Expense): Long = 1L
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
        override suspend fun replaceAllExpensesInTransaction(expenses: List<Expense>) {}
    }

    private class FakeBackupRepository : BackupRepository {
        val lastBackupFlow = MutableStateFlow<Long?>(null)

        override suspend fun createBackup(): KanakBackup = KanakBackup()
        override suspend fun exportBackupToUri(context: Context, uri: Uri): Result<Unit> = Result.success(Unit)
        override suspend fun parseAndValidateBackupFromUri(context: Context, uri: Uri): Result<BackupSummary> =
            Result.success(
                BackupSummary(1, System.currentTimeMillis(), 0, 0, "INR", ThemePreference.DARK, KanakBackup())
            )
        override suspend fun restoreBackup(backup: KanakBackup): Result<Unit> = Result.success(Unit)
        override fun getLastBackupExportedTime(): Flow<Long?> = lastBackupFlow
        override suspend fun recordBackupExportedTime(timestamp: Long) {
            lastBackupFlow.value = timestamp
        }
    }
}
