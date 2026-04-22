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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.microblogwriter.domain.AppUiState
import com.example.microblogwriter.ui.AppViewModel

@Composable
fun DraftsScreen(uiState: AppUiState, vm: AppViewModel) {
    var query by remember { mutableStateOf("") }
    val drafts = uiState.drafts.filter {
        it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search drafts") }
        )
        Button(onClick = vm::refreshDrafts) { Text("Refresh") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(drafts, key = { it.id }) { draft ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { vm.selectDraft(draft.id) }.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(draft.title.ifBlank { "Untitled draft" })
                        Text("Updated: ${draft.updated}")
                        Text("Categories: ${draft.categories.joinToString()}")
                        Text("Status: ${draft.status}")
                    }
                    Button(onClick = { vm.deleteDraft(draft.id) }) { Text("Delete") }
                }
            }
        }
    }
}
