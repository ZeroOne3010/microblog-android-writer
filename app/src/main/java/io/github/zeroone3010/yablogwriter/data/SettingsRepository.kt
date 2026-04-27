package io.github.zeroone3010.yablogwriter.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.github.zeroone3010.yablogwriter.domain.AppTheme
import io.github.zeroone3010.yablogwriter.domain.SettingsState
import io.github.zeroone3010.yablogwriter.domain.TimestampFormat

class SettingsRepository(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        "secure_settings",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): SettingsState = SettingsState(
        aiEnabled = prefs.getBoolean("ai_enabled", true),
        aiProviderBaseUrl = prefs.getString("ai_provider_base_url", SettingsState().aiProviderBaseUrl) ?: SettingsState().aiProviderBaseUrl,
        aiApiKey = prefs.getString("ai_api_key", "") ?: "",
        aiModel = prefs.getString("ai_model", "gpt-4.1-mini") ?: "gpt-4.1-mini",
        aiPromptTemplate = prefs.getString("ai_prompt", SettingsState().aiPromptTemplate) ?: SettingsState().aiPromptTemplate,
        microblogApiBaseUrl = prefs.getString("microblog_api_base_url", SettingsState().microblogApiBaseUrl) ?: SettingsState().microblogApiBaseUrl,
        microblogMediaEndpoint = prefs.getString("microblog_media_endpoint", "") ?: "",
        theme = AppTheme.valueOf(prefs.getString("theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name),
        categoryReminderEnabled = prefs.getBoolean("category_reminder", true),
        timestampFormat = TimestampFormat.valueOf(
            prefs.getString("timestamp_format", TimestampFormat.ISO_24H.name) ?: TimestampFormat.ISO_24H.name
        )
    )

    fun save(settings: SettingsState) {
        prefs.edit()
            .putBoolean("ai_enabled", settings.aiEnabled)
            .putString("ai_provider_base_url", settings.aiProviderBaseUrl)
            .putString("ai_api_key", settings.aiApiKey)
            .putString("ai_model", settings.aiModel)
            .putString("ai_prompt", settings.aiPromptTemplate)
            .putString("microblog_api_base_url", settings.microblogApiBaseUrl)
            .putString("microblog_media_endpoint", settings.microblogMediaEndpoint)
            .putString("theme", settings.theme.name)
            .putBoolean("category_reminder", settings.categoryReminderEnabled)
            .putString("timestamp_format", settings.timestampFormat.name)
            .apply()
    }
}
