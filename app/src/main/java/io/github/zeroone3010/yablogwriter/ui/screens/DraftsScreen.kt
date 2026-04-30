package io.github.zeroone3010.yablogwriter.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.zeroone3010.yablogwriter.domain.AppUiState
import io.github.zeroone3010.yablogwriter.domain.Draft
import io.github.zeroone3010.yablogwriter.domain.DraftStatus
import io.github.zeroone3010.yablogwriter.domain.TimestampFormat
import io.github.zeroone3010.yablogwriter.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun DraftsScreen(
    uiState: AppUiState,
    vm: AppViewModel,
    onOpenEditor: () -> Unit = {},
    onRequireAuth: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val unpublishedDrafts = uiState.drafts.filter { it.status != DraftStatus.PUBLISHED }.filter {
        it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
    }
    val localPublishedPosts = uiState.drafts.filter { it.status == DraftStatus.PUBLISHED }
    val publishedPosts = (uiState.publishedPosts + localPublishedPosts)
        .distinctBy { postIdentityKey(it) }
        .filter { it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true) }
    val uriHandler = LocalUriHandler.current

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
                TextButton(onClick = onRequireAuth) { Text("Account") }
            }
        }
        uiState.publishedPostsError?.let { Text("Published fetch error: $it") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Unpublished posts", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    if (unpublishedDrafts.isEmpty()) {
                        Text("No unpublished posts.")
                    }
                }
            }
            items(unpublishedDrafts, key = { it.id }) { draft ->
                DraftCard(
                    draft = draft,
                    timestampFormat = uiState.settings.timestampFormat,
                    onOpen = {
                        vm.selectDraft(draft.id)
                        onOpenEditor()
                    },
                    onDelete = { vm.deleteDraft(draft.id) }
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Published posts", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    if (publishedPosts.isEmpty()) {
                        Text("No published posts.")
                    }
                }
            }
            items(publishedPosts, key = { postIdentityKey(it) }) { post ->
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
                            OutlinedButton(
                                onClick = { post.postId?.takeIf { it.startsWith("http") }?.let(uriHandler::openUri) },
                                enabled = post.postId?.startsWith("http") == true
                            ) { Text("Open in browser") }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun DraftCard(
    draft: Draft,
    timestampFormat: TimestampFormat,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(draft.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(draft.title.ifBlank { "Untitled post" }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Last updated ${formatTimestamp(draft.updated, timestampFormat)} · ${draftStateSubtitle(draft)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(modifier = Modifier.wrapContentSize()) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DraftTag("Draft")
                DraftTag("Local")
                if (draft.categories.isEmpty()) {
                    DraftTag("No category")
                }
            }
        }
    }
}

@Composable
private fun DraftTag(label: String) {
    Card {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun draftStateSubtitle(draft: Draft): String = when (draft.status) {
    DraftStatus.PENDING_PUBLISH -> "Pending publish"
    DraftStatus.PENDING_UPLOAD -> "Pending upload"
    DraftStatus.DRAFT -> if (!draft.postId.isNullOrBlank()) "Linked draft" else "Draft"
    DraftStatus.PUBLISHED -> "Published"
}

private fun publishedBadgeLabel(post: Draft): String = when {
    post.postId.isNullOrBlank() -> "Published (No ID)"
    else -> "Published Remote"
}

private fun formatTimestamp(instant: Instant, timestampFormat: TimestampFormat): String {
    val zoned = instant.atZone(ZoneId.systemDefault()).withSecond(0).withNano(0)
    return when (timestampFormat) {
        TimestampFormat.ISO_24H -> zoned.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        TimestampFormat.DMY_24H -> zoned.format(DateTimeFormatter.ofPattern("d.M.yyyy HH:mm"))
        TimestampFormat.MDY_12H -> zoned.format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(Locale.US)
        )
    }
}

private fun postIdentityKey(draft: Draft): String = draft.postId?.takeIf { it.isNotBlank() } ?: draft.id
