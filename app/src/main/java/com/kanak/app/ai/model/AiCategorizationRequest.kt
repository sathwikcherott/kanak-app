package com.kanak.app.ai.model

import com.kanak.app.model.ExpenseCategory

/**
 * Data-minimized payload sent to cloud AI boundaries for expense category classification.
 *
 * DATA MINIMIZATION COMPLIANCE:
 * Does NOT contain local database IDs, monetary amounts, exact timestamps, or financial metadata.
 * Contains strictly the expense description and minimal classification context.
 */
data class AiCategorizationRequest(
    val note: String?,
    val currentCategory: ExpenseCategory? = null
)
