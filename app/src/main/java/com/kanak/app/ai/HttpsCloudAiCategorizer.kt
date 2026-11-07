package com.kanak.app.ai

import com.kanak.app.ai.model.AiCategorizationRequest
import com.kanak.app.ai.model.AiCategorizationResult
import com.kanak.app.model.ExpenseCategory
import java.io.IOException
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class HttpsCloudAiCategorizer(
    private val connectionStore: AiConnectionStore
) : CloudAiCategorizer {
    override suspend fun categorizeExpense(request: AiCategorizationRequest): Result<AiCategorizationResult> =
        withContext(Dispatchers.IO) {
            try {
                val config = connectionStore.current() ?: throw IOException("AI connection is missing")
                val description = AiDescription.forSuggestion(request.note.orEmpty())
                require(description.any(Char::isLetter))
                val connection = config.endpoint.toURL().openConnection() as HttpsURLConnection
                val category = try {
                    connection.requestMethod = "POST"
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 10_000
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Bearer ${config.accessToken}")
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val payload = JSONObject().put("description", description).toString().toByteArray(Charsets.UTF_8)
                    connection.setFixedLengthStreamingMode(payload.size)
                    connection.outputStream.use { it.write(payload) }
                    if (connection.responseCode != 200) throw IOException("AI endpoint rejected request")
                    val response = connection.inputStream.use { it.readNBytes(4097) }
                    if (response.size > 4096) throw IOException("AI response is too large")
                    val result = JSONObject(response.toString(Charsets.UTF_8))
                    if (!result.has("category")) throw IOException("AI response is incomplete")
                    if (result.isNull("category")) null else ExpenseCategory.valueOf(result.getString("category"))
                } finally {
                    connection.disconnect()
                }
                if (connectionStore.current() !== config) throw IOException("AI connection changed")
                Result.success(AiCategorizationResult(category, 0f, null))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Result.failure(IOException("Could not get an AI suggestion"))
            }
        }
}
