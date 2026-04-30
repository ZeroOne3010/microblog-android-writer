package io.github.zeroone3010.yablogwriter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.zeroone3010.yablogwriter.ai.AiReviewClient
import io.github.zeroone3010.yablogwriter.auth.AuthConfig
import io.github.zeroone3010.yablogwriter.auth.AuthRepository
import io.github.zeroone3010.yablogwriter.auth.AuthState
import io.github.zeroone3010.yablogwriter.auth.MicroblogAuthApi
import io.github.zeroone3010.yablogwriter.auth.PendingAuthSession
import io.github.zeroone3010.yablogwriter.data.MarkdownDraftRepository
import io.github.zeroone3010.yablogwriter.data.SettingsRepository
import io.github.zeroone3010.yablogwriter.domain.AppUiState
import io.github.zeroone3010.yablogwriter.domain.Draft
import io.github.zeroone3010.yablogwriter.domain.DraftStatus
import io.github.zeroone3010.yablogwriter.domain.ImageUploadItem
import io.github.zeroone3010.yablogwriter.domain.LinkDialogState
import io.github.zeroone3010.yablogwriter.domain.SettingsState
import io.github.zeroone3010.yablogwriter.domain.UploadStatus
import io.github.zeroone3010.yablogwriter.network.MicroblogApi
import io.github.zeroone3010.yablogwriter.ui.editor.buildLinkInsertionRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val _events = Channel<UiEvent>(capacity = Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private var lastPublishedPostId: String? = null
    private var lastPublishedPermalink: String? = null
    private var autosaveJob: Job? = null
    private var autosaveVersion: Long = 0L

    companion object {
        private const val AUTOSAVE_DEBOUNCE_MS = 1200L
    }

    init {
        refreshDrafts()
        refreshPublishedPosts()
        refreshBlogCategories()
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
            if (result.isSuccess) {
                refreshBlogCategories()
            }
        }
    }

    fun logout() {
        authRepo.clear()
        pendingAuthSession = null
        _uiState.update {
            it.copy(
                auth = AuthState(),
                blogCategories = emptyList(),
                blogCategoriesLoading = false,
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

    fun createNewPost() {
        cancelPendingAutosave()
        val created = draftRepo.createDraft()
        refreshDrafts()
        _uiState.update {
            val words = wordCount(created.body)
            it.copy(
                selectedDraft = created,
                markdownWordCount = words,
                readingTimeMinutes = readingTime(words),
                statusMessage = "New post created"
            )
        }
    }

    fun togglePreview() {
        _uiState.update { it.copy(previewMode = !it.previewMode) }
    }

    fun requestLinkInsertion(body: String, selectionStart: Int, selectionEnd: Int, clipboardText: String?, asWebmention: Boolean = false) {
        val request = buildLinkInsertionRequest(body, selectionStart, selectionEnd, clipboardText)
        _uiState.update {
            it.copy(
                linkDialogState = LinkDialogState(
                    selectionStart = request.selectionStart,
                    selectionEnd = request.selectionEnd,
                    selectedText = request.selectedText,
                    initialUrl = request.initialUrl,
                    asWebmention = asWebmention
                )
            )
        }
    }

    fun dismissLinkDialog() {
        _uiState.update { it.copy(linkDialogState = null) }
    }

    fun insertMoreTag() {
        updateDraft {
            val withoutMore = body.replace("<!--more-->", "")
            val suffix = if (withoutMore.endsWith("\n") || withoutMore.isBlank()) "" else "\n"
            copy(body = "$withoutMore$suffix<!--more-->")
        }
    }

    fun queueImages(localUris: List<String>) {
        if (!ensureAuthenticated("Sign in to upload images.")) return
        val selectedUri = localUris.firstOrNull() ?: return
        _uiState.update { state ->
            val inFlightUploads = state.imageUploadQueue.filter { it.status == UploadStatus.UPLOADING }
            state.copy(
                imageUploadQueue = inFlightUploads + ImageUploadItem(localUri = selectedUri),
                statusMessage = "Selected image"
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
        val queue = _uiState.value.imageUploadQueue
        val selected = queue.firstOrNull { it.status == UploadStatus.QUEUED || it.status == UploadStatus.FAILED }
        if (selected == null && queue.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "No image selected") }
            return
        }
        if (selected == null && queue.any { it.status == UploadStatus.UPLOADING }) {
            _uiState.update { it.copy(statusMessage = "Image upload already in progress") }
            return
        }
        if (selected == null && queue.any { it.status == UploadStatus.SUCCEEDED }) {
            _uiState.update { it.copy(statusMessage = "Image already uploaded") }
            return
        }
        if (selected == null) return

        _uiState.update { state ->
            state.copy(imageUploadQueue = state.imageUploadQueue.map { queued ->
                if (queued.id == selected.id) queued.copy(status = UploadStatus.UPLOADING, progressPercent = 0, errorMessage = null)
                else queued
            })
        }

        viewModelScope.launch {
            val snapshot = _uiState.value.imageUploadQueue.firstOrNull { it.id == selected.id } ?: return@launch
            val token = _uiState.value.auth.accessToken
            val result = api.uploadImage(snapshot.localUri, snapshot.altText, _uiState.value.settings, token) { percent ->
                _uiState.update { state ->
                    state.copy(imageUploadQueue = state.imageUploadQueue.map { queued ->
                        if (queued.id == selected.id) queued.copy(progressPercent = percent, status = UploadStatus.UPLOADING) else queued
                    })
                }
            }

            _uiState.update { state ->
                result.fold(
                    onSuccess = { url ->
                        state.copy(
                            imageUploadQueue = state.imageUploadQueue.map { queued ->
                                if (queued.id == selected.id) queued.copy(status = UploadStatus.SUCCEEDED, progressPercent = 100, uploadedUrl = url, errorMessage = null)
                                else queued
                            },
                            statusMessage = "Uploaded image"
                        )
                    },
                    onFailure = { err ->
                        state.copy(
                            imageUploadQueue = state.imageUploadQueue.map { queued ->
                                if (queued.id == selected.id) queued.copy(status = UploadStatus.FAILED, progressPercent = 0, uploadedUrl = null, errorMessage = err.message ?: "Upload failed")
                                else queued
                            },
                            statusMessage = "Image upload failed"
                        )
                    }
                )
            }
        }
    }

    fun insertUploadedImagesMarkdown() {
        val uploaded = _uiState.value.imageUploadQueue.lastOrNull { it.status == UploadStatus.SUCCEEDED && !it.uploadedUrl.isNullOrBlank() }
        if (uploaded == null) {
            _uiState.update { it.copy(statusMessage = "No uploaded image available to insert") }
            return
        }
        val markdownBlock = "![${uploaded.altText.ifBlank { "image" }}](${uploaded.uploadedUrl})"
        updateDraft { copy(body = if (body.isBlank()) markdownBlock else "$body\n$markdownBlock") }
        _uiState.update { it.copy(statusMessage = "Inserted uploaded image") }
    }

    fun saveDraft() {
        cancelPendingAutosave()
        val saved = draftRepo.saveDraft(_uiState.value.selectedDraft)
        _uiState.update { it.copy(selectedDraft = saved, statusMessage = "Post saved locally") }
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
            _uiState.update { it.copy(aiReviewInProgress = true) }
            val result = ai.review(state.settings.aiProviderBaseUrl, state.settings.aiApiKey, state.settings.aiModel, prompt)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { output -> current.copy(aiReviewOutput = output, aiReviewInProgress = false, statusMessage = "AI review complete") },
                    onFailure = { err ->
                        val details = mapAiError(err)
                        current.copy(
                            aiReviewOutput = "AI review failed.\n\n$details",
                            aiReviewInProgress = false,
                            statusMessage = "AI review failed. See AI review output for details."
                        )
                    }
                )
            }
        }
    }

    fun publishPost() {
        if (!ensureAuthenticated("Sign in to publish posts.")) return
        cancelPendingAutosave()
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
                    _events.send(UiEvent.NavigateToPosts)
                    publishResponse.permalink
                        ?.takeIf(String::isNotBlank)
                        ?.let { permalink -> _events.send(UiEvent.PromptOpenInBrowser(permalink)) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = "Publish failed, local draft kept: ${err.message}") }
                }
            )
            refreshDrafts()
            if (result.isSuccess) {
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

    fun refreshBlogCategories() {
        if (!_uiState.value.auth.isAuthenticated) {
            _uiState.update { it.copy(blogCategories = emptyList(), blogCategoriesLoading = false) }
            return
        }
        val requestToken = _uiState.value.auth.accessToken
        _uiState.update { it.copy(blogCategoriesLoading = true) }
        viewModelScope.launch {
            val result = api.fetchCategories(_uiState.value.settings, requestToken)
            _uiState.update { current ->
                if (!current.auth.isAuthenticated || current.auth.accessToken != requestToken) {
                    return@update current.copy(blogCategoriesLoading = false)
                }
                result.fold(
                    onSuccess = { categories -> current.copy(blogCategories = categories, blogCategoriesLoading = false) },
                    onFailure = { err ->
                        current.copy(
                            blogCategoriesLoading = false,
                            statusMessage = "Could not load categories: ${err.message}"
                        )
                    }
                )
            }
        }
    }

    fun toggleCategory(category: String) {
        val current = _uiState.value.selectedDraft.categories
        val next = if (current.contains(category)) current - category else current + category
        updateDraft { copy(categories = next.distinct()) }
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

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun updateDraft(update: Draft.() -> Draft) {
        val updated = _uiState.value.selectedDraft.update().copy(status = DraftStatus.DRAFT)
        _uiState.update {
            val words = wordCount(updated.body)
            it.copy(selectedDraft = updated, markdownWordCount = words, readingTimeMinutes = readingTime(words))
        }
        scheduleAutosave(updated)
    }

    private fun scheduleAutosave(draft: Draft) {
        autosaveJob?.cancel()
        val version = ++autosaveVersion
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            if (version != autosaveVersion) return@launch
            val current = _uiState.value.selectedDraft
            if (current.id == draft.id && current.status == DraftStatus.PUBLISHED && !current.postId.isNullOrBlank()) {
                return@launch
            }
            val (saved, drafts, categories) = withContext(Dispatchers.IO) {
                val savedDraft = draftRepo.saveDraft(draft)
                val allDrafts = draftRepo.listDrafts()
                val allCategories = allDrafts.flatMap { it.categories }.distinct().sorted()
                Triple(savedDraft, allDrafts, allCategories)
            }
            _uiState.update {
                val selectedDraft = if (it.selectedDraft.id == saved.id) saved else it.selectedDraft
                val words = wordCount(selectedDraft.body)
                it.copy(
                    drafts = drafts,
                    categoryHistory = categories,
                    selectedDraft = selectedDraft,
                    markdownWordCount = words,
                    readingTimeMinutes = readingTime(words)
                )
            }
        }
    }

    private fun cancelPendingAutosave() {
        autosaveVersion++
        autosaveJob?.cancel()
        autosaveJob = null
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
        is io.github.zeroone3010.yablogwriter.ai.AiReviewError.Network -> "Network error. Check your connection and provider URL."
        is io.github.zeroone3010.yablogwriter.ai.AiReviewError.Authentication -> err.message ?: "Authentication failed"
        is io.github.zeroone3010.yablogwriter.ai.AiReviewError.RateLimited -> err.message ?: "Rate limit reached"
        is io.github.zeroone3010.yablogwriter.ai.AiReviewError.MissingConfiguration -> err.message ?: "Missing AI configuration"
        is io.github.zeroone3010.yablogwriter.ai.AiReviewError.Provider -> err.message ?: "Provider request failed"
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
