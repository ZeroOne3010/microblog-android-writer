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

data class SettingsState(
    val aiEnabled: Boolean = true,
    val aiProviderBaseUrl: String = "https://api.openai.com",
    val aiApiKey: String = "",
    val aiModel: String = "gpt-5.4-mini",
    val aiPromptTemplate: String = "Here's my latest blog post titled {title}. Please review for any grammatical mistakes. Feel free to suggest changes for better flow, for example, but be careful not to change my own voice. {contents}",
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
    val publishedPosts: List<Draft> = emptyList(),
    val publishedPostsLoading: Boolean = false,
    val publishedPostsError: String? = null,
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
