package com.example.microblogwriter.domain

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

data class SettingsState(
    val aiEnabled: Boolean = true,
    val aiApiKey: String = "",
    val aiModel: String = "gpt-4.1-mini",
    val aiPromptTemplate: String = "Here's my latest blog post titled {title}. Please review for any grammatical mistakes. Feel free to suggest changes for better flow, for example, but be careful not to change my own voice. {contents}",
    val microblogApiBaseUrl: String = "https://micro.blog",
    val microblogAccessToken: String = "",
    val microblogMediaEndpoint: String = "",
    val theme: AppTheme = AppTheme.SYSTEM,
    val categoryReminderEnabled: Boolean = true
)

data class AppUiState(
    val drafts: List<Draft> = emptyList(),
    val selectedDraft: Draft = Draft(),
    val categoryHistory: List<String> = emptyList(),
    val publishedPosts: List<Draft> = emptyList(),
    val aiReviewOutput: String = "",
    val previewMode: Boolean = false,
    val markdownWordCount: Int = 0,
    val readingTimeMinutes: Int = 0,
    val statusMessage: String? = null,
    val settings: SettingsState = SettingsState()
)
