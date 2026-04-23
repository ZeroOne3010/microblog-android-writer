package com.example.microblogwriter.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditorHelpersTest {

    @Test
    fun `isValidHttpUrl accepts http and https only`() {
        assertTrue(isValidHttpUrl("https://example.com/path"))
        assertTrue(isValidHttpUrl("http://example.com"))
        assertFalse(isValidHttpUrl("ftp://example.com"))
        assertFalse(isValidHttpUrl("not a url"))
        assertFalse(isValidHttpUrl("https:///missing-host"))
    }

    @Test
    fun `buildLinkInsertionRequest prefills url only when clipboard is valid url`() {
        val valid = buildLinkInsertionRequest("hello world", 0, 5, "https://micro.blog")
        assertEquals("https://micro.blog", valid.initialUrl)
        assertEquals("hello", valid.selectedText)

        val invalid = buildLinkInsertionRequest("hello world", 0, 5, "draft note")
        assertEquals("", invalid.initialUrl)
    }

    @Test
    fun `insertLinkTemplate wraps selected text and places cursor at end`() {
        val request = LinkInsertionRequest(
            selectionStart = 6,
            selectionEnd = 11,
            selectedText = "world",
            initialUrl = ""
        )

        val mutation = insertLinkTemplate("hello world", request, "https://example.com")

        assertEquals("hello [world](https://example.com)", mutation.text)
        assertEquals(mutation.selectionStart, mutation.selectionEnd)
        assertEquals(mutation.text.length, mutation.selectionStart)
    }

    @Test
    fun `insertLinkTemplate inserts placeholder and selects it when no text is selected`() {
        val request = LinkInsertionRequest(
            selectionStart = 0,
            selectionEnd = 0,
            selectedText = "",
            initialUrl = ""
        )

        val mutation = insertLinkTemplate("", request, "https://example.com")

        assertEquals("[link text](https://example.com)", mutation.text)
        assertEquals(1, mutation.selectionStart)
        assertEquals(10, mutation.selectionEnd)
    }
}
