package com.example.microblogwriter.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.microblogwriter.domain.AppUiState
import com.example.microblogwriter.domain.Draft
import com.example.microblogwriter.domain.DraftStatus
import com.example.microblogwriter.ui.AppViewModel
import com.example.microblogwriter.ui.theme.destructiveButtonColors

@Composable
fun DraftsScreen(
    uiState: AppUiState,
    vm: AppViewModel,
    onOpenEditor: () -> Unit = {},
    onRequireAuth: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var renamingDraft by remember { mutableStateOf<Draft?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val drafts = uiState.drafts.filter {
        it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
    }
    val publishedPosts = uiState.publishedPosts.filter {
        it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search posts") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                vm.createNewPost()
                onOpenEditor()
            }) { Text("New Post") }
            OutlinedButton(onClick = vm::refreshDrafts) { Text("Refresh") }
            OutlinedButton(onClick = vm::refreshPublishedPosts, enabled = uiState.auth.isAuthenticated && !uiState.publishedPostsLoading) {
                Text(if (uiState.publishedPostsLoading) "Fetching..." else "Fetch published")
            }
            if (!uiState.auth.isAuthenticated) {
                TextButton(onClick = onRequireAuth) { Text("Sign in") }
            }
        }
        uiState.publishedPostsError?.let { Text("Published fetch error: $it") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Local posts")
            }
            items(drafts, key = { it.id }) { draft ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.selectDraft(draft.id)
                            onOpenEditor()
                        }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(draft.title.ifBlank { "Untitled post" })
                        Text("Updated: ${draft.updated}")
                        Text("Categories: ${draft.categories.joinToString()}")
                        Text("Status: ${draft.status}")
                        Text("Label: ${draftBadgeLabel(draft)}")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            renamingDraft = draft
                            renameValue = draft.title.ifBlank { draft.id }
                        }) { Text("Rename") }
                        OutlinedButton(onClick = { vm.duplicateDraft(draft.id) }) { Text("Duplicate") }
                        Button(
                            onClick = { vm.deleteDraft(draft.id) },
                            colors = destructiveButtonColors()
                        ) { Text("Delete") }
                    }
                }
            }

            item {
                Text("Published posts")
            }
            items(publishedPosts, key = { it.postId ?: it.id }) { post ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(post.title.ifBlank { "Untitled post" })
                        Text("Categories: ${post.categories.joinToString().ifBlank { "(none)" }}")
                        Text("Label: ${publishedBadgeLabel(post)}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.importPublishedPost(post) }, enabled = uiState.auth.isAuthenticated) {
                                Text("Import locally")
                            }
                            TextButton(onClick = {
                                vm.openPublishedPostInEditor(post)
                                onOpenEditor()
                            }) { Text("Open in editor") }
                        }
                    }
                }
            }
        }
    }

    if (renamingDraft != null) {
        AlertDialog(
            onDismissRequest = { renamingDraft = null },
            title = { Text("Rename post") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("New title or slug") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renamingDraft?.let { vm.renameDraft(it.id, renameValue) }
                    renamingDraft = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingDraft = null }) { Text("Cancel") }
            }
        )
    }
}

private fun draftBadgeLabel(draft: Draft): String = when {
    draft.status == DraftStatus.PENDING_PUBLISH -> "Pending Publish"
    !draft.postId.isNullOrBlank() -> "Published Linked"
    else -> "Local Post"
}

private fun publishedBadgeLabel(post: Draft): String = when {
    post.postId.isNullOrBlank() -> "Published (No ID)"
    else -> "Published Remote"
}
