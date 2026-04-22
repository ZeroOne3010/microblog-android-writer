package com.example.microblogwriter.data

import android.content.Context
import com.example.microblogwriter.domain.Draft
import java.io.File
import java.time.Instant

class MarkdownDraftRepository(private val context: Context) {
    private val draftsDir: File by lazy { File(context.filesDir, "drafts").apply { mkdirs() } }

    fun listDrafts(): List<Draft> = draftsDir.listFiles()
        ?.filter { it.extension == "md" }
        ?.mapNotNull { parseDraft(it) }
        ?.sortedByDescending { it.updated }
        ?: emptyList()

    fun saveDraft(draft: Draft): Draft {
        val file = File(draftsDir, "${draft.id}.md")
        val updatedDraft = draft.copy(updated = Instant.now())
        file.writeText(toMarkdown(updatedDraft))
        return updatedDraft
    }

    fun deleteDraft(id: String) {
        File(draftsDir, "$id.md").delete()
    }

    private fun parseDraft(file: File): Draft? {
        val text = file.readText()
        val parts = text.split("---")
        if (parts.size < 3) return null
        val yaml = parts[1]
        val body = parts.drop(2).joinToString("---").trim()
        val map = yaml.lines().mapNotNull { line ->
            val idx = line.indexOf(":")
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }.toMap()
        return Draft(
            id = file.nameWithoutExtension,
            title = map["title"] ?: "",
            categories = yaml.lines().filter { it.trimStart().startsWith("- ") }.map { it.substringAfter("- ").trim() },
            body = body,
            created = map["created"]?.let { Instant.parse(it) } ?: Instant.now(),
            updated = map["updated"]?.let { Instant.parse(it) } ?: Instant.now(),
            postId = map["post_id"]?.takeUnless { it == "null" }
        )
    }

    private fun toMarkdown(draft: Draft): String = buildString {
        appendLine("---")
        appendLine("title: ${draft.title}")
        appendLine("categories:")
        draft.categories.forEach { appendLine("  - $it") }
        appendLine("status: ${draft.status.name.lowercase()}")
        appendLine("created: ${draft.created}")
        appendLine("updated: ${draft.updated}")
        appendLine("post_id: ${draft.postId ?: "null"}")
        appendLine("---")
        appendLine()
        append(draft.body)
    }
}
