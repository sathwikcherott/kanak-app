package com.kanak.app.ai

import com.kanak.app.ai.model.AiCategorizationRequest
import com.kanak.app.ai.model.AiCategorizationResult

/**
 * Boundary interface for cloud-based expense categorization services.
 * Implementations will be injected asynchronously without blocking local expense persistence.
 */
interface CloudAiCategorizer {
    suspend fun categorizeExpense(request: AiCategorizationRequest): Result<AiCategorizationResult>
}
