package com.example.microblogwriter.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.microblogwriter.domain.AppTheme
import com.example.microblogwriter.domain.SettingsState

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
        microblogAccessToken = prefs.getString("microblog_access_token", "") ?: "",
        microblogMediaEndpoint = prefs.getString("microblog_media_endpoint", "") ?: "",
        theme = AppTheme.valueOf(prefs.getString("theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name),
        categoryReminderEnabled = prefs.getBoolean("category_reminder", true)
    )

    fun save(settings: SettingsState) {
        prefs.edit()
            .putBoolean("ai_enabled", settings.aiEnabled)
            .putString("ai_provider_base_url", settings.aiProviderBaseUrl)
            .putString("ai_api_key", settings.aiApiKey)
            .putString("ai_model", settings.aiModel)
            .putString("ai_prompt", settings.aiPromptTemplate)
            .putString("microblog_api_base_url", settings.microblogApiBaseUrl)
            .putString("microblog_access_token", settings.microblogAccessToken)
            .putString("microblog_media_endpoint", settings.microblogMediaEndpoint)
            .putString("theme", settings.theme.name)
            .putBoolean("category_reminder", settings.categoryReminderEnabled)
            .apply()
    }
}
