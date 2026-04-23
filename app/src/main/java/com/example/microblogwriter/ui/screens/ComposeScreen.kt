package com.example.microblogwriter.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.microblogwriter.domain.AppUiState
import com.example.microblogwriter.domain.ImageUploadItem
import com.example.microblogwriter.domain.LinkDialogState
import com.example.microblogwriter.domain.UploadStatus
import com.example.microblogwriter.ui.AppViewModel
import com.example.microblogwriter.ui.editor.LinkInsertionRequest
import com.example.microblogwriter.ui.editor.findMarkdownImageAtSelection
import com.example.microblogwriter.ui.editor.insertInlineAtSelection
import com.example.microblogwriter.ui.editor.insertLinkTemplate
import com.example.microblogwriter.ui.editor.prefixSelectedLines
import com.example.microblogwriter.ui.editor.replaceMarkdownImageAltText
import com.example.microblogwriter.ui.editor.wrapInCodeBlock
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(uiState: AppUiState, vm: AppViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var imageAltEditorOpen by remember { mutableStateOf(false) }
    var markdownAltText by remember { mutableStateOf("") }

    var editorValue by remember(uiState.selectedDraft.id) {
        mutableStateOf(TextFieldValue(uiState.selectedDraft.body, TextRange(uiState.selectedDraft.body.length)))
    }

    val pickMultiplePhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        vm.queueImages(uris.map(Uri::toString))
    }

    val pickMultipleFiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        vm.queueImages(uris.map(Uri::toString))
    }

    val captureImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        if (success && capturedUri != null) {
            vm.queueImages(listOf(capturedUri.toString()))
        }
        pendingCameraUri = null
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
        Text("Writing-first compose")
        OutlinedTextField(
            value = uiState.selectedDraft.title,
            onValueChange = vm::editTitle,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.selectedDraft.categories.joinToString(", "),
            onValueChange = vm::editCategories,
            label = { Text("Categories (always visible)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            uiState.categoryHistory.take(4).forEach { category ->
                FilterChip(
                    selected = uiState.selectedDraft.categories.contains(category),
                    onClick = {
                        val next = (uiState.selectedDraft.categories + category).distinct().joinToString(",")
                        vm.editCategories(next)
                    },
                    label = { Text(category) }
                )
            }
        }

        if (uiState.previewMode) {
            Text("Preview")
            Divider()
            Text(uiState.selectedDraft.body)
        } else {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = {
                        val mutation = prefixSelectedLines(editorValue.text, editorValue.selection.start, editorValue.selection.end, "# ")
                        editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart, mutation.selectionEnd))
                        vm.editBody(mutation.text)
                    }) { Text("H1") }
                    Button(onClick = {
                        vm.requestLinkInsertion(
                            body = editorValue.text,
                            selectionStart = editorValue.selection.start,
                            selectionEnd = editorValue.selection.end,
                            clipboardText = clipboardManager.getText()?.text
                        )
                    }) { Text("Link") }
                    Button(onClick = {
                        val mutation = insertInlineAtSelection(
                            editorValue.text,
                            editorValue.selection.start,
                            editorValue.selection.end,
                            "![alt text](https://)"
                        )
                        editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart))
                        vm.editBody(mutation.text)
                    }) { Text("Image") }
                    Button(onClick = {
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
                    }) { Text("Edit image alt") }
                    Button(onClick = {
                        val mutation = prefixSelectedLines(editorValue.text, editorValue.selection.start, editorValue.selection.end, "> ")
                        editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart, mutation.selectionEnd))
                        vm.editBody(mutation.text)
                    }) { Text("Quote") }
                    Button(onClick = {
                        val mutation = wrapInCodeBlock(editorValue.text, editorValue.selection.start, editorValue.selection.end)
                        editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart, mutation.selectionEnd))
                        vm.editBody(mutation.text)
                    }) { Text("Code") }
                    Button(onClick = {
                        val mutation = insertInlineAtSelection(
                            editorValue.text,
                            editorValue.selection.start,
                            editorValue.selection.end,
                            "<!--more-->"
                        )
                        editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart))
                        vm.editBody(mutation.text)
                    }) { Text("<!--more-->") }
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
                editorValue = TextFieldValue(mutation.text, TextRange(mutation.selectionStart, mutation.selectionEnd))
                vm.editBody(mutation.text)
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
                    Button(onClick = { imageAltEditorOpen = false }) { Text("Cancel") }
                }
            )
        }

        Text("Words: ${uiState.markdownWordCount} • Reading time: ${uiState.readingTimeMinutes} min")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = vm::insertMoreTag) { Text("Insert <!--more-->") }
            Button(onClick = vm::togglePreview) { Text(if (uiState.previewMode) "Edit" else "Preview") }
        }

        Text("Image picker and upload queue")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                pickMultiplePhotos.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) { Text("Pick photos") }
            Button(onClick = { pickMultipleFiles.launch("image/*") }) { Text("Pick files") }
            Button(onClick = {
                val targetUri = createCameraImageUri(context)
                pendingCameraUri = targetUri
                captureImage.launch(targetUri)
            }) { Text("Capture") }
        }

        ImageUploadQueue(
            queue = uiState.imageUploadQueue,
            onAltTextChange = vm::updateUploadAltText,
            onRemove = vm::removeUploadItem
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = vm::uploadQueuedImages, enabled = uiState.imageUploadQueue.isNotEmpty()) { Text("Upload queue") }
            Button(onClick = vm::insertUploadedImagesMarkdown, enabled = uiState.imageUploadQueue.any { it.status == UploadStatus.SUCCEEDED }) {
                Text("Insert uploaded markdown")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = vm::saveDraft) { Text("Save Draft") }
            Button(onClick = vm::runAiReview) { Text("AI Review") }
            Button(onClick = vm::publishPost) { Text("Publish") }
        }
        Text("AI disclosure: When you tap AI Review, this draft content is sent to your configured provider.")

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

        uiState.statusMessage?.let { Text(it) }
    }
}

@Composable
private fun ImageUploadQueue(
    queue: List<ImageUploadItem>,
    onAltTextChange: (String, String) -> Unit,
    onRemove: (String) -> Unit
) {
    if (queue.isEmpty()) {
        Text("No images selected yet.")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        queue.forEach { item ->
            Surface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.localUri)
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
                        UploadStatus.QUEUED -> Text("Queued")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRemove(item.id) }) { Text("Remove") }
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
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun createCameraImageUri(context: Context): Uri {
    val imageDir = File(context.cacheDir, "images").apply { mkdirs() }
    val imageFile = File.createTempFile("capture_", ".jpg", imageDir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}
