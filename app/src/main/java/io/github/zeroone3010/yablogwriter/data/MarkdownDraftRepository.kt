package io.github.zeroone3010.yablogwriter.data

import android.content.Context
import io.github.zeroone3010.yablogwriter.domain.Draft
import io.github.zeroone3010.yablogwriter.domain.DraftStatus
import java.io.File
import java.time.Instant
import java.util.Locale

class MarkdownDraftRepository(private val context: Context) {
    private val draftsDir: File by lazy {
        val internalDir = File(context.filesDir, "drafts").apply { mkdirs() }
        val preferredExternalDir = context.externalMediaDirs
            .firstOrNull()
            ?.let { File(it, "yablogwriter-drafts") }
            ?.takeIf { dir -> (dir.exists() || dir.mkdirs()) && dir.canWrite() }
        preferredExternalDir ?: internalDir
    }

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

    fun createDraft(initialDraft: Draft = Draft()): Draft = saveDraft(initialDraft.copy(status = DraftStatus.DRAFT))

    fun deleteDraft(id: String) {
        File(draftsDir, "$id.md").delete()
    }

    fun duplicateDraft(id: String): Draft? {
        val source = File(draftsDir, "$id.md")
        if (!source.exists()) return null
        val sourceDraft = parseDraft(source) ?: return null
        val duplicatedTitle = sourceDraft.title.ifBlank { "Untitled draft" } + " (Copy)"
        val duplicateId = createUniqueId(slugify(duplicatedTitle).ifBlank { "${sourceDraft.id}-copy" })
        val duplicated = sourceDraft.copy(
            id = duplicateId,
            title = duplicatedTitle,
            updated = Instant.now(),
            status = DraftStatus.DRAFT,
            postId = null
        )
        File(draftsDir, "$duplicateId.md").writeText(toMarkdown(duplicated))
        return duplicated
    }

    fun renameDraft(id: String, newTitleOrSlug: String): Draft? {
        val source = File(draftsDir, "$id.md")
        if (!source.exists()) return null
        val draft = parseDraft(source) ?: return null
        val trimmed = newTitleOrSlug.trim()
        if (trimmed.isBlank()) return null

        val newId = createUniqueId(slugify(trimmed).ifBlank { id }, excludeId = id)
        val renamed = draft.copy(
            id = newId,
            title = trimmed,
            updated = Instant.now()
        )
        val destination = File(draftsDir, "$newId.md")
        destination.writeText(toMarkdown(renamed))
        if (destination.absolutePath != source.absolutePath) {
            source.delete()
        }
        return renamed
    }

    fun importRemoteDraft(remoteDraft: Draft): Draft {
        val existing = remoteDraft.postId?.let { remoteId ->
            listDrafts().firstOrNull { it.postId == remoteId }
        }
        val now = Instant.now()
        val imported = remoteDraft.copy(
            id = existing?.id ?: createUniqueId(slugify(remoteDraft.title).ifBlank { "imported-post" }),
            status = DraftStatus.DRAFT,
            created = existing?.created ?: now,
            updated = now
        )
        File(draftsDir, "${imported.id}.md").writeText(toMarkdown(imported))
        return imported
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
            status = map["status"]?.let { parseStatus(it) } ?: DraftStatus.DRAFT,
            created = map["created"]?.let { Instant.parse(it) } ?: Instant.now(),
            updated = map["updated"]?.let { Instant.parse(it) } ?: Instant.now(),
            postId = map["post_id"]?.takeUnless { it == "null" }
        )
    }

    private fun parseStatus(raw: String): DraftStatus = runCatching {
        DraftStatus.valueOf(raw.trim().uppercase())
    }.getOrDefault(DraftStatus.DRAFT)

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

    private fun createUniqueId(base: String, excludeId: String? = null): String {
        var candidate = base
        var suffix = 2
        while (true) {
            if (candidate == excludeId || !File(draftsDir, "$candidate.md").exists()) {
                return candidate
            }
            candidate = "$base-$suffix"
            suffix++
        }
    }

    private fun slugify(text: String): String = text
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}
