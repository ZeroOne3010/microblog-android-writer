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

    fun savePendingAuth(session: PendingAuthSession) {
        prefs.edit()
            .putString("pending_client_id", session.config.clientId)
            .putString("pending_redirect_uri", session.config.redirectUri)
            .putString("pending_scope", session.config.scope)
            .putString("pending_state", session.config.state)
            .putString("pending_me", session.config.me)
            .putString("pending_authorization_endpoint", session.authorizationEndpoint)
            .putString("pending_token_endpoint", session.tokenEndpoint)
            .apply()
    }

    fun loadPendingAuth(): PendingAuthSession? {
        val state = prefs.getString("pending_state", "") ?: ""
        if (state.isBlank()) return null
        val clientId = prefs.getString("pending_client_id", "") ?: ""
        val redirectUri = prefs.getString("pending_redirect_uri", "") ?: ""
        val me = prefs.getString("pending_me", "") ?: ""
        val tokenEndpoint = prefs.getString("pending_token_endpoint", "") ?: ""
        val authorizationEndpoint = prefs.getString("pending_authorization_endpoint", "") ?: ""
        if (clientId.isBlank() || redirectUri.isBlank() || me.isBlank() || tokenEndpoint.isBlank()) {
            return null
        }
        val scope = prefs.getString("pending_scope", "create update media") ?: "create update media"
        return PendingAuthSession(
            config = AuthConfig(
                clientId = clientId,
                redirectUri = redirectUri,
                scope = scope,
                state = state,
                me = me
            ),
            authorizationEndpoint = authorizationEndpoint,
            tokenEndpoint = tokenEndpoint
        )
    }

    fun clearPendingAuth() {
        prefs.edit()
            .remove("pending_client_id")
            .remove("pending_redirect_uri")
            .remove("pending_scope")
            .remove("pending_state")
            .remove("pending_me")
            .remove("pending_authorization_endpoint")
            .remove("pending_token_endpoint")
            .apply()
    }
}
