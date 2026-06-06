package io.github.zeroone3010.yablogwriter.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
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
                item {
                    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.35f
                    val postCardContainerColor = MaterialTheme.colorScheme.surface.copy(
                        alpha = if (isDarkTheme) 0.98f else 0.92f
                    )
                    val postCardBorderColor = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (isDarkTheme) 0.24f else 0.16f
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .clickable {
                                vm.createNewPost()
                                onOpenEditor()
                            },
                        colors = CardDefaults.cardColors(containerColor = postCardContainerColor),
                        border = BorderStroke(0.6.dp, postCardBorderColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    append("No posts yet. ")
                                    pushStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold))
                                    append("Write one!")
                                    pop()
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Your drafts will appear here.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                items(filteredPosts, key = { it.id }) { post ->
                    DraftCard(
                        draft = post,
                        timestampFormat = uiState.settings.timestampFormat,
                        locationLabel = if (post.status == DraftStatus.PUBLISHED) publishedLocationLabel(post) else "Local",
                        browserUrl = post.postId?.takeIf { post.status == DraftStatus.PUBLISHED && it.startsWith("http") },
                        onOpen = {
                            vm.selectDraft(post.id)
                            onOpenEditor()
                        },
                        onOpenBrowser = { url -> uriHandler.openUri(url) },
                        onDelete = if (post.status == DraftStatus.PUBLISHED) null else ({ vm.deleteDraft(post.id) })
                    )
                }
            }
        }
    }

}

@Composable
private fun DraftCard(
    draft: Draft,
    timestampFormat: TimestampFormat,
    locationLabel: String,
    browserUrl: String?,
    onOpen: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var menuExpanded by remember(draft.id) { mutableStateOf(false) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.35f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = if (isDarkTheme) 0.98f else 0.92f
            )
        ),
        border = BorderStroke(
            width = 0.6.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (isDarkTheme) 0.24f else 0.16f
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TinyMetadataIcon(Icons.Outlined.Description, tint = metadataColor)
                        Text(
                            text = "${draftStateSubtitle(draft)} · $locationLabel",
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
                if (browserUrl != null || onDelete != null) {
                    Column(modifier = Modifier.wrapContentSize()) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (browserUrl != null) {
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenBrowser(browserUrl)
                                    }
                                )
                            }
                            if (onDelete != null) {
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
                }
            }

            val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    TinyMetadataIcon(Icons.Outlined.AccessTime, tint = metadataColor)
                    Text(
                        text = "Updated ${formatTimestamp(draft.updated, timestampFormat)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = metadataColor
                    )
                }
                DraftTag(
                    label = draft.categories.firstOrNull() ?: "No category",
                    extraCount = (draft.categories.size - 1).coerceAtLeast(0)
                )
            }
        }
    }
}

@Composable
private fun DraftTag(label: String, extraCount: Int = 0) {
    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    Card {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .widthIn(max = 180.dp)
        ) {
            TinyMetadataIcon(Icons.Outlined.Folder, tint = metadataColor)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = metadataColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (extraCount > 0) {
                Text(
                    text = "+$extraCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = metadataColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
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

private fun publishedLocationLabel(post: Draft): String = if (post.postId.isNullOrBlank()) "No remote ID" else "Remote"

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
