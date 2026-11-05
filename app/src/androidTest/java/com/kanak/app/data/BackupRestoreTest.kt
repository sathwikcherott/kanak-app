package com.kanak.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kanak.app.ai.CloudAiCategorizer
import com.kanak.app.ai.model.AiCategorizationRequest
import com.kanak.app.ai.model.AiCategorizationResult
import com.kanak.app.data.backup.*
import com.kanak.app.data.local.KanakDatabase
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.datastore.UserPreferencesDataSource
import com.kanak.app.data.repository.OfflineExpenseRepository
import com.kanak.app.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real Room/SQLite, including a trigger that fails after deletion/partial insertion. */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private lateinit var db: KanakDatabase
    private lateinit var prefs: Preferences
    private lateinit var expenses: OfflineExpenseRepository
    private lateinit var backups: OfflineBackupRepository

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, KanakDatabase::class.java).build()
        prefs = Preferences()
        expenses = OfflineExpenseRepository(db.expenseDao(), prefs, db.aiEnrichmentJobDao(), db)
        backups = OfflineBackupRepository(expenses, prefs)
    }

    @After fun tearDown() { db.close() }

    private fun backup(vararg records: BackupExpense) = KanakBackup(
        preferences = BackupPreferences("USD", ThemePreference.LIGHT), expenses = records.toList()
    )
    private fun record(id: Long = 7L) = BackupExpense(
        id, 123456789012345L, "INR", 1715000000000L,
        "Yesterday with Swathi 🧡", ExpenseCategory.GIRLFRIEND, EntrySource.VOICE,
        CategorizationState.CONFIRMED
    )

    @Test fun restoreReplacesExpensesClearsJobsAndPreservesEveryFinancialField() = runBlocking {
        val oldId = expenses.saveExpense(Expense(amountMinor = 1500L))
        assertNotNull(db.aiEnrichmentJobDao().getJobForExpenseSync(oldId))
        val data = backup(record())
        assertEquals(RestoreOutcome.COMPLETE, backups.restoreBackup(data).getOrThrow())
        assertEquals(data.expenses.map { it.toDomainModel() }, expenses.getAllExpenses().first())
        assertNull(db.aiEnrichmentJobDao().getJobForExpenseSync(oldId))
        assertEquals("USD", prefs.defaultCurrency.first())
        assertEquals(ThemePreference.LIGHT, prefs.themePreference.first())
        assertEquals(99L, prefs.lastBackupExportedTime.first()) // never imported/rewritten
        val newId = expenses.saveExpense(Expense(amountMinor = 100L))
        assertTrue(newId > 7L)
        assertEquals(2, expenses.getAllExpenses().first().size)
    }

    @Test fun sqliteFailureAfterPartialInsertionRollsBackExpensesJobsAndLeavesPreferencesUntouched() = runBlocking {
        val oldId = expenses.saveExpense(Expense(amountMinor = 1500L, note = "Keep me"))
        val before = expenses.getAllExpenses().first()
        val job = db.aiEnrichmentJobDao().getJobForExpenseSync(oldId)
        // First backup record is inserted, then the second aborts. The entire transaction must roll back.
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_restore BEFORE INSERT ON expenses WHEN NEW.id = 8
            BEGIN SELECT RAISE(ABORT, 'Injected restore insertion failure'); END
        """.trimIndent())
        assertTrue(backups.restoreBackup(backup(record(), record(8L))).isFailure)
        assertEquals(before, expenses.getAllExpenses().first())
        assertEquals(job, db.aiEnrichmentJobDao().getJobForExpenseSync(oldId))
        assertEquals(0, prefs.restoreCalls)
        assertEquals("INR", prefs.defaultCurrency.first())
    }

    @Test fun invalidBackupPassedDirectlyCannotDeleteExistingData() = runBlocking {
        expenses.saveExpense(Expense(amountMinor = 900L))
        val before = expenses.getAllExpenses().first()
        val invalid = listOf(
            backup(record().copy(amountMinor = -1L)),
            backup(record(), record()),
            backup(record()).copy(version = 2),
            backup(record().copy(currency = "bad"))
        )
        for (data in invalid) {
            assertTrue(backups.restoreBackup(data).isFailure)
            assertEquals(before, expenses.getAllExpenses().first())
        }
        assertEquals(0, prefs.restoreCalls)
    }

    @Test fun failedQueueInsertionRollsBackNewExpenseSave() = runBlocking {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_job BEFORE INSERT ON ai_enrichment_jobs
            BEGIN SELECT RAISE(ABORT, 'Injected queue failure'); END
        """.trimIndent())
        try {
            expenses.saveExpense(Expense(amountMinor = 500L))
            fail("Save must fail if its queue insert fails")
        } catch (_: android.database.sqlite.SQLiteException) {
            assertTrue(expenses.getAllExpenses().first().isEmpty())
        }
    }

    @Test fun emptyBackupIsAnExplicitValidReplacement() = runBlocking {
        val id = expenses.saveExpense(Expense(amountMinor = 100L))
        assertTrue(backups.restoreBackup(backup()).isSuccess)
        assertTrue(expenses.getAllExpenses().first().isEmpty())
        assertNull(db.aiEnrichmentJobDao().getJobForExpenseSync(id))
    }

    @Test fun preferenceFailureReturnsPartialSuccessAndKeepsRestoredExpenses() = runBlocking {
        expenses.saveExpense(Expense(amountMinor = 100L))
        prefs.failRestore = true
        val outcome = backups.restoreBackup(backup(record())).getOrThrow()
        assertEquals(RestoreOutcome.EXPENSES_RESTORED_PREFERENCES_FAILED, outcome)
        assertEquals(listOf(record().toDomainModel()), expenses.getAllExpenses().first())
        assertEquals("INR", prefs.defaultCurrency.first())
        assertEquals(ThemePreference.DARK, prefs.themePreference.first())
    }

    @Test fun lateEnrichmentCannotUpdateRestoredExpenseWithReusedId() = runBlocking {
        val id = expenses.saveExpense(Expense(amountMinor = 500L, category = null, categorizationState = CategorizationState.UNCATEGORIZED))
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val worker = async(Dispatchers.IO) {
            expenses.processAiEnrichment(id, object : CloudAiCategorizer {
                override suspend fun categorizeExpense(request: AiCategorizationRequest): Result<AiCategorizationResult> {
                    started.complete(Unit)
                    finish.await()
                    return Result.success(AiCategorizationResult(ExpenseCategory.FOOD, 1f, "Old request"))
                }
            })
        }
        withTimeout(5000) { started.await() }
        val restored = record(id).copy(category = null, categorizationState = CategorizationState.UNCATEGORIZED)
        backups.restoreBackup(backup(restored)).getOrThrow()
        finish.complete(Unit)
        worker.await().getOrThrow()
        assertEquals(restored.toDomainModel(), expenses.getAllExpenses().first().single())
        assertNull(db.aiEnrichmentJobDao().getJobForExpenseSync(id))
    }

    @Test fun saveAndSnapshotWaitUntilRestorePreferencesFinish() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        prefs.beforeRestore = { entered.complete(Unit); finish.await() }
        val restore = async(Dispatchers.IO) { backups.restoreBackup(backup(record())).getOrThrow() }
        withTimeout(5000) { entered.await() }
        val saving = async(Dispatchers.IO) { expenses.saveExpense(Expense(amountMinor = 500L)) }
        val snapshot = async(Dispatchers.IO) { backups.createBackup() }
        try {
            delay(100)
            assertFalse(saving.isCompleted)
            assertFalse(snapshot.isCompleted)
        } finally {
            finish.complete(Unit)
        }
        assertEquals(RestoreOutcome.COMPLETE, restore.await())
        saving.await()
        val data = snapshot.await()
        assertEquals("USD", data.preferences.defaultCurrency)
        assertEquals(ThemePreference.LIGHT, data.preferences.themePreference)
        assertTrue(data.expenses.any { it.id == 7L })
    }

    @Test fun snapshotRoundTripPreservesDataAndExcludesQueue() = runBlocking {
        expenses.saveExpense(record().toDomainModel())
        val snapshot = backups.createBackup()
        assertEquals(snapshot, KanakBackupSerializer.fromJson(KanakBackupSerializer.toJson(snapshot)))
        assertFalse(KanakBackupSerializer.toJson(snapshot).contains("ai_enrichment_jobs"))
    }

    @Test fun cancellationWhileWaitingForRestoreLockNeverAppliesSecondBackup() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        prefs.beforeRestore = { entered.complete(Unit); finish.await() }
        val first = async(Dispatchers.IO) { backups.restoreBackup(backup(record())).getOrThrow() }
        withTimeout(5000) { entered.await() }
        val second = launch(Dispatchers.IO) { backups.restoreBackup(backup(record(100L))) }
        try {
            delay(100)
            second.cancelAndJoin()
        } finally {
            finish.complete(Unit)
        }
        first.await()
        assertEquals(listOf(record().toDomainModel()), expenses.getAllExpenses().first())
        expenses.saveExpense(Expense(amountMinor = 200L))
        assertEquals(2, expenses.getAllExpenses().first().size)
    }

    @Test fun fileExportPreviewAndRestoreUseSameValidatedDataEvenIfFileChanges() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File.createTempFile("kanak-backup-", ".json", context.cacheDir)
        try {
            val uri = android.net.Uri.fromFile(file)
            expenses.saveExpense(record().toDomainModel())
            backups.exportBackupToUri(context, uri).getOrThrow()
            expenses.saveExpense(Expense(amountMinor = 500L))
            val before = expenses.getAllExpenses().first()
            val summary = backups.parseAndValidateBackupFromUri(context, uri).getOrThrow()
            assertEquals(1, summary.totalExpenses)
            assertEquals(before, expenses.getAllExpenses().first()) // Preview is read-only.
            file.writeText("not the original backup anymore")
            backups.restoreBackup(summary.backup).getOrThrow()
            assertEquals(listOf(record().toDomainModel()), expenses.getAllExpenses().first())
        } finally { file.delete() }
    }

    @Test fun corruptedAndMissingFilesNeverTouchCurrentData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File.createTempFile("kanak-invalid-", ".json", context.cacheDir)
        try {
            expenses.saveExpense(Expense(amountMinor = 400L))
            val before = expenses.getAllExpenses().first()
            file.writeText("{broken")
            val uri = android.net.Uri.fromFile(file)
            assertTrue(backups.parseAndValidateBackupFromUri(context, uri).isFailure)
            file.delete()
            assertTrue(backups.parseAndValidateBackupFromUri(context, uri).isFailure)
            assertEquals(before, expenses.getAllExpenses().first())
            assertEquals(0, prefs.restoreCalls)
        } finally { file.delete() }
    }

    private class Preferences : UserPreferencesDataSource {
        override val defaultCurrency = MutableStateFlow("INR")
        override val themePreference = MutableStateFlow(ThemePreference.DARK)
        override val lastBackupExportedTime = MutableStateFlow<Long?>(99L)
        var failRestore = false
        var restoreCalls = 0
        var beforeRestore: suspend () -> Unit = {}
        override suspend fun setDefaultCurrency(currency: String) { defaultCurrency.value = currency }
        override suspend fun setThemePreference(theme: ThemePreference) { themePreference.value = theme }
        override suspend fun setLastBackupExportedTime(timestamp: Long) { lastBackupExportedTime.value = timestamp }
        override suspend fun restorePreferences(currency: String, theme: ThemePreference) {
            beforeRestore()
            restoreCalls++
            if (failRestore) error("Injected DataStore failure")
            defaultCurrency.value = currency
            themePreference.value = theme
        }
    }
}
