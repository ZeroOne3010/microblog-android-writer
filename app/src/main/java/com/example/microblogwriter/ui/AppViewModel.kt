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
import com.example.microblogwriter.domain.LinkDialogState
import com.example.microblogwriter.domain.SettingsState
import com.example.microblogwriter.ui.editor.buildLinkInsertionRequest
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
    private val api = MicroblogApi(application)
    private val ai = AiReviewClient()

    private val _uiState = MutableStateFlow(AppUiState(settings = settingsRepo.load()))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        refreshDrafts()
        refreshPublishedPosts()
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


    fun requestLinkInsertion(body: String, selectionStart: Int, selectionEnd: Int, clipboardText: String?) {
        val request = buildLinkInsertionRequest(body, selectionStart, selectionEnd, clipboardText)
        _uiState.update {
            it.copy(
                linkDialogState = LinkDialogState(
                    selectionStart = request.selectionStart,
                    selectionEnd = request.selectionEnd,
                    selectedText = request.selectedText,
                    initialUrl = request.initialUrl
                )
            )
        }
    }

    fun dismissLinkDialog() {
        _uiState.update { it.copy(linkDialogState = null) }
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

    fun duplicateDraft(id: String) {
        val duplicated = draftRepo.duplicateDraft(id)
        refreshDrafts()
        _uiState.update {
            it.copy(
                selectedDraft = duplicated ?: it.selectedDraft,
                statusMessage = if (duplicated != null) "Draft duplicated" else "Could not duplicate draft"
            )
        }
    }

    fun renameDraft(id: String, newTitleOrSlug: String) {
        val renamed = draftRepo.renameDraft(id, newTitleOrSlug)
        refreshDrafts()
        _uiState.update {
            it.copy(
                selectedDraft = renamed ?: it.selectedDraft,
                statusMessage = if (renamed != null) "Draft renamed" else "Could not rename draft"
            )
        }
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
        val draft = draftRepo.saveDraft(_uiState.value.selectedDraft)
        _uiState.update { it.copy(selectedDraft = draft) }
        viewModelScope.launch {
            val result = api.publishPost(draft, _uiState.value.settings)
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
            refreshPublishedPosts()
        }
    }

    fun refreshPublishedPosts() {
        _uiState.update { it.copy(publishedPostsLoading = true, publishedPostsError = null) }
        viewModelScope.launch {
            val result = api.fetchRecentPosts(_uiState.value.settings)
            _uiState.update {
                result.fold(
                    onSuccess = { posts ->
                        it.copy(
                            publishedPosts = posts,
                            publishedPostsLoading = false,
                            publishedPostsError = null,
                            statusMessage = "Fetched ${posts.size} published posts"
                        )
                    },
                    onFailure = { err ->
                        it.copy(
                            publishedPosts = emptyList(),
                            publishedPostsLoading = false,
                            publishedPostsError = err.message ?: "Unable to fetch published posts",
                            statusMessage = "Fetch published posts failed: ${err.message}"
                        )
                    }
                )
            }
        }
    }

    fun importPublishedPost(post: Draft) {
        val imported = draftRepo.importRemoteDraft(post)
        refreshDrafts()
        _uiState.update {
            it.copy(
                selectedDraft = imported,
                markdownWordCount = wordCount(imported.body),
                readingTimeMinutes = readingTime(wordCount(imported.body)),
                statusMessage = "Imported published post into local drafts"
            )
        }
    }

    fun openPublishedPostInEditor(post: Draft) {
        val local = linkedLocalDraft(post.postId)
        val selected = local ?: post.copy(status = DraftStatus.DRAFT)
        _uiState.update {
            val words = wordCount(selected.body)
            it.copy(
                selectedDraft = selected,
                markdownWordCount = words,
                readingTimeMinutes = readingTime(words),
                statusMessage = if (local != null) {
                    "Opened linked local draft in editor"
                } else {
                    "Opened remote post in editor (import to save locally)"
                }
            )
        }
    }

    fun republishUpdate(post: Draft) {
        val local = linkedLocalDraft(post.postId) ?: draftRepo.importRemoteDraft(post)
        _uiState.update { it.copy(selectedDraft = local) }
        publishPost()
    }

    fun uploadImageAndInsert(localUri: String, alt: String) {
        viewModelScope.launch {
            val result = api.uploadImage(localUri, alt, _uiState.value.settings)
            _uiState.update {
                result.fold(
                    onSuccess = { url ->
                        val draft = it.selectedDraft.copy(body = "${it.selectedDraft.body}\n![${alt.ifBlank { "image" }}]($url)")
                        val words = wordCount(draft.body)
                        it.copy(
                            selectedDraft = draft,
                            markdownWordCount = words,
                            readingTimeMinutes = readingTime(words),
                            statusMessage = "Image uploaded to Micro.blog"
                        )
                    },
                    onFailure = { err ->
                        it.copy(statusMessage = "Image upload failed: ${err.message}")
                    }
                )
            }
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

    private fun linkedLocalDraft(postId: String?): Draft? {
        if (postId.isNullOrBlank()) return null
        return _uiState.value.drafts.firstOrNull { it.postId == postId }
    }

    private fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    private fun readingTime(words: Int): Int = max(1, words / 220)
}
