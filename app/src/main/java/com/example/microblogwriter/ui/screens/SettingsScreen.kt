package com.example.microblogwriter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.microblogwriter.domain.AppTheme
import com.example.microblogwriter.domain.AppUiState
import com.example.microblogwriter.domain.SettingsState
import com.example.microblogwriter.ui.AppViewModel

@Composable
fun SettingsScreen(uiState: AppUiState, vm: AppViewModel) {
    var settings by remember(uiState.settings) { mutableStateOf(uiState.settings) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AI settings")
        RowSwitch("Enable AI review", settings.aiEnabled) { settings = settings.copy(aiEnabled = it) }
        OutlinedTextField(
            value = settings.aiProviderBaseUrl,
            onValueChange = { settings = settings.copy(aiProviderBaseUrl = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("AI provider base URL") }
        )
        OutlinedTextField(
            value = settings.aiApiKey,
            onValueChange = { settings = settings.copy(aiApiKey = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Provider API key") }
        )
        OutlinedTextField(
            value = settings.aiModel,
            onValueChange = { settings = settings.copy(aiModel = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Model name") }
        )
        OutlinedTextField(
            value = settings.aiPromptTemplate,
            onValueChange = { settings = settings.copy(aiPromptTemplate = it) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = { Text("Prompt template ({title}, {contents})") }
        )
        Text("Disclosure: Running AI review sends the current draft title/body to the configured AI provider.")

        Text("Micro.blog")
        OutlinedTextField(
            value = settings.microblogApiBaseUrl,
            onValueChange = { settings = settings.copy(microblogApiBaseUrl = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Micropub base URL") }
        )
        OutlinedTextField(
            value = settings.microblogAccessToken,
            onValueChange = { settings = settings.copy(microblogAccessToken = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Access token (Bearer)") }
        )
        OutlinedTextField(
            value = settings.microblogMediaEndpoint,
            onValueChange = { settings = settings.copy(microblogMediaEndpoint = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Media endpoint (optional override)") }
        )

        Text("Theme")
        RowSwitch("System default", settings.theme == AppTheme.SYSTEM) {
            settings = settings.copy(theme = if (it) AppTheme.SYSTEM else AppTheme.LIGHT)
        }
        RowSwitch("Dark mode", settings.theme == AppTheme.DARK) {
            settings = settings.copy(theme = if (it) AppTheme.DARK else AppTheme.LIGHT)
        }

        RowSwitch("Category reminder", settings.categoryReminderEnabled) {
            settings = settings.copy(categoryReminderEnabled = it)
        }

        Button(onClick = { vm.updateSettings(settings) }) { Text("Save settings") }
        uiState.statusMessage?.let { Text(it) }
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
