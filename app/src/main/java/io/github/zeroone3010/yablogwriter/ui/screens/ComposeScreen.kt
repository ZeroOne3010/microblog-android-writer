package io.github.zeroone3010.yablogwriter.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zeroone3010.yablogwriter.domain.AppUiState
import io.github.zeroone3010.yablogwriter.domain.ImageUploadItem
import io.github.zeroone3010.yablogwriter.domain.LinkDialogState
import io.github.zeroone3010.yablogwriter.domain.UploadStatus
import io.github.zeroone3010.yablogwriter.ui.AppViewModel
import io.github.zeroone3010.yablogwriter.ui.theme.destructiveButtonColors
import io.github.zeroone3010.yablogwriter.ui.editor.LinkInsertionRequest
import io.github.zeroone3010.yablogwriter.ui.editor.autoLinkInsertionRequest
import io.github.zeroone3010.yablogwriter.ui.editor.findMarkdownImageAtSelection
import io.github.zeroone3010.yablogwriter.ui.editor.insertInlineAtSelection
import io.github.zeroone3010.yablogwriter.ui.editor.insertLinkTemplate
import io.github.zeroone3010.yablogwriter.ui.editor.insertWebmentionLinkTemplate
import io.github.zeroone3010.yablogwriter.ui.editor.prefixSelectedLines
import io.github.zeroone3010.yablogwriter.ui.editor.removeAllMoreTags
import io.github.zeroone3010.yablogwriter.ui.editor.replaceMarkdownImageAltText
import io.github.zeroone3010.yablogwriter.ui.editor.wrapInCodeBlock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen(uiState: AppUiState, vm: AppViewModel, onRequireAuth: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var imageAltEditorOpen by remember { mutableStateOf(false) }
    var markdownAltText by remember { mutableStateOf("") }

    var editorValue by remember(uiState.selectedDraft.id) {
        mutableStateOf(TextFieldValue(uiState.selectedDraft.body, TextRange(uiState.selectedDraft.body.length)))
    }

    val pickSinglePhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { vm.queueImages(listOf(it.toString())) }
    }

    val pickSingleFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { vm.queueImages(listOf(it.toString())) }
    }

    LaunchedEffect(uiState.selectedDraft.id, uiState.selectedDraft.body) {
        if (editorValue.text != uiState.selectedDraft.body) {
            val cursor = editorValue.selection.end.coerceAtMost(uiState.selectedDraft.body.length)
            editorValue = TextFieldValue(uiState.selectedDraft.body, TextRange(cursor))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!uiState.auth.isAuthenticated) {
            Text("Sign in is required for publish/upload actions.")
            OutlinedButton(onClick = onRequireAuth) { Text("Go to account settings") }
        }
        OutlinedTextField(
            value = uiState.selectedDraft.title,
            onValueChange = vm::editTitle,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )

        val categories = if (uiState.blogCategories.isNotEmpty()) {
            uiState.blogCategories
        } else {
            uiState.categoryHistory
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Categories")
                TextButton(onClick = vm::refreshBlogCategories, enabled = uiState.auth.isAuthenticated && !uiState.blogCategoriesLoading) {
                    Text(if (uiState.blogCategoriesLoading) "Loading..." else "Refresh")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = uiState.selectedDraft.categories.contains(category),
                        onClick = { vm.toggleCategory(category) },
                        label = { Text(category) }
                    )
                }
            }
            if (categories.isEmpty()) {
                Text(
                    if (uiState.auth.isAuthenticated) "No existing categories returned by Micropub config."
                    else "Sign in to load categories from your Micro.blog."
                )
            }
        }

        if (uiState.previewMode) {
            Text("Preview")
            Divider()
            Text(uiState.selectedDraft.body)
        } else {
            Surface(modifier = Modifier.fillMaxWidth()) {
                val formattingButtonModifier = Modifier.heightIn(min = 36.dp)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        val mutation = prefixSelectedLines(editorValue.text, editorValue.selection.start, editorValue.selection.end, "# ")
                        editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart, mutation.selectionEnd))
                        vm.editBody(mutation.text)
                    }, modifier = formattingButtonModifier) { Text("H1") }
                    OutlinedButton(onClick = {
                        val auto = autoLinkInsertionRequest(editorValue.text, editorValue.selection.start, editorValue.selection.end, clipboardManager.getText()?.text)
                        if (auto != null) {
                            val mutation = insertLinkTemplate(editorValue.text, auto.first, auto.second)
                            editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart, mutation.selectionEnd))
                            vm.editBody(mutation.text)
                        } else {
                            vm.requestLinkInsertion(
                                body = editorValue.text,
                                selectionStart = editorValue.selection.start,
                                selectionEnd = editorValue.selection.end,
                                clipboardText = clipboardManager.getText()?.text
                            )
                        }
                    }, modifier = formattingButtonModifier) { Text("Link") }
                    OutlinedButton(onClick = {
                        val auto = autoLinkInsertionRequest(editorValue.text, editorValue.selection.start, editorValue.selection.end, clipboardManager.getText()?.text)
                        if (auto != null) {
                            val mutation = insertWebmentionLinkTemplate(editorValue.text, auto.first, auto.second)
                            editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart, mutation.selectionEnd))
                            vm.editBody(mutation.text)
                        } else {
                            vm.requestLinkInsertion(
                                body = editorValue.text,
                                selectionStart = editorValue.selection.start,
                                selectionEnd = editorValue.selection.end,
                                clipboardText = clipboardManager.getText()?.text,
                                asWebmention = true
                            )
                        }
                    }, modifier = formattingButtonModifier) { Text("Webmention") }
                    OutlinedButton(onClick = {
                        val mutation = insertInlineAtSelection(
                            editorValue.text,
                            editorValue.selection.start,
                            editorValue.selection.end,
                            "![alt text](https://)"
                        )
                        editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart))
                        vm.editBody(mutation.text)
                    }, modifier = formattingButtonModifier) { Text("Image") }
                    OutlinedButton(onClick = {
                        val match = findMarkdownImageAtSelection(
                            editorValue.text,
                            editorValue.selection.start,
                            editorValue.selection.end
                        )
                        if (match != null) {
                            markdownAltText = match.altText
                            imageAltEditorOpen = true
                        } else {
                            vm.editBody(editorValue.text)
                        }
                    }, modifier = formattingButtonModifier) { Text("Edit image alt") }
                    OutlinedButton(onClick = {
                        val mutation = prefixSelectedLines(editorValue.text, editorValue.selection.start, editorValue.selection.end, "> ")
                        editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart, mutation.selectionEnd))
                        vm.editBody(mutation.text)
                    }, modifier = formattingButtonModifier) { Text("Quote") }
                    OutlinedButton(onClick = {
                        val mutation = wrapInCodeBlock(editorValue.text, editorValue.selection.start, editorValue.selection.end)
                        editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart, mutation.selectionEnd))
                        vm.editBody(mutation.text)
                    }, modifier = formattingButtonModifier) { Text("Code") }
                    OutlinedButton(onClick = {
                        val originalText = editorValue.text
                        val strippedText = removeAllMoreTags(originalText)
                        val moreTag = "<!--more-->"
                        val removedBeforeSelectionStart = Regex(Regex.escape(moreTag))
                            .findAll(originalText.substring(0, editorValue.selection.start.coerceIn(0, originalText.length)))
                            .count() * moreTag.length
                        val removedBeforeSelectionEnd = Regex(Regex.escape(moreTag))
                            .findAll(originalText.substring(0, editorValue.selection.end.coerceIn(0, originalText.length)))
                            .count() * moreTag.length
                        val rebasedSelectionStart = (editorValue.selection.start - removedBeforeSelectionStart).coerceIn(0, strippedText.length)
                        val rebasedSelectionEnd = (editorValue.selection.end - removedBeforeSelectionEnd).coerceIn(0, strippedText.length)
                        val mutation = insertInlineAtSelection(
                            strippedText,
                            rebasedSelectionStart,
                            rebasedSelectionEnd,
                            moreTag
                        )
                        editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart))
                        vm.editBody(mutation.text)
                    }, modifier = formattingButtonModifier) { Text("<!--more-->") }
                }
            }

            OutlinedTextField(
                value = editorValue,
                onValueChange = {
                    editorValue = it
                    vm.editBody(it.text)
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 12,
                label = { Text("Markdown") }
            )
        }

        LinkInsertionDialog(
            dialogState = uiState.linkDialogState,
            onDismiss = vm::dismissLinkDialog,
            onInsert = { state, url ->
                val mutation = insertLinkTemplate(
                    editorValue.text,
                    LinkInsertionRequest(
                        selectionStart = state.selectionStart,
                        selectionEnd = state.selectionEnd,
                        selectedText = state.selectedText,
                        initialUrl = state.initialUrl
                    ),
                    url
                )
                val resolvedMutation = if (state.asWebmention) {
                    insertWebmentionLinkTemplate(
                        editorValue.text,
                        LinkInsertionRequest(
                            selectionStart = state.selectionStart,
                            selectionEnd = state.selectionEnd,
                            selectedText = state.selectedText,
                            initialUrl = state.initialUrl
                        ),
                        url
                    )
                } else mutation
                editorValue = TextFieldValue(resolvedMutation.text, TextRange(resolvedMutation.selectionStart, resolvedMutation.selectionEnd))
                vm.editBody(resolvedMutation.text)
                vm.dismissLinkDialog()
            }
        )

        if (imageAltEditorOpen) {
            val selectedImage = findMarkdownImageAtSelection(
                editorValue.text,
                editorValue.selection.start,
                editorValue.selection.end
            )
            AlertDialog(
                onDismissRequest = { imageAltEditorOpen = false },
                title = { Text("Edit image alt text") },
                text = {
                    OutlinedTextField(
                        value = markdownAltText,
                        onValueChange = { markdownAltText = it },
                        label = { Text("Alt text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (selectedImage != null) {
                            val mutation = replaceMarkdownImageAltText(editorValue.text, selectedImage, markdownAltText)
                            editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart, mutation.selectionEnd))
                            vm.editBody(mutation.text)
                        }
                        imageAltEditorOpen = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { imageAltEditorOpen = false }) { Text("Cancel") }
                }
            )
        }

        Text("Words: ${uiState.markdownWordCount} • Reading time: ${uiState.readingTimeMinutes} min")

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Image upload")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = {
                        pickSinglePhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Text("Pick photo") }
                    OutlinedButton(onClick = { pickSingleFile.launch("image/*") }) { Text("Pick file") }
                }

                ImageUploadQueue(
                    queue = uiState.imageUploadQueue,
                    onAltTextChange = vm::updateUploadAltText,
                    onRemove = vm::removeUploadItem
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = vm::uploadQueuedImages, enabled = uiState.auth.isAuthenticated && uiState.imageUploadQueue.isNotEmpty()) { Text("Upload image") }
                    TextButton(onClick = vm::insertUploadedImagesMarkdown, enabled = uiState.imageUploadQueue.any { it.status == UploadStatus.SUCCEEDED }) {
                        Text("Insert uploaded markdown")
                    }
                }
            }
        }

        val aiReviewAvailable = uiState.settings.aiEnabled && uiState.settings.aiApiKey.isNotBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = vm::saveDraft) { Text("Save Post") }
            if (aiReviewAvailable) {
                TextButton(onClick = vm::runAiReview) { Text("AI Review") }
            }
            Button(onClick = vm::publishPost, enabled = uiState.auth.isAuthenticated) { Text("Publish") }
        }
        if (uiState.aiReviewOutput.isNotBlank()) {
            Text("AI review (separate suggestions, never auto-applied):")
            OutlinedTextField(
                value = uiState.aiReviewOutput,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                label = { Text("Suggestions") }
            )
        }
    }
}

@Composable
private fun ImageUploadQueue(
    queue: List<ImageUploadItem>,
    onAltTextChange: (String, String) -> Unit,
    onRemove: (String) -> Unit
) {
    if (queue.isEmpty()) {
        Text("No image selected.")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        queue.forEach { item ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.localUri,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedTextField(
                        value = item.altText,
                        onValueChange = { onAltTextChange(item.id, it) },
                        label = { Text("Alt text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    when (item.status) {
                        UploadStatus.UPLOADING -> {
                            LinearProgressIndicator(progress = item.progressPercent / 100f, modifier = Modifier.fillMaxWidth())
                            Text("Uploading ${item.progressPercent}%")
                        }

                        UploadStatus.SUCCEEDED -> Text("Uploaded: ${item.uploadedUrl.orEmpty()}")
                        UploadStatus.FAILED -> Text("Error: ${item.errorMessage.orEmpty()}")
                        UploadStatus.QUEUED -> Text("Selected")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { onRemove(item.id) },
                            colors = destructiveButtonColors()
                        ) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkInsertionDialog(
    dialogState: LinkDialogState?,
    onDismiss: () -> Unit,
    onInsert: (LinkDialogState, String) -> Unit
) {
    if (dialogState == null) return

    var url by remember(dialogState) { mutableStateOf(dialogState.initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dialogState.selectedText.isNotBlank()) {
                    Text("Link text: ${dialogState.selectedText}")
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onInsert(dialogState, url) }, enabled = url.isNotBlank()) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
