package com.example.microblogwriter.ui.editor

import kotlin.math.max
import kotlin.math.min

private const val LINK_PLACEHOLDER_TEXT = "link text"

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

fun insertInlineAtSelection(text: String, selectionStart: Int, selectionEnd: Int, snippet: String): EditorMutation {
    val safeStart = min(max(selectionStart, 0), text.length)
    val safeEnd = min(max(selectionEnd, 0), text.length)
    val selectedRangeStart = min(safeStart, safeEnd)
    val selectedRangeEnd = max(safeStart, safeEnd)
    val newText = text.replaceRange(selectedRangeStart, selectedRangeEnd, snippet)
    val cursor = selectedRangeStart + snippet.length
    return EditorMutation(newText, cursor)
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
