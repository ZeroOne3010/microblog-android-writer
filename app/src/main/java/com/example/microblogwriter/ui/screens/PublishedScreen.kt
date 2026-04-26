package com.example.microblogwriter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.microblogwriter.domain.AppUiState
import com.example.microblogwriter.domain.Draft
import com.example.microblogwriter.ui.AppViewModel

@Composable
fun PublishedScreen(uiState: AppUiState, vm: AppViewModel, onRequireAuth: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = vm::refreshPublishedPosts, enabled = uiState.auth.isAuthenticated && !uiState.publishedPostsLoading) {
                Text(if (uiState.publishedPostsLoading) "Fetching..." else "Fetch recent posts")
            }
            if (!uiState.auth.isAuthenticated) {
                OutlinedButton(onClick = onRequireAuth) { Text("Account") }
            }
        }
        if (!uiState.auth.isAuthenticated) {
            Text("You need to sign in from Account settings to fetch/import/republish remote posts.")
        }
        uiState.publishedPostsError?.let { Text("Error: $it") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(uiState.publishedPosts, key = { it.postId ?: it.id }) { post ->
                PublishedPostCard(
                    post = post,
                    onImport = { vm.importPublishedPost(post) },
                    onOpenInEditor = { vm.openPublishedPostInEditor(post) },
                    onRepublish = { vm.republishUpdate(post) },
                    enabled = uiState.auth.isAuthenticated
                )
            }
        }
    }
}

@Composable
private fun PublishedPostCard(
    post: Draft,
    onImport: () -> Unit,
    onOpenInEditor: () -> Unit,
    onRepublish: () -> Unit,
    enabled: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(post.title.ifBlank { "Untitled post" })
            Text(post.postId ?: "No post ID")
            Text(
                if (post.categories.isEmpty()) "Categories: (none)"
                else "Categories: ${post.categories.joinToString()}"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onImport, enabled = enabled) { Text("Import locally") }
                TextButton(onClick = onOpenInEditor, enabled = enabled) { Text("Open in editor") }
            }
            Button(onClick = onRepublish, enabled = enabled) { Text("Republish update") }
        }
    }
}
