package io.github.zeroone3010.yablogwriter.ui.editor

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


    @Test
    fun `markdownFromHtmlClipboard converts rich links to markdown`() {
        val markdown = markdownFromHtmlClipboard(
            """Read <a href="https://example.com/post?x=1&amp;y=2">Example &amp; Co</a> today.""",
            "Read Example & Co today."
        )

        assertEquals("Read [Example & Co](https://example.com/post?x=1&y=2) today.", markdown)
    }

    @Test
    fun `markdownFromHtmlClipboard converts multiple rich links`() {
        val markdown = markdownFromHtmlClipboard(
            """<p><a href='https://one.example'>One</a> and <a href="https://two.example/path)">Two</a></p>""",
            "One and Two"
        )

        assertEquals("[One](https://one.example) and [Two](https://two.example/path%29)", markdown)
    }

    @Test
    fun `replacePastedPlainTextWithMarkdown swaps detected paste with markdown`() {
        val mutation = replacePastedPlainTextWithMarkdown(
            previousText = "Before  after",
            newText = "Before Example after",
            clipboardPlainText = "Example",
            clipboardMarkdownText = "[Example](https://example.com)"
        )

        assertEquals("Before [Example](https://example.com) after", mutation?.text)
        assertEquals("Before [Example](https://example.com)".length, mutation?.selectionStart)
    }

    @Test
    fun `wrapSelectionWithMarkup wraps selected text and places cursor at end`() {
        val mutation = wrapSelectionWithMarkup("hello world", 6, 11, "**", "bold text")

        assertEquals("hello **world**", mutation.text)
        assertEquals(mutation.selectionStart, mutation.selectionEnd)
        assertEquals(mutation.text.length, mutation.selectionStart)
    }

    @Test
    fun `wrapSelectionWithMarkup inserts placeholder and selects it when no text is selected`() {
        val mutation = wrapSelectionWithMarkup("hello ", 6, 6, "_", "italic text")

        assertEquals("hello _italic text_", mutation.text)
        assertEquals(7, mutation.selectionStart)
        assertEquals(18, mutation.selectionEnd)
    }

    @Test
    fun `prefixSelectedLines handles caret at start when text begins with newline`() {
        val mutation = prefixSelectedLines("\nhello", 0, 0, "# ")

        assertEquals("# \nhello", mutation.text)
        assertEquals(2, mutation.selectionStart)
        assertEquals(2, mutation.selectionEnd)
    }

    @Test
    fun `findMarkdownImageAtSelection finds image at caret`() {
        val text = "Intro\n![old alt](https://example.com/image.jpg)\nOutro"
        val match = findMarkdownImageAtSelection(text, 10, 10)

        assertEquals("old alt", match?.altText)
        assertEquals("https://example.com/image.jpg", match?.url)
    }

    @Test
    fun `replaceMarkdownImageAltText updates alt text only`() {
        val text = "![old alt](https://example.com/image.jpg)"
        val match = findMarkdownImageAtSelection(text, 2, 2) ?: error("Expected match")

        val mutation = replaceMarkdownImageAltText(text, match, "new alt")

        assertEquals("![new alt](https://example.com/image.jpg)", mutation.text)
        assertEquals(mutation.selectionStart, mutation.selectionEnd)
    }
}
