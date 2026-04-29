package io.github.zeroone3010.yablogwriter.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val MAX_ERROR_BODY_LENGTH = 4_000

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
                return@withContext Result.failure(
                    AiReviewError.Authentication(
                        formatErrorDetails(
                            summary = "Authentication failed. Check API key and provider URL.",
                            code = code,
                            raw = raw,
                            providerMessage = extractProviderMessage(raw),
                            headers = connection.headerFields
                        )
                    )
                )
            }
            if (code == 429) {
                return@withContext Result.failure(
                    AiReviewError.RateLimited(
                        formatErrorDetails(
                            summary = "Rate limit reached. Please retry in a moment.",
                            code = code,
                            raw = raw,
                            providerMessage = extractProviderMessage(raw),
                            headers = connection.headerFields
                        )
                    )
                )
            }
            if (code !in 200..299) {
                return@withContext Result.failure(
                    AiReviewError.Provider(
                        formatErrorDetails(
                            summary = "Provider returned an error.",
                            code = code,
                            raw = raw,
                            providerMessage = extractProviderMessage(raw),
                            headers = connection.headerFields
                        )
                    )
                )
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



    private fun extractProviderMessage(raw: String): String = runCatching {
        json.decodeFromString<ChatResponse>(raw).error?.message
    }.getOrNull().orEmpty()

    private fun formatErrorDetails(
        summary: String,
        code: Int,
        raw: String,
        providerMessage: String,
        headers: Map<String?, List<String>?>
    ): String {
        val headerDetails = headers
            .filterKeys { !it.isNullOrBlank() }
            .entries
            .sortedBy { it.key }
            .joinToString(separator = "\n") { (key, values) ->
                "$key: ${values.orEmpty().joinToString()}"
            }
            .ifBlank { "(none)" }

        val payload = raw
            .trim()
            .ifBlank { "(empty)" }
            .take(MAX_ERROR_BODY_LENGTH)

        val providerLine = providerMessage.ifBlank { "(none)" }

        return buildString {
            appendLine(summary)
            appendLine("HTTP status: $code")
            appendLine("Provider message: $providerLine")
            appendLine("Response headers:")
            appendLine(headerDetails)
            appendLine("Raw response body:")
            append(payload)
        }.trim()
    }
    private fun buildEndpoint(providerBaseUrl: String): String {
        val trimmed = providerBaseUrl.trim().trimEnd('/')
        val withoutVersionSuffix = if (trimmed.endsWith("/v1")) trimmed.removeSuffix("/v1") else trimmed
        return withoutVersionSuffix + "/v1/chat/completions"
    }
}
