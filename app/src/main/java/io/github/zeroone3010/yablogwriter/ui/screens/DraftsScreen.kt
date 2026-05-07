package io.github.zeroone3010.yablogwriter.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
    onOpenEditor: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val filteredPosts = uiState.drafts.filter {
        it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
    }
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search posts") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    vm.createNewPost()
                    onOpenEditor()
                },
                modifier = Modifier.weight(1f)
            ) { Text("New Post") }
            OutlinedButton(onClick = vm::refreshDrafts, modifier = Modifier.weight(1f)) { Text("Refresh") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filteredPosts.isEmpty()) {
                item { Text("No posts yet. Write one!") }
            } else {
                items(filteredPosts, key = { it.id }) { post ->
                    if (post.status == DraftStatus.PUBLISHED) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(post.title.ifBlank { "Untitled post" })
                                Text("Categories: ${post.categories.joinToString().ifBlank { "(none)" }}")
                                Text("Label: ${publishedBadgeLabel(post)}")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        vm.selectDraft(post.id)
                                        onOpenEditor()
                                    }) { Text("Open in editor") }
                                    OutlinedButton(
                                        onClick = { post.postId?.takeIf { it.startsWith("http") }?.let(uriHandler::openUri) },
                                        enabled = post.postId?.startsWith("http") == true
                                    ) { Text("Open in browser") }
                                }
                            }
                        }
                    } else {
                        DraftCard(
                            draft = post,
                            timestampFormat = uiState.settings.timestampFormat,
                            onOpen = {
                                vm.selectDraft(post.id)
                                onOpenEditor()
                            },
                            onDelete = { vm.deleteDraft(post.id) }
                        )
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
                    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TinyMetadataIcon(Icons.Outlined.Description, tint = metadataColor)
                        Text(
                            text = "${draftStateSubtitle(draft)} · Local",
                            style = MaterialTheme.typography.labelMedium,
                            color = metadataColor
                        )
                    }
                    Text(
                        draft.title.ifBlank { "Untitled post" },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = draft.body.lineSequence().firstOrNull { it.isNotBlank() }
                            ?: "Start writing your thoughts…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = metadataColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

            val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TinyMetadataIcon(Icons.Outlined.AccessTime, tint = metadataColor)
                    Text(
                        text = "Updated ${formatTimestamp(draft.updated, timestampFormat)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = metadataColor
                    )
                }
                if (draft.categories.isEmpty()) {
                    DraftTag("No category")
                } else {
                    DraftTag(draft.categories.first())
                }
            }
        }
    }
}

@Composable
private fun DraftTag(label: String) {
    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    Card {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            TinyMetadataIcon(Icons.Outlined.Folder, tint = metadataColor)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = metadataColor
        )
        }
    }
}

@Composable
private fun TinyMetadataIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp).padding(top = 1.dp))
}

private fun draftStateSubtitle(draft: Draft): String = when (draft.status) {
    DraftStatus.PENDING_PUBLISH -> "Pending publish"
    DraftStatus.PENDING_UPLOAD -> "Pending upload"
    DraftStatus.DRAFT -> if (!draft.postId.isNullOrBlank()) "Linked draft" else "Draft"
    DraftStatus.PUBLISHED -> "Published"
}

private fun publishedBadgeLabel(post: Draft): String = if (post.postId.isNullOrBlank()) "Published (No ID)" else "Published"

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
