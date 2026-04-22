package com.example.microblogwriter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.microblogwriter.domain.AppUiState
import com.example.microblogwriter.ui.AppViewModel

@Composable
fun PublishedScreen(uiState: AppUiState, vm: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Published posts / remote editing placeholder")
        Text("Use this tab to fetch recent Micro.blog posts and map local files to remote IDs.")
        uiState.publishedPosts.forEach { post -> Text("• ${post.title} (${post.postId})") }
    }
}
