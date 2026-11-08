package com.kanak.app.ai

import com.kanak.app.ai.model.AiCategorizationRequest
import com.kanak.app.ai.model.AiCategorizationResult
import com.kanak.app.model.ExpenseCategory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

internal class AiConnectionFailure(val userMessage: String) : IOException(userMessage)

class HttpsCloudAiCategorizer(
    private val connectionStore: AiConnectionStore
) : CloudAiCategorizer {
    override suspend fun categorizeExpense(request: AiCategorizationRequest): Result<AiCategorizationResult> =
        withContext(Dispatchers.IO) {
            try {
                val config = connectionStore.current() ?: throw AiConnectionFailure("Set up AI Categorization in Settings.")
                val description = AiDescription.forSuggestion(request.note.orEmpty())
                if (description.none(Char::isLetter)) throw AiConnectionFailure("Add a description before asking AI.")
                val connection = config.endpoint.toURL().openConnection() as HttpsURLConnection
                val category = try {
                    connection.requestMethod = "POST"
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 10_000
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Bearer ${config.accessToken}")
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("X-Kanak-Category-Version", "2")
                    val payload = JSONObject().put("description", description).toString().toByteArray(Charsets.UTF_8)
                    connection.setFixedLengthStreamingMode(payload.size)
                    connection.outputStream.use { it.write(payload) }
                    val status = connection.responseCode
                    if (status != 200) {
                        val providerStatus = if (status == 502) readProviderStatus(connection) else null
                        throw AiConnectionFailure(httpFailureMessage(status, providerStatus))
                    }
                    val response = connection.inputStream.use { it.readNBytes(4097) }
                    if (response.size > 4096) throw AiConnectionFailure("The AI backend returned an oversized response.")
                    val result = JSONObject(response.toString(Charsets.UTF_8))
                    if (!result.has("category")) throw AiConnectionFailure("The AI backend returned an incomplete response.")
                    if (result.isNull("category")) null else ExpenseCategory.valueOf(result.getString("category"))
                } finally {
                    connection.disconnect()
                }
                if (connectionStore.current() !== config) throw AiConnectionFailure("The AI connection changed. Try again.")
                Result.success(AiCategorizationResult(category, 0f, null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiConnectionFailure) {
                Result.failure(e)
            } catch (_: SocketTimeoutException) {
                Result.failure(AiConnectionFailure("The AI connection timed out. Try again."))
            } catch (_: UnknownHostException) {
                Result.failure(AiConnectionFailure("Could not find the AI backend. Check your internet connection and endpoint."))
            } catch (_: SSLException) {
                Result.failure(AiConnectionFailure("Could not establish a secure connection to the AI backend."))
            } catch (_: JSONException) {
                Result.failure(AiConnectionFailure("The AI backend returned an invalid response."))
            } catch (_: IllegalArgumentException) {
                Result.failure(AiConnectionFailure("The AI backend returned an invalid category."))
            } catch (_: IOException) {
                Result.failure(AiConnectionFailure("Could not connect to the AI backend. Check your internet connection."))
            } catch (_: Exception) {
                Result.failure(AiConnectionFailure("The AI request failed on this device."))
            }
        }

    private fun readProviderStatus(connection: HttpsURLConnection): Int? = try {
        val response = connection.errorStream?.use { it.readNBytes(4097) }
        if (response == null || response.size > 4096) null else {
            JSONObject(response.toString(Charsets.UTF_8)).optInt("providerStatus").takeIf { it in 400..599 }
        }
    } catch (_: IOException) {
        null
    } catch (_: JSONException) {
        null
    }

    private fun httpFailureMessage(status: Int, providerStatus: Int?): String = when {
        status == 401 -> "The AI backend rejected the access token (HTTP 401). Edit the connection in Settings."
        status == 403 -> "The AI backend denied access (HTTP 403). Check the connection in Settings."
        status == 429 -> "The AI backend rate limit was reached (HTTP 429). Try again shortly."
        status == 504 -> "The AI backend timed out waiting for Gemini (HTTP 504). Try again."
        status == 502 && providerStatus == 503 -> "Gemini is temporarily unavailable (Gemini 503). Try again shortly."
        status == 502 && providerStatus != null -> "Gemini rejected the request (Gemini HTTP $providerStatus)."
        else -> "The AI backend could not complete the request (HTTP $status)."
    }
}
