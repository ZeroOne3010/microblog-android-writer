package com.example.microblogwriter.auth

data class AuthState(
    val isAuthenticated: Boolean = false,
    val accessToken: String = "",
    val tokenType: String = "Bearer",
    val scope: String = "",
    val me: String = "",
    val authorizationEndpoint: String = "",
    val tokenEndpoint: String = "",
    val authInProgress: Boolean = false,
    val authError: String? = null
)

data class AuthConfig(
    val clientId: String,
    val redirectUri: String,
    val scope: String = "create update media",
    val state: String,
    val me: String
)
