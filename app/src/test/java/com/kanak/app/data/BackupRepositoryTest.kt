package com.kanak.app.data

import com.kanak.app.data.backup.BackupExpense
import com.kanak.app.data.backup.BackupException
import com.kanak.app.data.backup.BackupPreferences
import com.kanak.app.data.backup.KanakBackup
import com.kanak.app.data.backup.KanakBackupSerializer
import com.kanak.app.data.backup.OfflineBackupRepository
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeExpenseRepository = FakeExpenseRepository()
    private val fakeUserPreferencesDataSource = FakeUserPreferencesDataSource()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun backupSerialization_exportsAllFieldsToValidJson() {
        val backup = KanakBackup(
            version = 1,
            exportedAt = 1700000000000L,
            appVersion = "1.0",
            preferences = BackupPreferences("USD", ThemePreference.LIGHT),
            expenses = listOf(
                BackupExpense(
                    id = 1L,
                    amountMinor = 2500L,
                    currency = "USD",
                    occurredAt = 1700000000000L,
                    note = "Taxi ride",
                    category = ExpenseCategory.TRANSPORT,
                    source = EntrySource.TYPED,
                    categorizationState = CategorizationState.CONFIRMED
                )
            )
        )

        val json = KanakBackupSerializer.toJson(backup)
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"exportedAt\": 1700000000000"))
        assertTrue(json.contains("\"defaultCurrency\": \"USD\""))
        assertTrue(json.contains("\"themePreference\": \"LIGHT\""))
        assertTrue(json.contains("\"amountMinor\": 2500"))
        assertTrue(json.contains("\"category\": \"TRANSPORT\""))
    }

    @Test
    fun backupDeserialization_parsesValidJsonSuccessfully() {
        val json = """
            {
              "version": 1,
              "exportedAt": 1700000000000,
              "appVersion": "1.0",
              "preferences": {
                "defaultCurrency": "EUR",
                "themePreference": "DARK"
              },
              "expenses": [
                {
                  "id": 10,
                  "amountMinor": 4500,
                  "currency": "EUR",
                  "occurredAt": 1700000000000,
                  "note": "Dinner",
                  "category": "FOOD",
                  "source": "VOICE",
                  "categorizationState": "CONFIRMED"
                }
              ]
            }
        """.trimIndent()

        val backup = KanakBackupSerializer.fromJson(json)
        assertEquals(1, backup.version)
        assertEquals("EUR", backup.preferences.defaultCurrency)
        assertEquals(ThemePreference.DARK, backup.preferences.themePreference)
        assertEquals(1, backup.expenses.size)
        val exp = backup.expenses[0]
        assertEquals(10L, exp.id)
        assertEquals(4500L, exp.amountMinor)
        assertEquals("EUR", exp.currency)
        assertEquals("Dinner", exp.note)
        assertEquals(ExpenseCategory.FOOD, exp.category)
        assertEquals(EntrySource.VOICE, exp.source)
    }

    @Test
    fun parseAndValidate_detectsCorruptedJson() {
        val corruptedJson = "not a json object"
        try {
            KanakBackupSerializer.fromJson(corruptedJson)
            fail("Should throw CorruptedJson exception")
        } catch (e: BackupException.CorruptedJson) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun parseAndValidate_detectsUnsupportedSchemaVersion() {
        val futureJson = """
            {
              "version": 99,
              "exportedAt": 1700000000000,
              "expenses": []
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(futureJson)
            fail("Should throw UnsupportedSchemaVersion exception")
        } catch (e: BackupException.UnsupportedSchemaVersion) {
            assertEquals(99, e.version)
        }
    }

    @Test
    fun parseAndValidate_detectsDuplicateExpenseIds() {
        val duplicateJson = """
            {
              "version": 1,
              "expenses": [
                { "id": 1, "amountMinor": 1000, "occurredAt": 1000, "currency": "INR", "source": "TYPED", "categorizationState": "CONFIRMED" },
                { "id": 1, "amountMinor": 2000, "occurredAt": 2000, "currency": "INR", "source": "TYPED", "categorizationState": "CONFIRMED" }
              ]
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(duplicateJson)
            fail("Should throw DuplicateExpenseIds exception")
        } catch (e: BackupException.DuplicateExpenseIds) {
            assertTrue(e.message!!.contains("Duplicate expense ID"))
        }
    }

    @Test
    fun parseAndValidate_detectsNegativeMonetaryValues() {
        val negativeJson = """
            {
              "version": 1,
              "expenses": [
                { "id": 1, "amountMinor": -500, "occurredAt": 1000, "currency": "INR", "source": "TYPED", "categorizationState": "CONFIRMED" }
              ]
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(negativeJson)
            fail("Should throw InvalidMonetaryValue exception")
        } catch (e: BackupException.InvalidMonetaryValue) {
            assertTrue(e.message!!.contains("Negative amount"))
        }
    }

    @Test
    fun parseAndValidate_handlesMissingOptionalFields() {
        val missingOptionalJson = """
            {
              "version": 1,
              "expenses": [
                { "id": 5, "amountMinor": 1200, "occurredAt": 1000 }
              ]
            }
        """.trimIndent()

        val backup = KanakBackupSerializer.fromJson(missingOptionalJson)
        assertEquals(1, backup.expenses.size)
        val exp = backup.expenses[0]
        assertEquals(5L, exp.id)
        assertEquals(1200L, exp.amountMinor)
        assertNull(exp.note)
        assertNull(exp.category)
        assertEquals("INR", exp.currency)
    }

    @Test
    fun parseAndValidate_handlesInvalidCategoryReferencesSafely() {
        val invalidCategoryJson = """
            {
              "version": 1,
              "expenses": [
                { "id": 5, "amountMinor": 1200, "occurredAt": 1000, "category": "UNKNOWN_FUTURE_CATEGORY" }
              ]
            }
        """.trimIndent()

        val backup = KanakBackupSerializer.fromJson(invalidCategoryJson)
        assertEquals(1, backup.expenses.size)
        assertNull(backup.expenses[0].category)
    }

    @Test
    fun restoreBackup_atomicReplacementAndPreferencesUpdate() = runTest {
        // Initial state in repository
        val initialExpense = Expense(id = 99L, amountMinor = 9999L, category = ExpenseCategory.BILL)
        fakeExpenseRepository.setExpenses(listOf(initialExpense))

        val repository = OfflineBackupRepository(fakeExpenseRepository, fakeUserPreferencesDataSource)

        val backup = KanakBackup(
            version = 1,
            preferences = BackupPreferences("EUR", ThemePreference.LIGHT),
            expenses = listOf(
                BackupExpense(id = 1L, amountMinor = 5000L, currency = "EUR", occurredAt = 1000L, category = ExpenseCategory.FOOD),
                BackupExpense(id = 2L, amountMinor = 10000L, currency = "EUR", occurredAt = 2000L, category = ExpenseCategory.GIRLFRIEND)
            )
        )

        val result = repository.restoreBackup(backup)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertEquals(2, fakeExpenseRepository.expensesFlow.value.size)
        assertEquals("EUR", fakeExpenseRepository.currencyFlow.value)
        assertEquals(ThemePreference.LIGHT, fakeExpenseRepository.themeFlow.value)
        assertEquals(1L, fakeExpenseRepository.expensesFlow.value[0].id)
        assertEquals(2L, fakeExpenseRepository.expensesFlow.value[1].id)
    }

    @Test
    fun restoreBackup_failureRollsBackAndPreservesExistingData() = runTest {
        val initialExpense = Expense(id = 99L, amountMinor = 9999L, category = ExpenseCategory.BILL)
        fakeExpenseRepository.setExpenses(listOf(initialExpense))
        fakeExpenseRepository.shouldFailOnReplace = true

        val repository = OfflineBackupRepository(fakeExpenseRepository, fakeUserPreferencesDataSource)

        val backup = KanakBackup(
            version = 1,
            expenses = listOf(BackupExpense(id = 1L, amountMinor = 5000L, currency = "INR", occurredAt = 1000L))
        )

        val result = repository.restoreBackup(backup)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.isFailure)
        // Verify initial expense remains preserved
        assertEquals(1, fakeExpenseRepository.expensesFlow.value.size)
        assertEquals(99L, fakeExpenseRepository.expensesFlow.value[0].id)
    }

    @Test
    fun roundTrip_preservesExactDataConsistency() {
        val original = KanakBackup(
            version = 1,
            exportedAt = 1715000000000L,
            appVersion = "1.0",
            preferences = BackupPreferences("GBP", ThemePreference.DARK),
            expenses = listOf(
                BackupExpense(
                    id = 101L,
                    amountMinor = 12345L,
                    currency = "GBP",
                    occurredAt = 1715000000000L,
                    note = "Special \"quoted\" note\nLine two",
                    category = ExpenseCategory.GIRLFRIEND,
                    source = EntrySource.VOICE,
                    categorizationState = CategorizationState.CONFIRMED
                )
            )
        )

        val json = KanakBackupSerializer.toJson(original)
        val restored = KanakBackupSerializer.fromJson(json)

        assertEquals(original.version, restored.version)
        assertEquals(original.exportedAt, restored.exportedAt)
        assertEquals(original.preferences.defaultCurrency, restored.preferences.defaultCurrency)
        assertEquals(original.preferences.themePreference, restored.preferences.themePreference)
        assertEquals(original.expenses.size, restored.expenses.size)

        val origExp = original.expenses[0]
        val restExp = restored.expenses[0]

        assertEquals(origExp.id, restExp.id)
        assertEquals(origExp.amountMinor, restExp.amountMinor)
        assertEquals(origExp.currency, restExp.currency)
        assertEquals(origExp.occurredAt, restExp.occurredAt)
        assertEquals(origExp.note, restExp.note)
        assertEquals(origExp.category, restExp.category)
        assertEquals(origExp.source, restExp.source)
        assertEquals(origExp.categorizationState, restExp.categorizationState)
    }

    private class FakeExpenseRepository : ExpenseRepository {
        val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        val currencyFlow = MutableStateFlow("INR")
        val themeFlow = MutableStateFlow(ThemePreference.DARK)
        var shouldFailOnReplace = false

        fun setExpenses(list: List<Expense>) {
            expensesFlow.value = list
        }

        override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow
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

        override suspend fun replaceAllExpensesInTransaction(expenses: List<Expense>) {
            if (shouldFailOnReplace) {
                throw IllegalStateException("Simulated database transaction failure")
            }
            expensesFlow.value = expenses
        }
    }

    private class FakeUserPreferencesDataSource : com.kanak.app.data.local.datastore.UserPreferencesDataSource {
        private val _currency = MutableStateFlow("INR")
        private val _theme = MutableStateFlow(ThemePreference.DARK)
        private val _lastBackupTime = MutableStateFlow<Long?>(null)

        override val defaultCurrency: Flow<String> = _currency
        override suspend fun setDefaultCurrency(currency: String) { _currency.value = currency }
        override val themePreference: Flow<ThemePreference> = _theme
        override suspend fun setThemePreference(theme: ThemePreference) { _theme.value = theme }
        override val lastBackupExportedTime: Flow<Long?> = _lastBackupTime
        override suspend fun setLastBackupExportedTime(timestamp: Long) { _lastBackupTime.value = timestamp }
    }
}
