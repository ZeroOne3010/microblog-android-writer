package io.github.zeroone3010.yablogwriter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zeroone3010.yablogwriter.BuildConfig
import io.github.zeroone3010.yablogwriter.domain.AppTheme
import io.github.zeroone3010.yablogwriter.domain.AppUiState
import io.github.zeroone3010.yablogwriter.domain.TimestampFormat
import io.github.zeroone3010.yablogwriter.ui.AppViewModel
import kotlinx.coroutines.flow.debounce
import java.time.Duration
import java.time.Instant

@Composable
fun SettingsScreen(
    uiState: AppUiState,
    vm: AppViewModel,
    focusAccountSection: Boolean = false,
    onStartSignIn: (String) -> Unit,
    onLogout: () -> Unit
) {
    var settings by remember(uiState.settings) { mutableStateOf(uiState.settings) }
    val saveIndicator = remember(uiState.settingsLastSavedAt) { formatSettingsSaveIndicator(uiState.settingsLastSavedAt) }

    LaunchedEffect(Unit) {
        snapshotFlow { settings }
            .debounce(700)
            .collect { debounced -> vm.updateSettings(debounced) }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSection("Account") {
                AccountSection(
                    authState = uiState.auth,
                    defaultMe = uiState.auth.me.ifBlank { "https://micro.blog" },
                    onStartSignIn = onStartSignIn,
                    onLogout = onLogout,
                    autofocus = focusAccountSection,
                    showSectionTitle = false
                )
            }

            SettingsSection("Micro.blog") {
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
            }

            SettingsSection("Appearance") {
                ThemeRadioOption(
                    label = "System default",
                    selected = settings.theme == AppTheme.SYSTEM,
                    onClick = { settings = settings.copy(theme = AppTheme.SYSTEM) }
                )
                ThemeRadioOption(
                    label = "Light mode",
                    selected = settings.theme == AppTheme.LIGHT,
                    onClick = { settings = settings.copy(theme = AppTheme.LIGHT) }
                )
                ThemeRadioOption(
                    label = "Dark mode",
                    selected = settings.theme == AppTheme.DARK,
                    onClick = { settings = settings.copy(theme = AppTheme.DARK) }
                )
            }

            SettingsSection("Category reminder") {
                RowSwitch("Remind me to add categories", settings.categoryReminderEnabled) {
                    settings = settings.copy(categoryReminderEnabled = it)
                }
            }

            SettingsSection("Timestamp format") {
                ThemeRadioOption(
                    label = "YYYY-MM-DD HH:MM",
                    selected = settings.timestampFormat == TimestampFormat.ISO_24H,
                    onClick = { settings = settings.copy(timestampFormat = TimestampFormat.ISO_24H) }
                )
                ThemeRadioOption(
                    label = "D.M.Y HH:MM",
                    selected = settings.timestampFormat == TimestampFormat.DMY_24H,
                    onClick = { settings = settings.copy(timestampFormat = TimestampFormat.DMY_24H) }
                )
                ThemeRadioOption(
                    label = "M/D/Y h:MM a",
                    selected = settings.timestampFormat == TimestampFormat.MDY_12H,
                    onClick = { settings = settings.copy(timestampFormat = TimestampFormat.MDY_12H) }
                )
            }

            SettingsSection("AI settings") {
                var aiAdvancedExpanded by remember { mutableStateOf(false) }
                RowSwitch("Enable AI review", settings.aiEnabled) { settings = settings.copy(aiEnabled = it) }
                OutlinedButton(
                    onClick = { aiAdvancedExpanded = !aiAdvancedExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (aiAdvancedExpanded) "Hide advanced AI settings" else "Show advanced AI settings")
                }
                if (aiAdvancedExpanded) {
                    Text("Disclosure: Running AI review sends the current draft title/body to the configured AI provider.")
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
                }
            }

            SettingsSection("Draft storage") {
                Text("Drafts are stored locally on your device.")
                Text(
                    "/storage/emulated/0/Android/media/io.github.zeroone3010.yablogwriter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = saveIndicator,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val buildTime = BuildConfig.BUILD_TIME_UTC.takeUnless { it.isBlank() || it == "null" } ?: "debug build"
            val commit = BuildConfig.GIT_COMMIT_SHORT.takeUnless { it.isBlank() || it == "null" } ?: "local"
            Text(
                text = "Build: $buildTime • $commit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
            )
        }
    }
}

private fun formatSettingsSaveIndicator(lastSavedAt: Instant?): String {
    if (lastSavedAt == null) return "Saved just now"
    val seconds = Duration.between(lastSavedAt, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        seconds < 5 -> "Saved just now"
        seconds < 60 -> "Saved ${seconds}s ago"
        seconds < 3600 -> "Saved ${seconds / 60}m ago"
        else -> "Saved ${seconds / 3600}h ago"
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
            }
        )
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        RadioButton(selected = selected, onClick = onClick)
    }
}
