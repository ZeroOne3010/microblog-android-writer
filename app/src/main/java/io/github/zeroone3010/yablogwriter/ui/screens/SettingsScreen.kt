package io.github.zeroone3010.yablogwriter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zeroone3010.yablogwriter.domain.AppTheme
import io.github.zeroone3010.yablogwriter.domain.AppUiState
import io.github.zeroone3010.yablogwriter.ui.AppViewModel

@Composable
fun SettingsScreen(
    uiState: AppUiState,
    vm: AppViewModel,
    focusAccountSection: Boolean = false,
    onStartSignIn: (String) -> Unit,
    onLogout: () -> Unit
) {
    var settings by remember(uiState.settings) { mutableStateOf(uiState.settings) }

    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Button(
                    onClick = { vm.updateSettings(settings) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Save settings")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccountSection(
                authState = uiState.auth,
                defaultMe = uiState.auth.me.ifBlank { "https://micro.blog" },
                onStartSignIn = onStartSignIn,
                onLogout = onLogout,
                autofocus = focusAccountSection
            )

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

            uiState.statusMessage?.let { Text(it) }
        }
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
