package com.example.microblogwriter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.microblogwriter.domain.AppUiState
import com.example.microblogwriter.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(uiState: AppUiState, vm: AppViewModel) {
    val altText = remember { mutableStateOf("") }
    val imageUrl = remember { mutableStateOf("") }

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
            OutlinedTextField(
                value = uiState.selectedDraft.body,
                onValueChange = vm::editBody,
                modifier = Modifier.fillMaxWidth(),
                minLines = 12,
                label = { Text("Markdown") }
            )
        }

        Text("Words: ${uiState.markdownWordCount} • Reading time: ${uiState.readingTimeMinutes} min")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = vm::insertMoreTag) { Text("Insert <!--more-->") }
            Button(onClick = vm::togglePreview) { Text(if (uiState.previewMode) "Edit" else "Preview") }
        }

        Text("Inline image upload (MVP mock)")
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
        Button(onClick = { vm.insertMarkdownImage(imageUrl.value, altText.value) }) { Text("Insert image markdown") }

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
