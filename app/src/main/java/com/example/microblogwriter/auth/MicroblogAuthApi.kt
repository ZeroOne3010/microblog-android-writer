package com.example.microblogwriter.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

class MicroblogAuthApi {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun discoverEndpoints(me: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = if (me.startsWith("http://") || me.startsWith("https://")) me else "https://$me"
            val connection = (URL(normalized).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 10000
                readTimeout = 10000
            }
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val authorization = Regex("<link[^>]+rel=[\"']authorization_endpoint[\"'][^>]+href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?: throw IllegalStateException("Could not discover authorization_endpoint")
            val token = Regex("<link[^>]+rel=[\"']token_endpoint[\"'][^>]+href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?: throw IllegalStateException("Could not discover token_endpoint")
            toAbsolute(normalized, authorization) to toAbsolute(normalized, token)
        }
    }

    fun buildAuthorizationUrl(config: AuthConfig, authorizationEndpoint: String): String {
        val query = listOf(
            "client_id" to config.clientId,
            "redirect_uri" to config.redirectUri,
            "response_type" to "code",
            "scope" to config.scope,
            "state" to config.state,
            "me" to config.me
        ).joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8") }=${URLEncoder.encode(v, "UTF-8")}" }
        return "$authorizationEndpoint?$query"
    }

    suspend fun exchangeCodeForToken(
        tokenEndpoint: String,
        code: String,
        config: AuthConfig
    ): Result<AuthState> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = listOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to config.clientId,
                "redirect_uri" to config.redirectUri
            ).joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8") }=${URLEncoder.encode(v, "UTF-8")}" }

            val connection = (URL(tokenEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(payload.toByteArray()) }
            val codeResponse = connection.responseCode
            val response = (if (codeResponse in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (codeResponse !in 200..299) {
                throw IllegalStateException("Token exchange failed: HTTP $codeResponse $response")
            }
            val obj = json.parseToJsonElement(response).jsonObject
            val accessToken = obj["access_token"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("No access_token in token response")
            AuthState(
                isAuthenticated = true,
                accessToken = accessToken,
                tokenType = obj["token_type"]?.jsonPrimitive?.content ?: "Bearer",
                scope = obj["scope"]?.jsonPrimitive?.content ?: config.scope,
                me = obj["me"]?.jsonPrimitive?.content ?: config.me
            )
        }
    }

    private fun toAbsolute(base: String, value: String): String {
        val uri = URI(value)
        return if (uri.isAbsolute) value else URI(base).resolve(uri).toString()
    }
}
