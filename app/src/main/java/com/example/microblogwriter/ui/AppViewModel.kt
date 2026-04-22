package com.example.microblogwriter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.microblogwriter.ai.AiReviewClient
import com.example.microblogwriter.data.MarkdownDraftRepository
import com.example.microblogwriter.data.SettingsRepository
import com.example.microblogwriter.domain.AppUiState
import com.example.microblogwriter.domain.Draft
import com.example.microblogwriter.domain.DraftStatus
import com.example.microblogwriter.domain.SettingsState
import com.example.microblogwriter.network.MicroblogApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val draftRepo = MarkdownDraftRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val api = MicroblogApi()
    private val ai = AiReviewClient()

    private val _uiState = MutableStateFlow(AppUiState(settings = settingsRepo.load()))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        refreshDrafts()
    }

    fun refreshDrafts() {
        val drafts = draftRepo.listDrafts()
        val categories = drafts.flatMap { it.categories }.distinct().sorted()
        _uiState.update {
            val selectedDraft = drafts.firstOrNull { draft -> draft.id == it.selectedDraft.id }
                ?: drafts.firstOrNull()
                ?: it.selectedDraft
            val words = wordCount(selectedDraft.body)
            it.copy(
                drafts = drafts,
                selectedDraft = selectedDraft,
                categoryHistory = categories,
                markdownWordCount = words,
                readingTimeMinutes = readingTime(words)
            )
        }
    }

    fun editTitle(title: String) = updateDraft { copy(title = title) }
    fun editBody(body: String) = updateDraft { copy(body = body) }

    fun editCategories(rawCategories: String) {
        val categories = rawCategories.split(",").map { it.trim() }.filter { it.isNotBlank() }
        updateDraft { copy(categories = categories) }
    }

    fun togglePreview() {
        _uiState.update { it.copy(previewMode = !it.previewMode) }
    }

    fun insertMoreTag() {
        updateDraft { copy(body = if (body.endsWith("\n")) "$body<!--more-->" else "$body\n<!--more-->") }
    }

    fun insertMarkdownImage(imageUrl: String, alt: String) {
        val markdown = "![${alt.ifBlank { "image" }}]($imageUrl)"
        updateDraft { copy(body = "$body\n$markdown") }
    }

    fun saveDraft() {
        val saved = draftRepo.saveDraft(_uiState.value.selectedDraft)
        _uiState.update { it.copy(selectedDraft = saved, statusMessage = "Draft saved locally") }
        refreshDrafts()
    }

    fun deleteDraft(id: String) {
        draftRepo.deleteDraft(id)
        refreshDrafts()
    }

    fun selectDraft(id: String) {
        val selected = _uiState.value.drafts.find { it.id == id } ?: return
        _uiState.update {
            it.copy(
                selectedDraft = selected,
                markdownWordCount = wordCount(selected.body),
                readingTimeMinutes = readingTime(wordCount(selected.body))
            )
        }
    }

    fun runAiReview() {
        val state = _uiState.value
        if (!state.settings.aiEnabled) return
        val prompt = state.settings.aiPromptTemplate
            .replace("{title}", state.selectedDraft.title.ifBlank { "Untitled" })
            .replace("{contents}", state.selectedDraft.body)

        viewModelScope.launch {
            val result = ai.review(state.settings.aiApiKey, state.settings.aiModel, prompt)
            _uiState.update {
                it.copy(
                    aiReviewOutput = result.getOrElse { err -> "AI review failed: ${err.message}" },
                    statusMessage = "AI review complete"
                )
            }
        }
    }

    fun publishPost() {
        val draft = _uiState.value.selectedDraft
        saveDraft()
        viewModelScope.launch {
            val result = api.publishPost(draft)
            _uiState.update {
                result.fold(
                    onSuccess = { postId ->
                        val published = draft.copy(postId = postId, status = DraftStatus.PUBLISHED)
                        draftRepo.saveDraft(published)
                        it.copy(selectedDraft = published, statusMessage = "Published to Micro.blog")
                    },
                    onFailure = { err ->
                        it.copy(statusMessage = "Publish failed, local draft kept: ${err.message}")
                    }
                )
            }
            refreshDrafts()
        }
    }

    fun updateSettings(settings: SettingsState) {
        settingsRepo.save(settings)
        _uiState.update { it.copy(settings = settings, statusMessage = "Settings saved") }
    }

    private fun updateDraft(update: Draft.() -> Draft) {
        val updated = _uiState.value.selectedDraft.update().copy(status = DraftStatus.DRAFT)
        _uiState.update {
            val words = wordCount(updated.body)
            it.copy(selectedDraft = updated, markdownWordCount = words, readingTimeMinutes = readingTime(words))
        }
    }

    private fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    private fun readingTime(words: Int): Int = max(1, words / 220)
}
