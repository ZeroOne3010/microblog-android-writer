package io.github.zeroone3010.yablogwriter.ui.editor

import kotlin.math.max
import kotlin.math.min

private const val LINK_PLACEHOLDER_TEXT = "link text"
private const val WEBMENTION_CLASS = "u-in-reply-to"

private val ANCHOR_TAG_REGEX = Regex(
    """<a\b[^>]*\bhref\s*=\s*(?:"([^"]*)"|'([^']*)')[^>]*>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
private val HTML_BREAK_REGEX = Regex("""</?(?:br|p|div|li|h[1-6])\b[^>]*>""", RegexOption.IGNORE_CASE)
private val HTML_ENTITY_REGEX = Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""")

fun markdownFromHtmlClipboard(htmlText: String?, plainText: String?): String? {
    val html = htmlText.orEmpty()
    if (html.isBlank() || !ANCHOR_TAG_REGEX.containsMatchIn(html)) return null

    val markdown = buildString {
        var cursor = 0
        ANCHOR_TAG_REGEX.findAll(html).forEach { match ->
            append(htmlFragmentToPlainText(html.substring(cursor, match.range.first)))
            val href = decodeHtmlEntities(match.groupValues[1].ifBlank { match.groupValues[2] }).trim()
            val label = htmlFragmentToPlainText(match.groupValues[3]).trim()
            if (href.isNotBlank() && !href.startsWith("javascript:", ignoreCase = true) && label.isNotBlank()) {
                append("[")
                append(escapeMarkdownLinkLabel(label))
                append("](")
                append(escapeMarkdownLinkDestination(href))
                append(")")
            } else {
                append(label)
            }
            cursor = match.range.last + 1
        }
        append(htmlFragmentToPlainText(html.substring(cursor)))
    }.trimClipboardWrapperWhitespace(plainText)

    return markdown.takeIf { it.isNotBlank() && it != plainText }
}

fun replacePastedPlainTextWithMarkdown(
    previousText: String,
    newText: String,
    clipboardPlainText: String?,
    clipboardMarkdownText: String?,
    isPasteAction: Boolean
): EditorMutation? {
    if (!isPasteAction) return null
    val plain = clipboardPlainText ?: return null
    val markdown = clipboardMarkdownText ?: return null
    if (plain.isEmpty() || markdown == plain || previousText == newText) return null

    val commonPrefix = previousText.commonPrefixLength(newText)
    val maxSuffix = min(previousText.length - commonPrefix, newText.length - commonPrefix)
    val commonSuffix = previousText.commonSuffixLength(newText, maxSuffix)
    val insertedStart = commonPrefix
    val insertedEnd = newText.length - commonSuffix
    if (insertedStart > insertedEnd) return null

    val inserted = newText.substring(insertedStart, insertedEnd)
    if (!inserted.clipboardEquivalent(plain)) return null

    val updated = newText.replaceRange(insertedStart, insertedEnd, markdown)
    val cursor = insertedStart + markdown.length
    return EditorMutation(updated, cursor)
}

private fun String.trimClipboardWrapperWhitespace(plainText: String?): String {
    val plain = plainText.orEmpty()
    val leading = plain.takeWhile { it.isWhitespace() }
    val trailing = plain.takeLastWhile { it.isWhitespace() }
    return trim().let { "$leading$it$trailing" }
}

private fun htmlFragmentToPlainText(fragment: String): String = decodeHtmlEntities(
    fragment
        .replace(HTML_BREAK_REGEX, "\n")
        .replace(HTML_TAG_REGEX, "")
).collapseExcessBlankLines()

private fun String.collapseExcessBlankLines(): String = replace(Regex("\\n{3,}"), "\n\n")

private fun escapeMarkdownLinkLabel(label: String): String = label
    .replace("\\", "\\\\")
    .replace("]", "\\]")

private fun escapeMarkdownLinkDestination(destination: String): String = destination.replace(")", "%29")

private fun String.clipboardEquivalent(other: String): Boolean = this == other || normalizeClipboardLineEndings() == other.normalizeClipboardLineEndings()

private fun String.normalizeClipboardLineEndings(): String = replace("\r\n", "\n").replace("\r", "\n")

private fun String.commonPrefixLength(other: String): Int {
    val limit = min(length, other.length)
    var index = 0
    while (index < limit && this[index] == other[index]) index++
    return index
}

private fun String.commonSuffixLength(other: String, maxLength: Int): Int {
    var count = 0
    while (count < maxLength && this[length - 1 - count] == other[other.length - 1 - count]) count++
    return count
}

private fun decodeHtmlEntities(value: String): String = HTML_ENTITY_REGEX.replace(value) { match ->
    when (val entity = match.groupValues[1]) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos", "#39" -> "'"
        "nbsp" -> " "
        else -> decodeNumericHtmlEntity(entity) ?: match.value
    }
}

private fun decodeNumericHtmlEntity(entity: String): String? {
    if (!entity.startsWith("#")) return null
    val codePoint = if (entity.startsWith("#x", ignoreCase = true)) {
        entity.drop(2).toIntOrNull(16)
    } else {
        entity.drop(1).toIntOrNull()
    } ?: return null
    return runCatching { String(Character.toChars(codePoint)) }.getOrNull()
}

enum class LinkFormat { MARKDOWN, WEBMENTION }

data class EditorMutation(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart
)

data class LinkInsertionRequest(
    val selectionStart: Int,
    val selectionEnd: Int,
    val selectedText: String,
    val initialUrl: String
)

data class MarkdownImageMatch(
    val rangeStart: Int,
    val rangeEnd: Int,
    val altText: String,
    val url: String
)

fun isValidHttpUrl(raw: String?): Boolean {
    val candidate = raw?.trim().orEmpty()
    if (candidate.isBlank()) return false
    val parsed = runCatching { java.net.URI(candidate) }.getOrNull() ?: return false
    val scheme = parsed.scheme?.lowercase() ?: return false
    return (scheme == "http" || scheme == "https") && !parsed.host.isNullOrBlank()
}

fun buildLinkInsertionRequest(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    clipboardText: String?
): LinkInsertionRequest {
    val safeStart = min(max(selectionStart, 0), text.length)
    val safeEnd = min(max(selectionEnd, 0), text.length)
    val selectedRangeStart = min(safeStart, safeEnd)
    val selectedRangeEnd = max(safeStart, safeEnd)
    val initialUrl = clipboardText?.trim().orEmpty().takeIf(::isValidHttpUrl).orEmpty()

    return LinkInsertionRequest(
        selectionStart = selectedRangeStart,
        selectionEnd = selectedRangeEnd,
        selectedText = text.substring(selectedRangeStart, selectedRangeEnd),
        initialUrl = initialUrl
    )
}

fun insertLinkTemplate(text: String, request: LinkInsertionRequest, url: String): EditorMutation {
    val safeUrl = url.trim()
    val label = request.selectedText.ifBlank { LINK_PLACEHOLDER_TEXT }
    val replacement = "[$label]($safeUrl)"
    val newText = text.replaceRange(request.selectionStart, request.selectionEnd, replacement)

    return if (request.selectedText.isBlank()) {
        val labelStart = request.selectionStart + 1
        val labelEnd = labelStart + LINK_PLACEHOLDER_TEXT.length
        EditorMutation(newText, labelStart, labelEnd)
    } else {
        val cursor = request.selectionStart + replacement.length
        EditorMutation(newText, cursor)
    }
}

fun insertWebmentionLinkTemplate(text: String, request: LinkInsertionRequest, url: String): EditorMutation {
    val safeUrl = url.trim()
    val label = request.selectedText.ifBlank { LINK_PLACEHOLDER_TEXT }
    val replacement = """<a class="$WEBMENTION_CLASS" href="$safeUrl">$label</a>"""
    val newText = text.replaceRange(request.selectionStart, request.selectionEnd, replacement)

    return if (request.selectedText.isBlank()) {
        val labelStart = request.selectionStart + replacement.indexOf(LINK_PLACEHOLDER_TEXT)
        val labelEnd = labelStart + LINK_PLACEHOLDER_TEXT.length
        EditorMutation(newText, labelStart, labelEnd)
    } else {
        val cursor = request.selectionStart + replacement.length
        EditorMutation(newText, cursor)
    }
}

fun autoLinkInsertionRequest(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    clipboardText: String?
): Pair<LinkInsertionRequest, String>? {
    val base = buildLinkInsertionRequest(text, selectionStart, selectionEnd, clipboardText)
    val selected = base.selectedText.trim()
    val clipboard = clipboardText?.trim().orEmpty()

    val selectedIsUrl = isValidHttpUrl(selected)
    val clipboardIsUrl = isValidHttpUrl(clipboard)

    return when {
        selectedIsUrl && clipboard.isNotBlank() && !clipboardIsUrl -> {
            base.copy(selectedText = clipboard) to selected
        }
        clipboardIsUrl && selected.isNotBlank() && !selectedIsUrl -> {
            base to clipboard
        }
        else -> null
    }
}

fun removeAllMoreTags(text: String): String = text.replace("<!--more-->", "")

fun insertInlineAtSelection(text: String, selectionStart: Int, selectionEnd: Int, snippet: String): EditorMutation {
    val safeStart = min(max(selectionStart, 0), text.length)
    val safeEnd = min(max(selectionEnd, 0), text.length)
    val selectedRangeStart = min(safeStart, safeEnd)
    val selectedRangeEnd = max(safeStart, safeEnd)
    val newText = text.replaceRange(selectedRangeStart, selectedRangeEnd, snippet)
    val cursor = selectedRangeStart + snippet.length
    return EditorMutation(newText, cursor)
}

fun wrapSelectionWithMarkup(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    marker: String,
    placeholder: String
): EditorMutation {
    val safeStart = min(max(selectionStart, 0), text.length)
    val safeEnd = min(max(selectionEnd, 0), text.length)
    val selectedRangeStart = min(safeStart, safeEnd)
    val selectedRangeEnd = max(safeStart, safeEnd)
    val selected = text.substring(selectedRangeStart, selectedRangeEnd)
    val content = selected.ifBlank { placeholder }
    val replacement = "$marker$content$marker"
    val newText = text.replaceRange(selectedRangeStart, selectedRangeEnd, replacement)

    return if (selected.isBlank()) {
        val placeholderStart = selectedRangeStart + marker.length
        val placeholderEnd = placeholderStart + placeholder.length
        EditorMutation(newText, placeholderStart, placeholderEnd)
    } else {
        val cursor = selectedRangeStart + replacement.length
        EditorMutation(newText, cursor)
    }
}

fun wrapInCodeBlock(text: String, selectionStart: Int, selectionEnd: Int): EditorMutation {
    val safeStart = min(max(selectionStart, 0), text.length)
    val safeEnd = min(max(selectionEnd, 0), text.length)
    val selectedRangeStart = min(safeStart, safeEnd)
    val selectedRangeEnd = max(safeStart, safeEnd)
    val selected = text.substring(selectedRangeStart, selectedRangeEnd)
    val replacement = if (selected.isBlank()) "```\ncode\n```" else "```\n$selected\n```"
    val newText = text.replaceRange(selectedRangeStart, selectedRangeEnd, replacement)

    return if (selected.isBlank()) {
        val codeStart = selectedRangeStart + 4
        val codeEnd = codeStart + 4
        EditorMutation(newText, codeStart, codeEnd)
    } else {
        val cursor = selectedRangeStart + replacement.length
        EditorMutation(newText, cursor)
    }
}

fun prefixSelectedLines(text: String, selectionStart: Int, selectionEnd: Int, prefix: String): EditorMutation {
    val safeStart = min(max(selectionStart, 0), text.length)
    val safeEnd = min(max(selectionEnd, 0), text.length)
    val rangeStart = min(safeStart, safeEnd)
    val rangeEnd = max(safeStart, safeEnd)

    val lineStart = if (rangeStart == 0) {
        0
    } else {
        text.lastIndexOf('\n', startIndex = rangeStart - 1).let { if (it == -1) 0 else it + 1 }
    }
    val lineEndCandidate = text.indexOf('\n', startIndex = rangeEnd).let { if (it == -1) text.length else it }
    val lineEnd = max(lineStart, lineEndCandidate)
    val block = text.substring(lineStart, lineEnd)
    val prefixed = block.lines().joinToString("\n") { "$prefix$it" }
    val newText = text.replaceRange(lineStart, lineEnd, prefixed)

    val startShift = prefix.length
    val linesTouched = block.lines().size
    val endShift = prefix.length * linesTouched
    return EditorMutation(
        text = newText,
        selectionStart = rangeStart + startShift,
        selectionEnd = rangeEnd + endShift
    )
}

fun findMarkdownImageAtSelection(text: String, selectionStart: Int, selectionEnd: Int): MarkdownImageMatch? {
    val imageRegex = Regex("""!\[([^\]]*)]\(([^)]+)\)""")
    val safeStart = min(max(selectionStart, 0), text.length)
    val safeEnd = min(max(selectionEnd, 0), text.length)
    val point = min(safeStart, safeEnd)
    return imageRegex.findAll(text)
        .firstOrNull { match ->
            val range = match.range
            point in range.first..(range.last + 1)
        }
        ?.let { match ->
            MarkdownImageMatch(
                rangeStart = match.range.first,
                rangeEnd = match.range.last + 1,
                altText = match.groupValues[1],
                url = match.groupValues[2]
            )
        }
}

fun replaceMarkdownImageAltText(text: String, match: MarkdownImageMatch, newAltText: String): EditorMutation {
    val replacement = "![${newAltText.trim()}](${match.url})"
    val newText = text.replaceRange(match.rangeStart, match.rangeEnd, replacement)
    val cursor = match.rangeStart + replacement.length
    return EditorMutation(newText, cursor)
}
