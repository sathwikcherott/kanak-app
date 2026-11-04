package com.kanak.app.data.repository

import com.kanak.app.ai.CloudAiCategorizer
import com.kanak.app.ai.model.AiCategorizationRequest
import com.kanak.app.data.local.dao.AiEnrichmentJobDao
import com.kanak.app.data.local.dao.ExpenseDao
import com.kanak.app.data.local.datastore.UserPreferencesDataSource
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.local.entity.EnrichmentJobStatus
import com.kanak.app.data.local.entity.toDomainModel
import com.kanak.app.data.local.entity.toEntity
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OfflineExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val aiEnrichmentJobDao: AiEnrichmentJobDao? = null
) : ExpenseRepository {

    override fun getAllExpenses(): Flow<List<Expense>> {
        return expenseDao.getAllExpenses().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getExpenseById(id: Long): Flow<Expense?> {
        return expenseDao.getExpenseById(id).map { entity ->
            entity?.toDomainModel()
        }
    }

    /**
     * Atomically saves an expense and enqueues its AI enrichment job record in a Room transaction.
     * Completes immediately locally without network calls or external worker dependencies.
     */
    override suspend fun saveExpense(expense: Expense): Long = withContext(Dispatchers.IO) {
        val expenseId = expenseDao.insertExpenseInTransaction(expense.toEntity())
        aiEnrichmentJobDao?.enqueueJobForExpense(expenseId)
        expenseId
    }

    override suspend fun deleteExpense(id: Long) = withContext(Dispatchers.IO) {
        expenseDao.deleteExpenseById(id)
    }

    override fun getDefaultCurrency(): Flow<String> {
        return userPreferencesDataSource.defaultCurrency
    }

    override suspend fun setDefaultCurrency(currency: String) = withContext(Dispatchers.IO) {
        userPreferencesDataSource.setDefaultCurrency(currency)
    }

    override fun getEnrichmentJobForExpense(expenseId: Long): Flow<AiEnrichmentJobEntity?> {
        return aiEnrichmentJobDao?.getJobForExpense(expenseId) ?: flowOf(null)
    }

    /**
     * Asynchronous AI enrichment processing suitable for future WorkManager background execution.
     *
     * GUARANTEES:
     * 1. DATA MINIMIZATION: Pass only description (`note`) and minimal context to CloudAiCategorizer.
     * 2. DATA INTEGRITY: Protected financial fields (amount_minor, currency, occurred_at, note) are NEVER modified.
     * 3. MANUAL OVERRIDE: User-confirmed categories always take precedence over AI suggestions.
     * 4. RETRY RELIABILITY: Updates retryCount and lastAttemptAt deterministically on failures.
     */
    suspend fun processAiEnrichment(
        expenseId: Long,
        aiCategorizer: CloudAiCategorizer,
        maxRetries: Int = 3
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val jobDao = aiEnrichmentJobDao ?: return@withContext Result.failure(IllegalStateException("No job DAO configured"))
        val job = jobDao.getJobForExpenseSync(expenseId) ?: return@withContext Result.failure(IllegalArgumentException("No enrichment job found for expense"))

        val now = System.currentTimeMillis()
        jobDao.recordAttempt(
            jobId = job.jobId,
            status = EnrichmentJobStatus.IN_PROGRESS.name,
            retryCount = job.retryCount + 1,
            lastAttemptAt = now,
            updatedAt = now
        )

        val currentExpense = expenseDao.getExpenseByIdSync(expenseId)

        // Data minimization: Request payload includes ONLY description (note) & currentCategory enum
        val currentCategoryEnum = currentExpense?.category?.let { catName ->
            try { ExpenseCategory.valueOf(catName) } catch (_: Exception) { null }
        }

        val minRequest = AiCategorizationRequest(
            note = currentExpense?.note,
            currentCategory = currentCategoryEnum
        )

        val aiResult = aiCategorizer.categorizeExpense(minRequest)

        if (aiResult.isSuccess) {
            val result = aiResult.getOrThrow()
            val completedTime = System.currentTimeMillis()

            jobDao.completeJob(
                jobId = job.jobId,
                status = EnrichmentJobStatus.COMPLETED.name,
                suggestedCategory = result.suggestedCategory?.name,
                confidenceScore = result.confidenceScore,
                reasoning = result.reasoning,
                tags = result.suggestedTags.joinToString(","),
                updatedAt = completedTime
            )

            // Category update ONLY if user has NOT manually set/confirmed category
            if (currentExpense != null) {
                val isUserConfirmed = currentExpense.categorizationState == CategorizationState.CONFIRMED.name
                val hasUserCategory = currentExpense.category != null

                if ((!isUserConfirmed) && (!hasUserCategory) && (result.suggestedCategory != null)) {
                    val updatedExpense = currentExpense.copy(
                        category = result.suggestedCategory.name,
                        categorizationState = CategorizationState.AUTO_CATEGORIZED.name
                        // Protected fields amountMinor, currency, occurredAt, note are 100% untouched
                    )
                    expenseDao.updateExpense(updatedExpense)
                }
            }

            Result.success(Unit)
        } else {
            val error = aiResult.exceptionOrNull()
            val failedTime = System.currentTimeMillis()
            val newRetryCount = job.retryCount + 1
            val nextStatus = if (newRetryCount >= maxRetries) EnrichmentJobStatus.FAILED.name else EnrichmentJobStatus.PENDING.name

            jobDao.recordAttempt(
                jobId = job.jobId,
                status = nextStatus,
                retryCount = newRetryCount,
                lastAttemptAt = failedTime,
                updatedAt = failedTime,
                errorMessage = error?.localizedMessage ?: "AI enrichment failed"
            )

            Result.failure(error ?: Exception("AI enrichment failed"))
        }
    }
}
