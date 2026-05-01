package io.github.zeroone3010.yablogwriter.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.github.zeroone3010.yablogwriter.domain.AiReviewPromptType
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
        aiEnabled = prefs.getBoolean("ai_enabled", SettingsState().aiEnabled),
        aiProviderBaseUrl = prefs.getString("ai_provider_base_url", SettingsState().aiProviderBaseUrl) ?: SettingsState().aiProviderBaseUrl,
        aiApiKey = prefs.getString("ai_api_key", "") ?: "",
        aiIdeaPrompt = prefs.getString("ai_idea_prompt", SettingsState().aiIdeaPrompt) ?: SettingsState().aiIdeaPrompt,
        aiIdeaModel = prefs.getString("ai_idea_model", SettingsState().aiIdeaModel) ?: SettingsState().aiIdeaModel,
        aiDraftPrompt = prefs.getString("ai_draft_prompt", SettingsState().aiDraftPrompt) ?: SettingsState().aiDraftPrompt,
        aiDraftModel = prefs.getString("ai_draft_model", SettingsState().aiDraftModel) ?: SettingsState().aiDraftModel,
        aiFinalPrompt = prefs.getString("ai_final_prompt", SettingsState().aiFinalPrompt) ?: SettingsState().aiFinalPrompt,
        aiFinalModel = prefs.getString("ai_final_model", SettingsState().aiFinalModel) ?: SettingsState().aiFinalModel,
        aiCustomPrompt = prefs.getString("ai_custom_prompt", "") ?: "",
        aiCustomModel = prefs.getString("ai_custom_model", SettingsState().aiCustomModel) ?: SettingsState().aiCustomModel,
        aiSelectedPromptType = AiReviewPromptType.valueOf(
            prefs.getString("ai_selected_prompt_type", SettingsState().aiSelectedPromptType.name) ?: SettingsState().aiSelectedPromptType.name
        ),
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
            .putString("ai_idea_prompt", settings.aiIdeaPrompt)
            .putString("ai_idea_model", settings.aiIdeaModel)
            .putString("ai_draft_prompt", settings.aiDraftPrompt)
            .putString("ai_draft_model", settings.aiDraftModel)
            .putString("ai_final_prompt", settings.aiFinalPrompt)
            .putString("ai_final_model", settings.aiFinalModel)
            .putString("ai_custom_prompt", settings.aiCustomPrompt)
            .putString("ai_custom_model", settings.aiCustomModel)
            .putString("ai_selected_prompt_type", settings.aiSelectedPromptType.name)
            .putString("microblog_api_base_url", settings.microblogApiBaseUrl)
            .putString("microblog_media_endpoint", settings.microblogMediaEndpoint)
            .putString("theme", settings.theme.name)
            .putBoolean("category_reminder", settings.categoryReminderEnabled)
            .putString("timestamp_format", settings.timestampFormat.name)
            .apply()
    }
}
