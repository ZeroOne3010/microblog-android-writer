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
            ?.takeIf { dir -> (dir.exists() || dir.mkdirs()) && dir.canWrite() }
        preferredExternalDir ?: internalDir
    }

    fun listDrafts(): List<Draft> = draftFiles()
        ?.mapNotNull { parseDraft(it) }
        ?.sortedByDescending { it.updated }
        ?: emptyList()

    fun saveDraft(draft: Draft): Draft {
        val destination = resolveFileForId(draft.id)
        destination.parentFile?.mkdirs()
        val updatedDraft = draft.copy(updated = Instant.now())
        destination.writeText(toMarkdown(updatedDraft))
        return updatedDraft
    }

    fun createDraft(initialDraft: Draft = Draft()): Draft {
        val id = createUniqueId(baseFileName(initialDraft.title))
        return saveDraft(initialDraft.copy(id = id, status = DraftStatus.DRAFT))
    }

    fun deleteDraft(id: String) {
        resolveFileForId(id).delete()
    }

    fun duplicateDraft(id: String): Draft? {
        val source = resolveFileForId(id)
        if (!source.exists()) return null
        val sourceDraft = parseDraft(source) ?: return null
        val duplicatedTitle = sourceDraft.title.ifBlank { "Untitled draft" } + " (Copy)"
        val duplicateId = createUniqueId(baseFileName(duplicatedTitle))
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
        val source = resolveFileForId(id)
        if (!source.exists()) return null
        val draft = parseDraft(source) ?: return null
        val trimmed = newTitleOrSlug.trim()
        if (trimmed.isBlank()) return null

        val newId = createUniqueId(baseFileName(trimmed), excludeId = id)
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
            id = existing?.id ?: createUniqueId(baseFileName(remoteDraft.title)),
            status = DraftStatus.DRAFT,
            created = existing?.created ?: now,
            updated = now
        )
        File(draftsDir, "${imported.id}.md").writeText(toMarkdown(imported))
        return imported
    }

    private fun draftFiles(): List<File>? = draftsDir.walkTopDown()
        .filter { it.isFile && it.extension == "md" }
        .toList()

    private fun resolveFileForId(id: String): File = File(draftsDir, "$id.md")

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
            id = file.relativeTo(draftsDir).invariantSeparatorsPath.removeSuffix(".md"),
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
            if (candidate == excludeId || !resolveFileForId(candidate).exists()) {
                return candidate
            }
            candidate = "$base-$suffix"
            suffix++
        }
    }

    private fun baseFileName(title: String): String = sanitizeToAlphanumeric(title).ifBlank { "draft" }

    private fun sanitizeToAlphanumeric(text: String): String = text
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]"), "")
}
