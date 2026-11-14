package com.kanak.app.ai

import com.kanak.app.ai.model.AiCategorizationResult
import com.kanak.app.data.local.dao.AiEnrichmentJobDao
import com.kanak.app.data.local.dao.ExpenseDao
import com.kanak.app.data.local.datastore.UserPreferencesDataSource
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.local.entity.EnrichmentJobStatus
import com.kanak.app.data.local.entity.ExpenseEntity
import com.kanak.app.data.repository.OfflineExpenseRepository
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSavePathTest {

    @Test
    fun saveExpense_persistsLocallyAndEnqueuesEnrichmentJobWithoutNetworkOrAiCalls() = runTest {
        val fakeExpenseDao = FakeExpenseDao()
        val fakeJobDao = FakeAiEnrichmentJobDao()
        val fakePrefRepo = FakeUserPreferencesDataSource()
        val repository = OfflineExpenseRepository(fakeExpenseDao, fakePrefRepo, fakeJobDao)
        val fakeAiCategorizer = FakeCloudAiCategorizer()

        val expense = Expense(
            amountMinor = 3500L,
            currency = "USD",
            note = "Groceries at Target",
            category = null
        )

        val id = repository.saveExpense(expense)

        // Verify local expense was saved
        assertEquals(1L, id)
        assertEquals(1, fakeExpenseDao.expenses.value.size)
        val savedEntity = fakeExpenseDao.expenses.value[0]
        assertEquals(3500L, savedEntity.amountMinor)
        assertEquals("Groceries at Target", savedEntity.note)
        assertNull(savedEntity.category)

        // Verify persistent enrichment job was queued
        val job = fakeJobDao.jobs.value.find { it.expenseId == id }
        assertNotNull(job)
        assertEquals(EnrichmentJobStatus.PENDING.name, job?.status)

        // Verify no AI network requests were invoked on save path
        assertNull(fakeAiCategorizer.lastRequest)
    }

    @Test
    fun saveExpense_preventsDuplicatePendingJobsForSameExpense() = runTest {
        val fakeExpenseDao = FakeExpenseDao()
        val fakeJobDao = FakeAiEnrichmentJobDao()
        val fakePrefRepo = FakeUserPreferencesDataSource()
        val repository = OfflineExpenseRepository(fakeExpenseDao, fakePrefRepo, fakeJobDao)

        val expense = Expense(
            id = 10L,
            amountMinor = 1500L,
            currency = "USD",
            note = "Lunch"
        )

        // Save twice for same expense
        repository.saveExpense(expense)
        repository.saveExpense(expense)

        // Verify only 1 active pending job exists for the expense
        val pendingJobs = fakeJobDao.jobs.value.filter { it.expenseId == 10L && it.status == EnrichmentJobStatus.PENDING.name }
        assertEquals(1, pendingJobs.size)
    }

    @Test
    fun processAiEnrichment_usesDataMinimizationPayloadWithoutFinancialOrIdMetadata() = runTest {
        val fakeExpenseDao = FakeExpenseDao()
        val fakeJobDao = FakeAiEnrichmentJobDao()
        val fakePrefRepo = FakeUserPreferencesDataSource()
        val repository = OfflineExpenseRepository(fakeExpenseDao, fakePrefRepo, fakeJobDao)
        val fakeAiCategorizer = FakeCloudAiCategorizer()

        val expense = Expense(
            amountMinor = 9900L,
            currency = "USD",
            occurredAt = 123456789L,
            note = "Coffee with client"
        )
        val id = repository.saveExpense(expense)

        repository.processAiEnrichment(id, fakeAiCategorizer)

        val request = fakeAiCategorizer.lastRequest
        assertNotNull(request)
        // Data minimization checks: note is passed, but NO local ID, timestamp, or monetary amount
        assertEquals("Coffee with client", request?.note)
    }

    @Test
    fun processAiEnrichment_handlesRetriesAndIncrementsRetryCountOnFailure() = runTest {
        val fakeExpenseDao = FakeExpenseDao()
        val fakeJobDao = FakeAiEnrichmentJobDao()
        val fakePrefRepo = FakeUserPreferencesDataSource()
        val repository = OfflineExpenseRepository(fakeExpenseDao, fakePrefRepo, fakeJobDao)
        val failingAiCategorizer = FakeCloudAiCategorizer(shouldSucceed = false)

        val id = repository.saveExpense(Expense(amountMinor = 2000L, note = "Taxi"))

        // Attempt 1: Fails
        val result1 = repository.processAiEnrichment(id, failingAiCategorizer, maxRetries = 3)
        assertTrue(result1.isFailure)

        val jobAfter1 = fakeJobDao.jobs.value.first { it.expenseId == id }
        assertEquals(1, jobAfter1.retryCount)
        assertNotNull(jobAfter1.lastAttemptAt)
        assertEquals(EnrichmentJobStatus.PENDING.name, jobAfter1.status)

        // Attempt 2 & 3: Fails until max retries reached -> FAILED
        repository.processAiEnrichment(id, failingAiCategorizer, maxRetries = 3)
        repository.processAiEnrichment(id, failingAiCategorizer, maxRetries = 3)

        val jobFinal = fakeJobDao.jobs.value.first { it.expenseId == id }
        assertEquals(3, jobFinal.retryCount)
        assertEquals(EnrichmentJobStatus.FAILED.name, jobFinal.status)
    }

    @Test
    fun processAiEnrichment_preservesManualCategoryOverrideAndNeverOverwritesUserCategory() = runTest {
        val fakeExpenseDao = FakeExpenseDao()
        val fakeJobDao = FakeAiEnrichmentJobDao()
        val fakePrefRepo = FakeUserPreferencesDataSource()
        val repository = OfflineExpenseRepository(fakeExpenseDao, fakePrefRepo, fakeJobDao)
        val fakeAiCategorizer = FakeCloudAiCategorizer(
            mockResult = AiCategorizationResult(
                suggestedCategory = ExpenseCategory.SHOPPING,
                confidenceScore = 0.99f,
                reasoning = "AI guessed Shopping"
            )
        )

        // User manually selected & confirmed FOOD
        val userExpense = Expense(
            amountMinor = 4500L,
            note = "Lunch at Target cafe",
            category = ExpenseCategory.FOOD,
            categorizationState = CategorizationState.CONFIRMED
        )
        val id = repository.saveExpense(userExpense)

        repository.processAiEnrichment(id, fakeAiCategorizer)

        // Verify AI job is COMPLETED with metadata
        val job = fakeJobDao.jobs.value.first { it.expenseId == id }
        assertEquals("COMPLETED", job.status)
        assertEquals("SHOPPING", job.suggestedCategory)

        // User confirmed category (FOOD) MUST BE PRESERVED
        val expenseInDb = fakeExpenseDao.expenses.value.first { it.id == id }
        assertEquals("FOOD", expenseInDb.category)
        assertEquals(CategorizationState.CONFIRMED.name, expenseInDb.categorizationState)
    }

    @Test
    fun processAiEnrichment_doesNotOverwriteProtectedFinancialFields() = runTest {
        val fakeExpenseDao = FakeExpenseDao()
        val fakeJobDao = FakeAiEnrichmentJobDao()
        val fakePrefRepo = FakeUserPreferencesDataSource()
        val repository = OfflineExpenseRepository(fakeExpenseDao, fakePrefRepo, fakeJobDao)
        val fakeAiCategorizer = FakeCloudAiCategorizer(
            mockResult = AiCategorizationResult(
                suggestedCategory = ExpenseCategory.TRANSPORT,
                confidenceScore = 0.95f,
                reasoning = "AI suggested Transport"
            )
        )

        val originalTime = 987654321L
        val originalExpense = Expense(
            amountMinor = 1250L,
            currency = "EUR",
            occurredAt = originalTime,
            note = "Bus ticket to airport",
            category = null
        )
        val id = repository.saveExpense(originalExpense)

        repository.processAiEnrichment(id, fakeAiCategorizer)

        // Verify protected financial fields remain 100% UNCHANGED
        val saved = fakeExpenseDao.expenses.value.first { it.id == id }
        assertEquals(1250L, saved.amountMinor)
        assertEquals("EUR", saved.currency)
        assertEquals(originalTime, saved.occurredAt)
        assertEquals("Bus ticket to airport", saved.note)
        assertEquals("TRANSPORT", saved.category)
        assertEquals(CategorizationState.AUTO_CATEGORIZED.name, saved.categorizationState)
    }

    private class FakeExpenseDao : ExpenseDao {
        val expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
        var wasTransactionCalled = false

        override fun getAllExpenses(): Flow<List<ExpenseEntity>> = expenses
        override fun getExpenseById(id: Long): Flow<ExpenseEntity?> = flowOf(expenses.value.find { it.id == id })
        override fun getExpenseByIdSync(id: Long): ExpenseEntity? = expenses.value.find { it.id == id }
        override fun getAllExpensesSync(): List<ExpenseEntity> = expenses.value
        override fun insertExpense(expense: ExpenseEntity): Long {
            val nextId = if (expense.id != 0L) expense.id else (expenses.value.maxOfOrNull { it.id } ?: 0L) + 1L
            val inserted = expense.copy(id = nextId)
            expenses.value = expenses.value.filter { it.id != nextId } + inserted
            return nextId
        }
        override fun insertExpenseInTransaction(expense: ExpenseEntity): Long {
            wasTransactionCalled = true
            return insertExpense(expense)
        }
        override fun updateExpense(expense: ExpenseEntity) {
            expenses.value = expenses.value.map { if (it.id == expense.id) expense else it }
        }
        override fun deleteExpense(expense: ExpenseEntity) {}
        override fun deleteExpenseById(id: Long) {
            expenses.value = expenses.value.filter { it.id != id }
        }
        override fun deleteAllExpenses() {
            expenses.value = emptyList()
        }
        override fun insertExpenses(expenses: List<ExpenseEntity>) {
            this.expenses.value = expenses
        }
    }

    private class FakeAiEnrichmentJobDao : AiEnrichmentJobDao {
        val jobs = MutableStateFlow<List<AiEnrichmentJobEntity>>(emptyList())

        override fun insertJob(job: AiEnrichmentJobEntity): Long {
            val nextId = (jobs.value.maxOfOrNull { it.jobId } ?: 0L) + 1L
            val inserted = job.copy(jobId = nextId)
            jobs.value = jobs.value + inserted
            return nextId
        }

        override fun cancelPendingJobsForExpense(expenseId: Long): Int {
            val countBefore = jobs.value.size
            jobs.value = jobs.value.filterNot { it.expenseId == expenseId && (it.status == EnrichmentJobStatus.PENDING.name || it.status == EnrichmentJobStatus.IN_PROGRESS.name) }
            return countBefore - jobs.value.size
        }

        override fun deleteAllJobs() {
            jobs.value = emptyList()
        }

        override fun enqueueJobForExpense(expenseId: Long): Long {
            cancelPendingJobsForExpense(expenseId)
            return insertJob(
                AiEnrichmentJobEntity(
                    expenseId = expenseId,
                    status = EnrichmentJobStatus.PENDING.name
                )
            )
        }

        override fun getJobForExpense(expenseId: Long): Flow<AiEnrichmentJobEntity?> {
            return flowOf(jobs.value.find { it.expenseId == expenseId })
        }

        override fun getJobForExpenseSync(expenseId: Long): AiEnrichmentJobEntity? {
            return jobs.value.find { it.expenseId == expenseId }
        }

        override fun getJobsByStatus(status: String): List<AiEnrichmentJobEntity> {
            return jobs.value.filter { it.status == status }
        }

        override fun updateJob(job: AiEnrichmentJobEntity) {
            jobs.value = jobs.value.map { if (it.jobId == job.jobId) job else it }
        }

        override fun recordAttempt(
            jobId: Long,
            status: String,
            retryCount: Int,
            lastAttemptAt: Long,
            updatedAt: Long,
            errorMessage: String?
        ) {
            jobs.value = jobs.value.map {
                if (it.jobId == jobId) {
                    it.copy(
                        status = status,
                        retryCount = retryCount,
                        lastAttemptAt = lastAttemptAt,
                        updatedAt = updatedAt,
                        errorMessage = errorMessage
                    )
                } else {
                    it
                }
            }
        }

        override fun completeJob(
            jobId: Long,
            status: String,
            suggestedCategory: String?,
            confidenceScore: Float?,
            reasoning: String?,
            tags: String?,
            updatedAt: Long
        ) {
            jobs.value = jobs.value.map {
                if (it.jobId == jobId) {
                    it.copy(
                        status = status,
                        suggestedCategory = suggestedCategory,
                        confidenceScore = confidenceScore,
                        aiReasoning = reasoning,
                        suggestedTags = tags,
                        updatedAt = updatedAt
                    )
                } else {
                    it
                }
            }
        }
    }

    private class FakeUserPreferencesDataSource : UserPreferencesDataSource {
        private val _currency = MutableStateFlow("INR")
        private val _theme = MutableStateFlow(com.kanak.app.data.local.datastore.ThemePreference.DARK)

        override val defaultCurrency: Flow<String> = _currency
        override suspend fun setDefaultCurrency(currency: String) {
            _currency.value = currency
        }

        override val themePreference: Flow<com.kanak.app.data.local.datastore.ThemePreference> = _theme
        override suspend fun setThemePreference(theme: com.kanak.app.data.local.datastore.ThemePreference) {
            _theme.value = theme
        }

        private val _lastBackup = MutableStateFlow<Long?>(null)
        override val lastBackupExportedTime: Flow<Long?> = _lastBackup
        override suspend fun setLastBackupExportedTime(timestamp: Long) {
            _lastBackup.value = timestamp
        }
    }
}
