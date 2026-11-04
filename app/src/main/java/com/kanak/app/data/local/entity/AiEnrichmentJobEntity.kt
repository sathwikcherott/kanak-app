package com.kanak.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EnrichmentJobStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * Persistent job record for AI categorization / metadata enrichment designed for WorkManager pickup.
 *
 * CRITICAL ISOLATION GUARANTEE:
 * This table holds ONLY AI enrichment metadata and job state.
 * Processing or updating records in this table NEVER modifies core immutable expense fields
 * (amount_minor, currency, occurred_at, note, or user-selected category) on ExpenseEntity.
 */
@Entity(
    tableName = "ai_enrichment_jobs",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expense_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["expense_id"])]
)
data class AiEnrichmentJobEntity(
    @PrimaryKey(autoGenerate = true)
    val jobId: Long = 0L,
    @ColumnInfo(name = "expense_id")
    val expenseId: Long,
    @ColumnInfo(name = "status")
    val status: String = EnrichmentJobStatus.PENDING.name,
    @ColumnInfo(name = "suggested_category")
    val suggestedCategory: String? = null,
    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float? = null,
    @ColumnInfo(name = "ai_reasoning")
    val aiReasoning: String? = null,
    @ColumnInfo(name = "suggested_tags")
    val suggestedTags: String? = null,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)
