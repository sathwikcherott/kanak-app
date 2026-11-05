package com.kanak.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.local.entity.EnrichmentJobStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AiEnrichmentJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertJob(job: AiEnrichmentJobEntity): Long

    @Query("SELECT * FROM ai_enrichment_jobs WHERE expense_id = :expenseId")
    fun getJobForExpense(expenseId: Long): Flow<AiEnrichmentJobEntity?>

    @Query("SELECT * FROM ai_enrichment_jobs WHERE expense_id = :expenseId")
    fun getJobForExpenseSync(expenseId: Long): AiEnrichmentJobEntity?

    @Query("SELECT * FROM ai_enrichment_jobs WHERE status = :status ORDER BY created_at ASC")
    fun getJobsByStatus(status: String): List<AiEnrichmentJobEntity>

    @Query("DELETE FROM ai_enrichment_jobs WHERE expense_id = :expenseId AND status IN ('PENDING', 'IN_PROGRESS')")
    fun cancelPendingJobsForExpense(expenseId: Long): Int

    @Query("DELETE FROM ai_enrichment_jobs")
    fun deleteAllJobs()

    @Transaction
    fun enqueueJobForExpense(expenseId: Long): Long {
        cancelPendingJobsForExpense(expenseId)
        return insertJob(
            AiEnrichmentJobEntity(
                expenseId = expenseId,
                status = EnrichmentJobStatus.PENDING.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    @Update
    fun updateJob(job: AiEnrichmentJobEntity)

    @Query("UPDATE ai_enrichment_jobs SET status = :status, retry_count = :retryCount, last_attempt_at = :lastAttemptAt, updated_at = :updatedAt, error_message = :errorMessage WHERE jobId = :jobId")
    fun recordAttempt(
        jobId: Long,
        status: String,
        retryCount: Int,
        lastAttemptAt: Long,
        updatedAt: Long,
        errorMessage: String? = null
    )

    @Query("UPDATE ai_enrichment_jobs SET status = :status, suggested_category = :suggestedCategory, confidence_score = :confidenceScore, ai_reasoning = :reasoning, suggested_tags = :tags, updated_at = :updatedAt WHERE jobId = :jobId")
    fun completeJob(
        jobId: Long,
        status: String,
        suggestedCategory: String?,
        confidenceScore: Float?,
        reasoning: String?,
        tags: String?,
        updatedAt: Long
    )
}
