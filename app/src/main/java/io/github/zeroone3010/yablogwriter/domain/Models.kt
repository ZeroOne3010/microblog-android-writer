package io.github.zeroone3010.yablogwriter.domain

import io.github.zeroone3010.yablogwriter.auth.AuthState
import java.time.Instant
import java.util.UUID

data class Draft(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val categories: List<String> = emptyList(),
    val status: DraftStatus = DraftStatus.DRAFT,
    val created: Instant = Instant.now(),
    val updated: Instant = Instant.now(),
    val postId: String? = null
)

enum class DraftStatus { DRAFT, PUBLISHED, PENDING_UPLOAD, PENDING_PUBLISH }

enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class TimestampFormat { ISO_24H, DMY_24H, MDY_12H }
enum class AiReviewPromptType { IDEA, DRAFT, FINAL, CUSTOM }

data class SettingsState(
    val aiEnabled: Boolean = false,
    val aiProviderBaseUrl: String = "https://api.openai.com",
    val aiApiKey: String = "",
    val aiIdeaPrompt: String = """
        Here's my idea for a blog post titled {title}. Please help me flesh it out with angles, structure options, and concrete ideas.

        {contents}
    """.trimIndent(),
    val aiIdeaModel: String = "gpt-5.4-mini",
    val aiDraftPrompt: String = """
        Here's the first draft of my new blog post titled {title}. Please give constructive feedback on structure, clarity, and flow while preserving my voice.

        {contents}
    """.trimIndent(),
    val aiDraftModel: String = "gpt-5.4-mini",
    val aiFinalPrompt: String = """
        Here's my new blog post titled {title}. I'm almost ready to publish. Please do a final pass for style, grammar, and typos, and keep suggestions concise.

        {contents}
    """.trimIndent(),
    val aiFinalModel: String = "gpt-5.4-nano",
    val aiCustomPrompt: String = "",
    val aiCustomModel: String = "gpt-5.4-mini",
    val aiSelectedPromptType: AiReviewPromptType = AiReviewPromptType.IDEA,
    val microblogApiBaseUrl: String = "https://micro.blog",
    val microblogMediaEndpoint: String = "",
    val theme: AppTheme = AppTheme.SYSTEM,
    val categoryReminderEnabled: Boolean = true,
    val timestampFormat: TimestampFormat = TimestampFormat.ISO_24H
)

data class LinkDialogState(
    val selectionStart: Int,
    val selectionEnd: Int,
    val selectedText: String,
    val initialUrl: String,
    val asWebmention: Boolean = false
)

enum class UploadStatus { QUEUED, UPLOADING, SUCCEEDED, FAILED }

data class ImageUploadItem(
    val id: String = UUID.randomUUID().toString(),
    val localUri: String,
    val altText: String = "",
    val status: UploadStatus = UploadStatus.QUEUED,
    val progressPercent: Int = 0,
    val uploadedUrl: String? = null,
    val errorMessage: String? = null
)

data class AppUiState(
    val drafts: List<Draft> = emptyList(),
    val selectedDraft: Draft = Draft(),
    val categoryHistory: List<String> = emptyList(),
    val blogCategories: List<String> = emptyList(),
    val blogCategoriesLoading: Boolean = false,
    val aiReviewOutput: String = "",
    val aiReviewInProgress: Boolean = false,
    val previewMode: Boolean = false,
    val markdownWordCount: Int = 0,
    val readingTimeMinutes: Int = 0,
    val statusMessage: String? = null,
    val settings: SettingsState = SettingsState(),
    val auth: AuthState = AuthState(),
    val linkDialogState: LinkDialogState? = null,
    val imageUploadQueue: List<ImageUploadItem> = emptyList()
)
