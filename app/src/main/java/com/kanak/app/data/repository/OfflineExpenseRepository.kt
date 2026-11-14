package com.kanak.app.data.repository

import com.kanak.app.ai.CloudAiCategorizer
import com.kanak.app.ai.model.AiCategorizationRequest
import com.kanak.app.data.local.dao.AiEnrichmentJobDao
import com.kanak.app.data.local.dao.ExpenseDao
import com.kanak.app.data.local.datastore.ThemePreference
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

import com.kanak.app.data.backup.BackupExpense
import com.kanak.app.data.backup.BackupPersistence
import com.kanak.app.data.backup.BackupPreferences
import com.kanak.app.data.backup.KanakBackup
import com.kanak.app.data.backup.KanakBackupSerializer
import com.kanak.app.data.backup.RestoreOutcome
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import com.kanak.app.data.local.entity.CustomCategoryEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.kanak.app.data.local.KanakDatabase
import com.kanak.app.data.local.entity.InboxTransactionEntity
import com.kanak.app.data.local.entity.MerchantKnowledgeEntity
import com.kanak.app.data.backup.BackupInboxTransaction
import com.kanak.app.data.backup.BackupKnowledgeEntry

class OfflineExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val aiEnrichmentJobDao: AiEnrichmentJobDao? = null,
    private val database: KanakDatabase? = null
) : ExpenseRepository, BackupPersistence {
    private val writes = Mutex()
    private var restoreGeneration = 0L

    override fun getCategories(): Flow<List<ExpenseCategory>> = database?.customCategoryDao()?.observeAll()?.map { rows ->
        ExpenseCategory.entries + rows.map { it.toDomainModel() }
    } ?: flowOf(ExpenseCategory.entries)

    override suspend fun createCategory(name: String): ExpenseCategory = withContext(Dispatchers.IO) {
        val cleaned = ExpenseCategory.cleanName(name)
        require(!ExpenseCategory.isBuiltInName(cleaned)) { "This category already exists" }
        val dao = checkNotNull(database) { "Database required to create categories" }.customCategoryDao()
        writes.withLock {
            val existing = dao.getAll()
            require(existing.size < ExpenseCategory.MAX_CUSTOM_CATEGORIES) { "You can create up to ${ExpenseCategory.MAX_CUSTOM_CATEGORIES} custom categories" }
            require(existing.none { it.normalizedName == ExpenseCategory.normalizedName(cleaned) }) { "This category already exists" }
            val category = ExpenseCategory.custom("custom:${java.util.UUID.randomUUID()}", cleaned)
            dao.insert(CustomCategoryEntity.from(category))
            category
        }
    }


    override suspend fun snapshotForBackup(): KanakBackup = withContext(Dispatchers.IO) {
        writes.withLock {
            KanakBackup(
                preferences = BackupPreferences(
                    userPreferencesDataSource.defaultCurrency.first(),
                    userPreferencesDataSource.themePreference.first()
                ),
                customCategories = getCategories().first().filter { it.isCustom },
                expenses = getAllExpenses().first().map(BackupExpense::fromDomainModel),
                inboxTransactions = database?.smartInboxDao()?.getAll()?.map { row ->
                    BackupInboxTransaction(row.id, row.notificationKey, row.fingerprint, row.amountMinor, row.currency, row.merchant, row.occurredAt, row.suggestedCategory, row.suggestionReason, row.status, row.createdAt)
                } ?: emptyList(),
                knowledge = database?.merchantKnowledgeDao()?.getAll()?.map { row ->
                    BackupKnowledgeEntry(row.id, row.alias, row.normalizedAlias, row.merchantLabel, row.category, row.createdAt, row.updatedAt)
                } ?: emptyList()
            )
        }
    }

    override suspend fun restoreValidatedBackup(backup: KanakBackup): RestoreOutcome = withContext(Dispatchers.IO) {
        // Revalidate even for callers bypassing the file picker; use a private immutable copy.
        val validated = KanakBackupSerializer.fromJson(KanakBackupSerializer.toJson(backup))
        val entities = validated.expenses.map { it.toDomainModel().toEntity() }
        val db = checkNotNull(database) { "Database required for atomic replacement" }
        writes.withLock {
            currentCoroutineContext().ensureActive()
            // Once replacement starts, finish the database + preference outcome even if the UI leaves.
            withContext(NonCancellable) {
                db.runInTransaction {
                    db.aiEnrichmentJobDao().deleteAllJobs()
                    db.expenseDao().deleteAllExpenses()
                    db.customCategoryDao().deleteAll()
                    db.smartInboxDao().deleteAll()
                    db.merchantKnowledgeDao().deleteAll()
                    db.customCategoryDao().insertAll(validated.customCategories.map(CustomCategoryEntity::from))
                    db.expenseDao().insertExpenses(entities)
                    db.smartInboxDao().insertAll(validated.inboxTransactions.map { row -> InboxTransactionEntity(row.id, row.notificationKey, row.fingerprint, row.amountMinor, row.currency, row.merchant, row.occurredAt, row.suggestedCategory, row.suggestionReason, row.status, row.createdAt) })
                    db.merchantKnowledgeDao().insertAll(validated.knowledge.map { row -> MerchantKnowledgeEntity(row.id, row.alias, row.normalizedAlias, row.merchantLabel, row.category, row.createdAt, row.updatedAt) })
                }
                restoreGeneration++
                try {
                    userPreferencesDataSource.restorePreferences(
                        validated.preferences.defaultCurrency, validated.preferences.themePreference
                    )
                    RestoreOutcome.COMPLETE
                } catch (_: Exception) {
                    RestoreOutcome.EXPENSES_RESTORED_PREFERENCES_FAILED
                }
            }
        }
    }

    override fun getAllExpenses(): Flow<List<Expense>> {
        val dao = database?.customCategoryDao()
            ?: return expenseDao.getAllExpenses().map { rows -> rows.map { it.toDomainModel() } }
        return dao.observeExpenses().map { rows ->
            val categories = rows.asSequence().filter { it.customCategoryName != null && it.expense.category != null }
                .distinctBy { it.expense.category }
                .associate { it.expense.category!! to ExpenseCategory.custom(it.expense.category!!, it.customCategoryName!!) }
            rows.map { it.expense.toDomainModel(categories) }
        }
    }

    override fun getExpenseById(id: Long): Flow<Expense?> {
        val dao = database?.customCategoryDao()
            ?: return expenseDao.getExpenseById(id).map { it?.toDomainModel() }
        return dao.observeExpense(id).map { row ->
            row?.let {
                val category = it.customCategoryName?.let { name -> ExpenseCategory.custom(it.expense.category!!, name) }
                it.expense.toDomainModel(if (category != null) mapOf(category.name to category) else emptyMap())
            }
        }
    }

    /**
     * Atomically saves an expense and enqueues its AI enrichment job record in a Room transaction.
     * Completes immediately locally without network calls or external worker dependencies.
     */
    override suspend fun saveExpense(expense: Expense): Long = withContext(Dispatchers.IO) {
        writes.withLock {
            expense.category?.takeIf { it.isCustom }?.let { category ->
                require(database?.customCategoryDao()?.getById(category.name) != null) { "This category is no longer available. Select another category." }
            }
            val save = {
                val id = expenseDao.insertExpenseInTransaction(expense.toEntity())
                aiEnrichmentJobDao?.enqueueJobForExpense(id)
                id
            }
            database?.runInTransaction(java.util.concurrent.Callable { save() }) ?: save()
        }
    }

    override suspend fun deleteExpense(id: Long) = withContext(Dispatchers.IO) {
        writes.withLock { expenseDao.deleteExpenseById(id) }
    }

    override fun getDefaultCurrency(): Flow<String> {
        return userPreferencesDataSource.defaultCurrency
    }

    override suspend fun setDefaultCurrency(currency: String) = withContext(Dispatchers.IO) {
        writes.withLock { userPreferencesDataSource.setDefaultCurrency(currency) }
    }

    override fun getThemePreference(): Flow<ThemePreference> {
        return userPreferencesDataSource.themePreference
    }

    override suspend fun setThemePreference(theme: ThemePreference) = withContext(Dispatchers.IO) {
        writes.withLock { userPreferencesDataSource.setThemePreference(theme) }
    }

    override fun getEnrichmentJobForExpense(expenseId: Long): Flow<AiEnrichmentJobEntity?> {
        return aiEnrichmentJobDao?.getJobForExpense(expenseId) ?: flowOf(null)
    }

    override suspend fun replaceAllExpensesInTransaction(expenses: List<Expense>) = withContext(Dispatchers.IO) {
        val entities = expenses.map { it.toEntity() }
        val db = checkNotNull(database) { "Database required for atomic replacement" }
        writes.withLock {
            db.runInTransaction {
                db.aiEnrichmentJobDao().deleteAllJobs()
                expenseDao.deleteAllExpenses()
                expenseDao.insertExpenses(entities)
            }
            restoreGeneration++
            Unit
        }
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
        val (job, generation, initialExpense) = writes.withLock {
            val job = jobDao.getJobForExpenseSync(expenseId)
                ?: return@withContext Result.failure(IllegalArgumentException("No enrichment job found for expense"))
            val now = System.currentTimeMillis()
            jobDao.recordAttempt(job.jobId, EnrichmentJobStatus.IN_PROGRESS.name, job.retryCount + 1, now, now)
            Triple(job, restoreGeneration, expenseDao.getExpenseByIdSync(expenseId))
        }
        val currentExpense = initialExpense

        // Data minimization: Request payload includes ONLY description (note) & currentCategory enum
        val currentCategoryEnum = currentExpense?.category?.let { catName ->
            try { ExpenseCategory.valueOf(catName) } catch (_: Exception) { null }
        }

        val minRequest = AiCategorizationRequest(
            note = currentExpense?.note,
            currentCategory = currentCategoryEnum
        )

        val aiResult = aiCategorizer.categorizeExpense(minRequest)

        writes.withLock {
            if (generation != restoreGeneration || jobDao.getJobForExpenseSync(expenseId)?.jobId != job.jobId) {
                return@withContext Result.success(Unit) // Result belongs to data that has been replaced.
            }
            val latestExpense = expenseDao.getExpenseByIdSync(expenseId)
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
                if (latestExpense != null) {
                    val isUserConfirmed = latestExpense.categorizationState == CategorizationState.CONFIRMED.name
                    val hasUserCategory = latestExpense.category != null

                    if ((!isUserConfirmed) && (!hasUserCategory) && (result.suggestedCategory != null)) {
                        val updatedExpense = latestExpense.copy(
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
}
