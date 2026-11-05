package com.kanak.app.ui

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kanak.app.data.backup.*
import com.kanak.app.data.local.KanakDatabase
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.datastore.UserPreferencesDataSource
import com.kanak.app.data.repository.OfflineExpenseRepository
import com.kanak.app.model.Expense
import com.kanak.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SettingsRestoreFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var db: KanakDatabase
    private lateinit var expenses: OfflineExpenseRepository
    private lateinit var backups: OfflineBackupRepository
    private lateinit var vm: SettingsViewModel
    private lateinit var file: java.io.File
    private val viewModels = ViewModelStore()

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, KanakDatabase::class.java).build()
        val prefs = object : UserPreferencesDataSource {
            override val defaultCurrency = MutableStateFlow("INR")
            override val themePreference = MutableStateFlow(ThemePreference.DARK)
            override val lastBackupExportedTime = MutableStateFlow<Long?>(null)
            override suspend fun setDefaultCurrency(currency: String) { defaultCurrency.value = currency }
            override suspend fun setThemePreference(theme: ThemePreference) { themePreference.value = theme }
            override suspend fun setLastBackupExportedTime(timestamp: Long) { lastBackupExportedTime.value = timestamp }
            override suspend fun restorePreferences(currency: String, theme: ThemePreference) {
                defaultCurrency.value = currency
                themePreference.value = theme
            }
        }
        expenses = OfflineExpenseRepository(db.expenseDao(), prefs, db.aiEnrichmentJobDao(), db)
        backups = OfflineBackupRepository(expenses, prefs)
        expenses.saveExpense(Expense(amountMinor = 100L))
        file = java.io.File.createTempFile("kanak-vm-", ".json", context.cacheDir)
        file.writeText(KanakBackupSerializer.toJson(KanakBackup(expenses = listOf(BackupExpense(50L, 500L, "INR", 1715000000000L)))))
    }

    @After fun tearDown() {
        instrumentation.runOnMainSync { viewModels.clear() }
        file.delete()
        db.close()
    }

    private fun start(repository: BackupRepository = backups) {
        instrumentation.runOnMainSync {
            vm = SettingsViewModel(expenses, repository)
            viewModels.put("settings", vm)
        }
    }
    private suspend fun preview() {
        instrumentation.runOnMainSync { vm.onRestoreFileSelected(context, android.net.Uri.fromFile(file)) }
        withTimeout(5000) { vm.uiState.first { it.pendingRestoreSummary != null && !it.backupBusy } }
    }

    @Test fun cancellingPreviewNeverWritesAndConfirmationWithoutPreviewDoesNothing() = runBlocking {
        start()
        val before = expenses.getAllExpenses().first()
        preview()
        assertEquals(before, expenses.getAllExpenses().first())
        instrumentation.runOnMainSync { vm.cancelRestore(); vm.confirmRestore() }
        assertNull(vm.uiState.value.pendingRestoreSummary)
        assertEquals(before, expenses.getAllExpenses().first())
    }

    @Test fun repeatedConfirmationAndCancelDuringRestoreCannotStartSecondOperation() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var restores = 0
        start(object : BackupRepository by backups {
            override suspend fun restoreBackup(backup: KanakBackup): Result<RestoreOutcome> {
                restores++
                entered.complete(Unit)
                finish.await()
                return backups.restoreBackup(backup)
            }
        })
        preview()
        instrumentation.runOnMainSync { vm.confirmRestore(); vm.confirmRestore(); vm.cancelRestore() }
        withTimeout(5000) { entered.await() }
        assertEquals(1, restores)
        assertNotNull(vm.uiState.value.pendingRestoreSummary)
        assertTrue(vm.uiState.value.isRestoringBackup)
        finish.complete(Unit)
        withTimeout(5000) { vm.uiState.first { !it.backupBusy && it.userMessage != null } }
        assertNull(vm.uiState.value.pendingRestoreSummary)
        assertEquals(50L, expenses.getAllExpenses().first().single().id)
    }

    @Test fun unexpectedFailureResetsBusyStateAndKeepsExpenses() = runBlocking {
        start(object : BackupRepository by backups {
            override suspend fun restoreBackup(backup: KanakBackup): Result<RestoreOutcome> = error("Injected failure")
        })
        val before = expenses.getAllExpenses().first()
        preview()
        instrumentation.runOnMainSync { vm.confirmRestore() }
        withTimeout(5000) { vm.uiState.first { !it.backupBusy && it.backupError != null } }
        assertEquals(before, expenses.getAllExpenses().first())
        assertNull(vm.uiState.value.pendingRestoreSummary)
    }

    @Test fun partialSuccessShowsWarningWithoutClaimingRestoreFailed() = runBlocking {
        start(object : BackupRepository by backups {
            override suspend fun restoreBackup(backup: KanakBackup): Result<RestoreOutcome> =
                Result.success(RestoreOutcome.EXPENSES_RESTORED_PREFERENCES_FAILED)
        })
        preview()
        instrumentation.runOnMainSync { vm.confirmRestore() }
        val state = withTimeout(5000) { vm.uiState.first { !it.backupBusy && it.restoreWarning != null } }
        assertNull(state.backupError)
        assertNull(state.userMessage)
        assertNull(state.pendingRestoreSummary)
    }
}
