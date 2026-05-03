package io.github.zeroone3010.yablogwriter.network

import android.content.Context
import android.net.Uri
import io.github.zeroone3010.yablogwriter.domain.Draft
import io.github.zeroone3010.yablogwriter.domain.SettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MicroblogApi(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun publishPost(draft: Draft, settings: SettingsState, accessToken: String): Result<PublishResponse> = withContext(Dispatchers.IO) {
        runCatching {
            require(accessToken.isNotBlank()) { "Micro.blog access token is required" }

            val endpoint = "${settings.microblogApiBaseUrl.trimEnd('/')}/micropub"
            val form = if (draft.postId.isNullOrBlank()) {
                buildList {
                    add("h" to "entry")
                    add("name" to draft.title.ifBlank { "Untitled" })
                    add("content" to draft.body)
                    draft.categories.forEach { add("category[]" to it) }
                }
            } else {
                buildList {
                    add("action" to "update")
                    add("url" to draft.postId.orEmpty())
                    add("replace[name]" to draft.title.ifBlank { "Untitled" })
                    add("replace[content]" to draft.body)
                    draft.categories.forEach { add("replace[category][]" to it) }
                }
            }

            val response = postFormUrlEncoded(endpoint, accessToken, form)
            val parsed = extractPublishResponse(response.body)
            val permalink = response.location ?: parsed.permalink
            val postId = parsed.postId ?: draft.postId
            if (postId.isNullOrBlank() && permalink.isNullOrBlank()) {
                throw IllegalStateException("Publish succeeded but post URL/ID was not returned")
            }
            PublishResponse(
                postId = postId,
                permalink = permalink
            )
        }
    }

    suspend fun uploadImage(
        localUri: String,
        alt: String,
        settings: SettingsState,
        accessToken: String,
        onProgress: (Int) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(accessToken.isNotBlank()) { "Micro.blog access token is required" }
            require(localUri.isNotBlank()) { "Image URI is required" }

            val mediaEndpoint = resolveMediaEndpoint(settings, accessToken)
            val uri = Uri.parse(localUri)
            val (fileName, mimeType, bytes) = readImageBytes(uri)

            val response = postMultipart(
                endpoint = mediaEndpoint,
                token = accessToken,
                fileName = fileName,
                mimeType = mimeType,
                bytes = bytes,
                alt = alt,
                onProgress = onProgress
            )

            response.location
                ?: extractMediaUrl(response.body)
                ?: throw IllegalStateException("Upload succeeded but file URL was not returned")
        }
    }

    suspend fun fetchRecentPosts(settings: SettingsState, accessToken: String): Result<List<Draft>> = withContext(Dispatchers.IO) {
        runCatching {
            require(accessToken.isNotBlank()) { "Micro.blog access token is required" }
            val baseUrl = settings.microblogApiBaseUrl.trimEnd('/')
            val endpointAttempts = listOf(
                "$baseUrl/posts/all" to { endpoint: String -> parsePostsApiResponse(getRequest(endpoint, accessToken).body) },
                "$baseUrl/posts" to { endpoint: String -> parsePostsApiResponse(getRequest(endpoint, accessToken).body) },
                "$baseUrl/micropub?q=source&limit=20" to { endpoint: String -> parseMicropubSourceResponse(getRequest(endpoint, accessToken).body) }
            )
            val errors = mutableListOf<String>()

            for ((endpoint, fetcher) in endpointAttempts) {
                val result = runCatching { fetcher(endpoint) }
                result.onSuccess { return@runCatching it }
                result.onFailure { err -> errors += "$endpoint -> ${err.message ?: "Unknown error"}" }
            }

            throw IllegalStateException("Unable to fetch published posts. Attempts: ${errors.joinToString(" | ")}")
        }
    }

    private fun parsePostsApiResponse(body: String): List<Draft> {
        val root = json.parseToJsonElement(body).jsonObject
        val items = root["items"]?.jsonArray ?: emptyList()
        return items.mapNotNull { item ->
            val obj = item.jsonObject
            val content = obj["content_text"]?.jsonPrimitive?.contentOrNull
                ?: obj["content_html"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            Draft(
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "",
                body = content,
                categories = obj["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                postId = obj["url"]?.jsonPrimitive?.contentOrNull ?: obj["id"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    private fun parseMicropubSourceResponse(body: String): List<Draft> {
        val root = json.parseToJsonElement(body).jsonObject
        val items = root["items"]?.jsonArray ?: emptyList()
        return items.mapNotNull { item ->
            val obj = item.jsonObject
            val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Draft(
                title = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                body = content,
                categories = obj["category"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                postId = obj["url"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    suspend fun fetchCategories(settings: SettingsState, accessToken: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            require(accessToken.isNotBlank()) { "Micro.blog access token is required" }
            val endpoint = "${settings.microblogApiBaseUrl.trimEnd('/')}/micropub?q=category"
            val root = json.parseToJsonElement(getRequest(endpoint, accessToken).body).jsonObject
            parseCategoryArray(root["categories"] ?: root["category"]).distinct().sorted()
        }
    }

    private fun resolveMediaEndpoint(settings: SettingsState, accessToken: String): String {
        if (settings.microblogMediaEndpoint.isNotBlank()) {
            return settings.microblogMediaEndpoint
        }
        val configEndpoint = "${settings.microblogApiBaseUrl.trimEnd('/')}/micropub?q=config"
        val body = getRequest(configEndpoint, accessToken).body
        val mediaEndpoint = json.parseToJsonElement(body)
            .jsonObject["media-endpoint"]
            ?.jsonPrimitive
            ?.contentOrNull
        return mediaEndpoint ?: "${settings.microblogApiBaseUrl.trimEnd('/')}/micropub/media"
    }

    private fun readImageBytes(uri: Uri): Triple<String, String, ByteArray> {
        return when (uri.scheme) {
            "content" -> {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "upload.bin"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("Unable to open content URI: $uri")
                Triple(fileName, mimeType, bytes)
            }

            "file", null -> {
                val filePath = if (uri.scheme == "file") uri.path else uri.toString()
                val file = File(filePath ?: throw IllegalArgumentException("Invalid file URI: $uri"))
                require(file.exists()) { "File does not exist: ${file.absolutePath}" }
                Triple(file.name, "application/octet-stream", file.readBytes())
            }

            else -> throw IllegalArgumentException("Only content:// and file paths are supported for upload")
        }
    }

    private fun postFormUrlEncoded(endpoint: String, token: String, form: List<Pair<String, String>>): HttpResponse {
        val encoded = form.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        connection.outputStream.use { output ->
            output.write(encoded.toByteArray())
        }
        return readResponse(connection)
    }

    private fun postMultipart(
        endpoint: String,
        token: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        alt: String,
        onProgress: (Int) -> Unit
    ): HttpResponse {
        val boundary = "----microblogwriter${System.currentTimeMillis()}"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        DataOutputStream(connection.outputStream).use { output ->
            if (alt.isNotBlank()) {
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"alt\"\r\n\r\n")
                output.writeBytes("$alt\r\n")
            }

            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
            output.writeBytes("Content-Type: $mimeType\r\n\r\n")
            val total = bytes.size.coerceAtLeast(1)
            var uploaded = 0
            var offset = 0
            val chunkSize = 8 * 1024
            while (offset < bytes.size) {
                val count = minOf(chunkSize, bytes.size - offset)
                output.write(bytes, offset, count)
                uploaded += count
                offset += count
                onProgress(((uploaded * 100f) / total).toInt().coerceIn(0, 100))
            }
            output.writeBytes("\r\n")
            output.writeBytes("--$boundary--\r\n")
        }

        return readResponse(connection)
    }

    private fun getRequest(endpoint: String, token: String): HttpResponse {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): HttpResponse {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
        val location = connection.getHeaderField("Location")
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: $response")
        }
        return HttpResponse(body = response, location = location)
    }

    private fun extractPublishResponse(response: String): PublishResponse {
        val root = runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull()
            ?: return PublishResponse(postId = null, permalink = null)
        val permalink = root["url"]?.jsonPrimitive?.contentOrNull
            ?: root["permalink"]?.jsonPrimitive?.contentOrNull
            ?: root["location"]?.jsonPrimitive?.contentOrNull
        val postId = root["post"]?.jsonPrimitive?.contentOrNull
            ?: root["id"]?.jsonPrimitive?.contentOrNull
        return PublishResponse(
            postId = postId,
            permalink = permalink
        )
    }

    private fun extractMediaUrl(response: String): String? {
        val root = runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull() ?: return null
        return root["url"]?.jsonPrimitive?.contentOrNull
            ?: root["path"]?.jsonPrimitive?.contentOrNull
            ?: root["location"]?.jsonPrimitive?.contentOrNull
    }

    private fun parseCategoryArray(element: kotlinx.serialization.json.JsonElement?): List<String> {
        val array = element?.jsonArray ?: return emptyList()
        return array.mapNotNull { value ->
            when (value) {
                is JsonPrimitive -> value.contentOrNull
                is JsonObject -> value["name"]?.jsonPrimitive?.contentOrNull
                    ?: value["uid"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }.filter { it.isNotBlank() }
    }

    private data class HttpResponse(
        val body: String,
        val location: String?
    )

    data class PublishResponse(
        val postId: String?,
        val permalink: String?
    )
}
