package com.example.microblogwriter.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class AuthRepository(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        "secure_auth",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): AuthState {
        val token = prefs.getString("access_token", "") ?: ""
        return AuthState(
            isAuthenticated = token.isNotBlank(),
            accessToken = token,
            tokenType = prefs.getString("token_type", "Bearer") ?: "Bearer",
            scope = prefs.getString("scope", "") ?: "",
            me = prefs.getString("me", "") ?: "",
            authorizationEndpoint = prefs.getString("authorization_endpoint", "") ?: "",
            tokenEndpoint = prefs.getString("token_endpoint", "") ?: ""
        )
    }

    fun save(state: AuthState) {
        prefs.edit()
            .putString("access_token", state.accessToken)
            .putString("token_type", state.tokenType)
            .putString("scope", state.scope)
            .putString("me", state.me)
            .putString("authorization_endpoint", state.authorizationEndpoint)
            .putString("token_endpoint", state.tokenEndpoint)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
