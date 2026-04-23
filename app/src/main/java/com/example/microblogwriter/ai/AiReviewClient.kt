package com.example.microblogwriter.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed class AiReviewError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingConfiguration(message: String) : AiReviewError(message)
    class Network(cause: Throwable) : AiReviewError("Network error contacting AI provider", cause)
    class Authentication(message: String) : AiReviewError(message)
    class RateLimited(message: String) : AiReviewError(message)
    class Provider(message: String) : AiReviewError(message)
}

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(val model: String, val messages: List<ChatMessage>)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList(), val error: ProviderError? = null)

@Serializable
private data class ChatChoice(val message: ChatMessage? = null)

@Serializable
private data class ProviderError(val message: String? = null)

class AiReviewClient(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun review(
        providerBaseUrl: String,
        apiKey: String,
        model: String,
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (providerBaseUrl.isBlank()) {
            return@withContext Result.failure(AiReviewError.MissingConfiguration("Missing AI provider URL"))
        }
        if (apiKey.isBlank()) {
            return@withContext Result.failure(AiReviewError.MissingConfiguration("Missing AI API key"))
        }
        if (model.isBlank()) {
            return@withContext Result.failure(AiReviewError.MissingConfiguration("Missing AI model"))
        }

        val endpoint = buildEndpoint(providerBaseUrl)
        val body = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(
                model = model,
                messages = listOf(ChatMessage(role = "user", content = prompt))
            )
        )

        try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                doOutput = true
            }

            connection.outputStream.use { out ->
                out.write(body.toByteArray())
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code == 401 || code == 403) {
                return@withContext Result.failure(AiReviewError.Authentication("Authentication failed ($code). Check API key and provider URL."))
            }
            if (code == 429) {
                return@withContext Result.failure(AiReviewError.RateLimited("Rate limit reached (429). Please retry in a moment."))
            }
            if (code !in 200..299) {
                val providerMessage = runCatching {
                    json.decodeFromString<ChatResponse>(raw).error?.message
                }.getOrNull().orEmpty()
                val details = providerMessage.ifBlank { "HTTP $code from provider" }
                return@withContext Result.failure(AiReviewError.Provider(details))
            }

            val response = runCatching { json.decodeFromString<ChatResponse>(raw) }
                .getOrElse {
                    return@withContext Result.failure(AiReviewError.Provider("Received an unreadable response from provider"))
                }
            val output = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            if (output.isBlank()) {
                return@withContext Result.failure(AiReviewError.Provider("Provider returned no review text"))
            }

            Result.success(output)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            Result.failure(AiReviewError.Network(io))
        } catch (ex: Exception) {
            Result.failure(AiReviewError.Provider(ex.message ?: "Unknown AI provider error"))
        }
    }

    private fun buildEndpoint(providerBaseUrl: String): String {
        val trimmed = providerBaseUrl.trim().trimEnd('/')
        val withoutVersionSuffix = if (trimmed.endsWith("/v1")) trimmed.removeSuffix("/v1") else trimmed
        return withoutVersionSuffix + "/v1/chat/completions"
    }
}
