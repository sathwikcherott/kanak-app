package com.kanak.app.ai.model

import com.kanak.app.model.ExpenseCategory

/**
 * Result returned by AI categorizers containing suggested category and metadata enrichment.
 */
data class AiCategorizationResult(
    val suggestedCategory: ExpenseCategory?,
    val confidenceScore: Float,
    val reasoning: String?,
    val suggestedTags: List<String> = emptyList()
)
