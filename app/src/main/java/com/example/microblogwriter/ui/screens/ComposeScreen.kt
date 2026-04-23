package com.example.microblogwriter.ui.screens

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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.microblogwriter.domain.AppUiState
import com.example.microblogwriter.domain.LinkDialogState
import com.example.microblogwriter.ui.AppViewModel
import com.example.microblogwriter.ui.editor.LinkInsertionRequest
import com.example.microblogwriter.ui.editor.insertInlineAtSelection
import com.example.microblogwriter.ui.editor.insertLinkTemplate
import com.example.microblogwriter.ui.editor.prefixSelectedLines
import com.example.microblogwriter.ui.editor.wrapInCodeBlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(uiState: AppUiState, vm: AppViewModel) {
    val altText = remember { mutableStateOf("") }
    val imageUrl = remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    var editorValue by remember(uiState.selectedDraft.id) {
        mutableStateOf(TextFieldValue(uiState.selectedDraft.body, TextRange(uiState.selectedDraft.body.length)))
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

        Text("Words: ${uiState.markdownWordCount} • Reading time: ${uiState.readingTimeMinutes} min")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = vm::insertMoreTag) { Text("Insert <!--more-->") }
            Button(onClick = vm::togglePreview) { Text(if (uiState.previewMode) "Edit" else "Preview") }
        }

        Text("Inline image upload")
        OutlinedTextField(
            value = imageUrl.value,
            onValueChange = { imageUrl.value = it },
            label = { Text("Image URL or picked URI") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = altText.value,
            onValueChange = { altText.value = it },
            label = { Text("Alt text") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { vm.insertMarkdownImage(imageUrl.value, altText.value) }) { Text("Insert image markdown") }
            Button(onClick = { vm.uploadImageAndInsert(imageUrl.value, altText.value) }) { Text("Upload + Insert") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = vm::saveDraft) { Text("Save Draft") }
            Button(onClick = vm::runAiReview) { Text("AI Review") }
            Button(onClick = vm::publishPost) { Text("Publish") }
        }

        if (uiState.aiReviewOutput.isNotBlank()) {
            Text("AI review (explicit step):")
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
