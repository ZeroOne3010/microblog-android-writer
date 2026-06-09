package io.github.zeroone3010.yablogwriter.network

import io.github.zeroone3010.yablogwriter.domain.Draft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicroblogApiTest {
    @Test
    fun `new posts use form encoded micropub fields`() {
        val request = buildMicropubPublishRequest(
            Draft(
                title = "A title",
                body = "Hello world",
                categories = listOf("Testing", "Android")
            )
        )

        val form = request as MicropubPublishRequest.Form
        assertEquals(
            listOf(
                "h" to "entry",
                "name" to "A title",
                "content" to "Hello world",
                "category[]" to "Testing",
                "category[]" to "Android"
            ),
            form.parameters
        )
    }

    @Test
    fun `published posts use json micropub update requests`() {
        val request = buildMicropubPublishRequest(
            Draft(
                title = "Updated title",
                body = "Updated body",
                categories = listOf("Testing", "Android"),
                postId = "https://example.com/2026/06/09/post.html"
            )
        )

        val jsonRequest = request as MicropubPublishRequest.Json
        val root = Json.parseToJsonElement(jsonRequest.body).jsonObject
        val replace = root.getValue("replace").jsonObject

        assertEquals("update", root.getValue("action").jsonPrimitive.content)
        assertEquals("https://example.com/2026/06/09/post.html", root.getValue("url").jsonPrimitive.content)
        assertEquals("Updated title", replace.getValue("name").jsonArray.single().jsonPrimitive.content)
        assertEquals("Updated body", replace.getValue("content").jsonArray.single().jsonPrimitive.content)
        assertEquals(
            listOf("Testing", "Android"),
            replace.getValue("category").jsonArray.map { it.jsonPrimitive.content }
        )
        assertTrue(jsonRequest.body.contains("\"replace\""))
    }
}
