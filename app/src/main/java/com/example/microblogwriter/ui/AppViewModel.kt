package com.example.microblogwriter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.microblogwriter.ai.AiReviewClient
import com.example.microblogwriter.auth.AuthConfig
import com.example.microblogwriter.auth.AuthRepository
import com.example.microblogwriter.auth.AuthState
import com.example.microblogwriter.auth.MicroblogAuthApi
import com.example.microblogwriter.auth.PendingAuthSession
import com.example.microblogwriter.data.MarkdownDraftRepository
import com.example.microblogwriter.data.SettingsRepository
import com.example.microblogwriter.domain.AppUiState
import com.example.microblogwriter.domain.Draft
import com.example.microblogwriter.domain.DraftStatus
import com.example.microblogwriter.domain.ImageUploadItem
import com.example.microblogwriter.domain.LinkDialogState
import com.example.microblogwriter.domain.SettingsState
import com.example.microblogwriter.domain.UploadStatus
import com.example.microblogwriter.network.MicroblogApi
import com.example.microblogwriter.ui.editor.buildLinkInsertionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val draftRepo = MarkdownDraftRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val authRepo = AuthRepository(application)
    private val api = MicroblogApi(application)
    private val authApi = MicroblogAuthApi()
    private val ai = AiReviewClient()

    private var pendingAuthSession: PendingAuthSession? = authRepo.loadPendingAuth()

    private val _uiState = MutableStateFlow(AppUiState(settings = settingsRepo.load(), auth = authRepo.load()))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var lastPublishedPostId: String? = null
    private var lastPublishedPermalink: String? = null

    init {
        refreshDrafts()
        refreshPublishedPosts()
    }

    fun beginSignIn(me: String, openUrl: (String) -> Unit) {
        val normalizedMe = normalizeMe(me)
        val clientId = "https://micro.blog/apps"
        val redirectUri = "microblogwriter://auth/callback"
        val config = AuthConfig(
            clientId = clientId,
            redirectUri = redirectUri,
            state = UUID.randomUUID().toString(),
            me = normalizedMe
        )
        pendingAuthSession = null
        authRepo.clearPendingAuth()
        _uiState.update { it.copy(auth = it.auth.copy(authInProgress = true, authError = null)) }

        viewModelScope.launch {
            val discovered = authApi.discoverEndpoints(config.me)
            discovered.fold(
                onSuccess = { endpoints ->
                    val session = PendingAuthSession(
                        config = config,
                        authorizationEndpoint = endpoints.first,
                        tokenEndpoint = endpoints.second
                    )
                    pendingAuthSession = session
                    authRepo.savePendingAuth(session)
                    val url = authApi.buildAuthorizationUrl(config, endpoints.first)
                    _uiState.update {
                        it.copy(
                            auth = it.auth.copy(
                                authInProgress = false,
                                authorizationEndpoint = endpoints.first,
                                tokenEndpoint = endpoints.second,
                                authError = null
                            )
                        )
                    }
                    openUrl(url)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(auth = it.auth.copy(authInProgress = false, authError = err.message)) }
                }
            )
        }
    }

    fun handleAuthRedirect(uri: Uri?) {
        if (uri == null || uri.scheme != "microblogwriter" || uri.host != "auth") return
        val code = uri.getQueryParameter("code") ?: return
        val state = uri.getQueryParameter("state") ?: ""
        val session = pendingAuthSession ?: authRepo.loadPendingAuth()
        val config = session?.config
        if (config == null || config.state != state) {
            pendingAuthSession = null
            authRepo.clearPendingAuth()
            _uiState.update { it.copy(auth = it.auth.copy(authError = "Invalid auth state returned", authInProgress = false)) }
            return
        }
        val tokenEndpoint = session?.tokenEndpoint.orEmpty().ifBlank { _uiState.value.auth.tokenEndpoint }
        if (tokenEndpoint.isBlank()) {
            _uiState.update { it.copy(auth = it.auth.copy(authError = "Missing token endpoint", authInProgress = false)) }
            return
        }

        _uiState.update { it.copy(auth = it.auth.copy(authInProgress = true, authError = null)) }
        viewModelScope.launch {
            val result = authApi.exchangeCodeForToken(tokenEndpoint, code, config)
            _uiState.update { stateNow ->
                result.fold(
                    onSuccess = { auth ->
                        val merged = auth.copy(
                            authorizationEndpoint = stateNow.auth.authorizationEndpoint,
                            tokenEndpoint = tokenEndpoint,
                            authInProgress = false,
                            authError = null
                        )
                        authRepo.save(merged)
                        pendingAuthSession = null
                        authRepo.clearPendingAuth()
                        stateNow.copy(auth = merged, statusMessage = "Authenticated with Micro.blog")
                    },
                    onFailure = { err ->
                        stateNow.copy(
                            auth = stateNow.auth.copy(authInProgress = false, authError = err.message),
                            statusMessage = "Authentication failed"
                        )
                    }
                )
            }
        }
    }

    fun logout() {
        authRepo.clear()
        pendingAuthSession = null
        _uiState.update {
            it.copy(
                auth = AuthState(),
                publishedPosts = emptyList(),
                publishedPostsError = null,
                imageUploadQueue = emptyList(),
                statusMessage = "Signed out"
            )
        }
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

    fun queueImages(localUris: List<String>) {
        if (!ensureAuthenticated("Sign in to upload images.")) return
        if (localUris.isEmpty()) return
        _uiState.update { state ->
            val existingUris = state.imageUploadQueue.map { it.localUri }.toSet()
            val newItems = localUris
                .filterNot(existingUris::contains)
                .map { uri -> ImageUploadItem(localUri = uri) }
            state.copy(
                imageUploadQueue = state.imageUploadQueue + newItems,
                statusMessage = if (newItems.isNotEmpty()) "Queued ${newItems.size} image(s)" else "Images already queued"
            )
        }
    }

    fun updateUploadAltText(itemId: String, altText: String) {
        _uiState.update {
            it.copy(imageUploadQueue = it.imageUploadQueue.map { item -> if (item.id == itemId) item.copy(altText = altText) else item })
        }
    }

    fun removeUploadItem(itemId: String) {
        _uiState.update { it.copy(imageUploadQueue = it.imageUploadQueue.filterNot { item -> item.id == itemId }) }
    }

    fun uploadQueuedImages() {
        if (!ensureAuthenticated("Sign in to upload images.")) return
        val queue = _uiState.value.imageUploadQueue.filter { it.status != UploadStatus.SUCCEEDED && it.status != UploadStatus.UPLOADING }
        if (queue.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "No queued images to upload") }
            return
        }

        queue.forEach { item ->
            _uiState.update { state ->
                state.copy(imageUploadQueue = state.imageUploadQueue.map { queued ->
                    if (queued.id == item.id) queued.copy(status = UploadStatus.UPLOADING, progressPercent = 0, errorMessage = null)
                    else queued
                })
            }

            viewModelScope.launch {
                val snapshot = _uiState.value.imageUploadQueue.firstOrNull { it.id == item.id } ?: return@launch
                val token = _uiState.value.auth.accessToken
                val result = api.uploadImage(snapshot.localUri, snapshot.altText, _uiState.value.settings, token) { percent ->
                    _uiState.update { state ->
                        state.copy(imageUploadQueue = state.imageUploadQueue.map { queued ->
                            if (queued.id == item.id) queued.copy(progressPercent = percent, status = UploadStatus.UPLOADING) else queued
                        })
                    }
                }

                _uiState.update { state ->
                    result.fold(
                        onSuccess = { url ->
                            state.copy(
                                imageUploadQueue = state.imageUploadQueue.map { queued ->
                                    if (queued.id == item.id) queued.copy(status = UploadStatus.SUCCEEDED, progressPercent = 100, uploadedUrl = url, errorMessage = null)
                                    else queued
                                },
                                statusMessage = "Uploaded image"
                            )
                        },
                        onFailure = { err ->
                            state.copy(
                                imageUploadQueue = state.imageUploadQueue.map { queued ->
                                    if (queued.id == item.id) queued.copy(status = UploadStatus.FAILED, progressPercent = 0, uploadedUrl = null, errorMessage = err.message ?: "Upload failed")
                                    else queued
                                },
                                statusMessage = "One or more image uploads failed"
                            )
                        }
                    )
                }
            }
        }
    }

    fun insertUploadedImagesMarkdown() {
        val uploaded = _uiState.value.imageUploadQueue.filter { it.status == UploadStatus.SUCCEEDED && !it.uploadedUrl.isNullOrBlank() }
        if (uploaded.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "No uploaded images available to insert") }
            return
        }
        val markdownBlock = uploaded.joinToString("\n") { item -> "![${item.altText.ifBlank { "image" }}](${item.uploadedUrl})" }
        updateDraft { copy(body = if (body.isBlank()) markdownBlock else "$body\n$markdownBlock") }
        _uiState.update { it.copy(statusMessage = "Inserted ${uploaded.size} uploaded image(s)") }
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
        _uiState.update { it.copy(selectedDraft = duplicated ?: it.selectedDraft, statusMessage = if (duplicated != null) "Draft duplicated" else "Could not duplicate draft") }
    }

    fun renameDraft(id: String, newTitleOrSlug: String) {
        val renamed = draftRepo.renameDraft(id, newTitleOrSlug)
        refreshDrafts()
        _uiState.update { it.copy(selectedDraft = renamed ?: it.selectedDraft, statusMessage = if (renamed != null) "Draft renamed" else "Could not rename draft") }
    }

    fun selectDraft(id: String) {
        val selected = _uiState.value.drafts.find { it.id == id } ?: return
        _uiState.update {
            it.copy(selectedDraft = selected, markdownWordCount = wordCount(selected.body), readingTimeMinutes = readingTime(wordCount(selected.body)))
        }
    }

    fun runAiReview() {
        val state = _uiState.value
        if (!state.settings.aiEnabled) return
        val prompt = state.settings.aiPromptTemplate
            .replace("{title}", state.selectedDraft.title.ifBlank { "Untitled" })
            .replace("{contents}", state.selectedDraft.body)

        viewModelScope.launch {
            val result = ai.review(state.settings.aiProviderBaseUrl, state.settings.aiApiKey, state.settings.aiModel, prompt)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { output -> current.copy(aiReviewOutput = output, statusMessage = "AI review complete") },
                    onFailure = { err -> current.copy(aiReviewOutput = "", statusMessage = "AI review failed: ${mapAiError(err)}") }
                )
            }
        }
    }

    fun publishPost() {
        if (!ensureAuthenticated("Sign in to publish posts.")) return
        val draft = draftRepo.saveDraft(_uiState.value.selectedDraft)
        _uiState.update { it.copy(selectedDraft = draft) }
        viewModelScope.launch {
            val result = api.publishPost(draft, _uiState.value.settings, _uiState.value.auth.accessToken)
            result.fold(
                onSuccess = { publishResponse ->
                    val resolvedPostId = publishResponse.postId ?: publishResponse.permalink ?: draft.postId
                    val published = draft.copy(postId = resolvedPostId, status = DraftStatus.PUBLISHED)
                    lastPublishedPostId = resolvedPostId
                    lastPublishedPermalink = publishResponse.permalink
                    draftRepo.saveDraft(published)
                    _uiState.update {
                        it.copy(
                            selectedDraft = published,
                            statusMessage = "Published successfully. Synced posts list may update shortly."
                        )
                    }
                    _events.emit(UiEvent.NavigateToPosts)
                    publishResponse.permalink
                        ?.takeIf(String::isNotBlank)
                        ?.let { permalink -> _events.emit(UiEvent.PromptOpenInBrowser(permalink)) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = "Publish failed, local draft kept: ${err.message}") }
                }
            )
            if (result.isSuccess) {
                refreshDrafts()
                refreshPublishedPosts()
            }
        }
    }

    fun refreshPublishedPosts() {
        if (!_uiState.value.auth.isAuthenticated) {
            _uiState.update { it.copy(publishedPosts = emptyList(), publishedPostsLoading = false, publishedPostsError = null) }
            return
        }
        _uiState.update { it.copy(publishedPostsLoading = true, publishedPostsError = null) }
        viewModelScope.launch {
            val result = api.fetchRecentPosts(_uiState.value.settings, _uiState.value.auth.accessToken)
            _uiState.update {
                result.fold(
                    onSuccess = { posts -> it.copy(publishedPosts = posts, publishedPostsLoading = false, publishedPostsError = null) },
                    onFailure = { err -> it.copy(publishedPosts = emptyList(), publishedPostsLoading = false, publishedPostsError = err.message ?: "Unable to fetch published posts", statusMessage = "Fetch published posts failed: ${err.message}") }
                )
            }
        }
    }

    fun importPublishedPost(post: Draft) {
        val imported = draftRepo.importRemoteDraft(post)
        refreshDrafts()
        _uiState.update { it.copy(selectedDraft = imported, markdownWordCount = wordCount(imported.body), readingTimeMinutes = readingTime(wordCount(imported.body)), statusMessage = "Imported published post into local drafts") }
    }

    fun openPublishedPostInEditor(post: Draft) {
        val local = linkedLocalDraft(post.postId)
        val selected = local ?: post.copy(status = DraftStatus.DRAFT)
        _uiState.update {
            val words = wordCount(selected.body)
            it.copy(selectedDraft = selected, markdownWordCount = words, readingTimeMinutes = readingTime(words), statusMessage = if (local != null) "Opened linked local draft in editor" else "Opened remote post in editor (import to save locally)")
        }
    }

    fun republishUpdate(post: Draft) {
        if (!ensureAuthenticated("Sign in to publish updates.")) return
        val local = linkedLocalDraft(post.postId) ?: draftRepo.importRemoteDraft(post)
        _uiState.update { it.copy(selectedDraft = local) }
        publishPost()
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

    private fun ensureAuthenticated(message: String): Boolean {
        if (_uiState.value.auth.isAuthenticated) return true
        _uiState.update { it.copy(statusMessage = message) }
        return false
    }

    private fun normalizeMe(me: String): String {
        val trimmed = me.trim()
        if (trimmed.isBlank()) return "https://micro.blog"
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    private fun mapAiError(err: Throwable): String = when (err) {
        is com.example.microblogwriter.ai.AiReviewError.Network -> "Network error. Check your connection and provider URL."
        is com.example.microblogwriter.ai.AiReviewError.Authentication -> err.message ?: "Authentication failed"
        is com.example.microblogwriter.ai.AiReviewError.RateLimited -> err.message ?: "Rate limit reached"
        is com.example.microblogwriter.ai.AiReviewError.MissingConfiguration -> err.message ?: "Missing AI configuration"
        is com.example.microblogwriter.ai.AiReviewError.Provider -> err.message ?: "Provider request failed"
        else -> err.message ?: "Unknown error"
    }

    private fun linkedLocalDraft(postId: String?): Draft? {
        if (postId.isNullOrBlank()) return null
        return _uiState.value.drafts.firstOrNull { it.postId == postId }
    }

    private fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    private fun readingTime(words: Int): Int = max(1, words / 220)

    sealed interface UiEvent {
        data object NavigateToPosts : UiEvent
        data class PromptOpenInBrowser(val url: String) : UiEvent
    }
}
