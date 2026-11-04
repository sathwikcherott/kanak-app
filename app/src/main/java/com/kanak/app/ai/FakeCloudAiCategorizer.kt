package com.kanak.app.ai

import com.kanak.app.ai.model.AiCategorizationRequest
import com.kanak.app.ai.model.AiCategorizationResult
import com.kanak.app.model.ExpenseCategory

/**
 * Fake Cloud AI Categorizer implementation for test verification and offline simulation.
 */
class FakeCloudAiCategorizer(
    var shouldSucceed: Boolean = true,
    var mockResult: AiCategorizationResult = AiCategorizationResult(
        suggestedCategory = ExpenseCategory.FOOD,
        confidenceScore = 0.95f,
        reasoning = "Inferred from transaction notes",
        suggestedTags = listOf("food", "dining")
    )
) : CloudAiCategorizer {

    var lastRequest: AiCategorizationRequest? = null
        private set

    override suspend fun categorizeExpense(request: AiCategorizationRequest): Result<AiCategorizationResult> {
        lastRequest = request
        return if (shouldSucceed) {
            Result.success(mockResult)
        } else {
            Result.failure(IllegalStateException("Simulated cloud AI failure"))
        }
    }
}
