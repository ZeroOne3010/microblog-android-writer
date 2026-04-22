package com.example.microblogwriter.network

import android.content.Context
import android.net.Uri
import com.example.microblogwriter.domain.Draft
import com.example.microblogwriter.domain.SettingsState
import kotlinx.serialization.json.Json
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

    suspend fun publishPost(draft: Draft, settings: SettingsState): Result<String> = runCatching {
        require(settings.microblogAccessToken.isNotBlank()) { "Micro.blog access token is required" }

        val endpoint = "${settings.microblogApiBaseUrl.trimEnd('/')}/micropub"
        val form = buildList {
            add("h" to "entry")
            add("name" to draft.title.ifBlank { "Untitled" })
            add("content" to draft.body)
            draft.categories.forEach { add("category[]" to it) }
            draft.postId?.let { add("mp-destination" to it) }
        }

        val response = postFormUrlEncoded(endpoint, settings.microblogAccessToken, form)
        extractPostId(response) ?: throw IllegalStateException("Publish succeeded but post URL/ID was not returned")
    }

    suspend fun uploadImage(localUri: String, alt: String, settings: SettingsState): Result<String> = runCatching {
        require(settings.microblogAccessToken.isNotBlank()) { "Micro.blog access token is required" }
        require(localUri.isNotBlank()) { "Image URI is required" }

        val mediaEndpoint = resolveMediaEndpoint(settings)
        val uri = Uri.parse(localUri)
        val (fileName, mimeType, bytes) = readImageBytes(uri)

        val response = postMultipart(
            endpoint = mediaEndpoint,
            token = settings.microblogAccessToken,
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            alt = alt
        )

        extractMediaUrl(response) ?: throw IllegalStateException("Upload succeeded but file URL was not returned")
    }

    suspend fun fetchRecentPosts(settings: SettingsState): Result<List<Draft>> = runCatching {
        require(settings.microblogAccessToken.isNotBlank()) { "Micro.blog access token is required" }
        val endpoint = "${settings.microblogApiBaseUrl.trimEnd('/')}/micropub?q=source&limit=20"
        val body = getRequest(endpoint, settings.microblogAccessToken)
        val root = json.parseToJsonElement(body).jsonObject
        val items = root["items"]?.jsonArray ?: return@runCatching emptyList()

        items.mapNotNull { item ->
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

    private fun resolveMediaEndpoint(settings: SettingsState): String {
        if (settings.microblogMediaEndpoint.isNotBlank()) {
            return settings.microblogMediaEndpoint
        }
        val configEndpoint = "${settings.microblogApiBaseUrl.trimEnd('/')}/micropub?q=config"
        val body = getRequest(configEndpoint, settings.microblogAccessToken)
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

    private fun postFormUrlEncoded(endpoint: String, token: String, form: List<Pair<String, String>>): String {
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
        alt: String
    ): String {
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
            output.write(bytes)
            output.writeBytes("\r\n")
            output.writeBytes("--$boundary--\r\n")
        }

        return readResponse(connection)
    }

    private fun getRequest(endpoint: String, token: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: $response")
        }
        return response
    }

    private fun extractPostId(response: String): String? {
        val root = runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull() ?: return null
        return root["url"]?.jsonPrimitive?.contentOrNull
            ?: root["post"]?.jsonPrimitive?.contentOrNull
            ?: root["id"]?.jsonPrimitive?.contentOrNull
    }

    private fun extractMediaUrl(response: String): String? {
        val root = runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull() ?: return null
        return root["url"]?.jsonPrimitive?.contentOrNull
            ?: root["path"]?.jsonPrimitive?.contentOrNull
            ?: root["location"]?.jsonPrimitive?.contentOrNull
    }
}
