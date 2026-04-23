package com.example.microblogwriter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.microblogwriter.auth.AuthState

@Composable
fun SignInScreen(
    authState: AuthState,
    defaultMe: String,
    onStartSignIn: (String) -> Unit,
    onLogout: () -> Unit
) {
    var meInput by remember(defaultMe) { mutableStateOf(defaultMe) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Sign in to Micro.blog")
        Text("Uses IndieAuth to acquire a Micropub token compatible with Micro.blog.")

        OutlinedTextField(
            value = meInput,
            onValueChange = { meInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your site (me), e.g. https://yourname.micro.blog") }
        )

        Button(
            onClick = { onStartSignIn(meInput) },
            enabled = !authState.authInProgress
        ) {
            Text(if (authState.authInProgress) "Starting sign-in..." else "Sign in with Micro.blog")
        }

        if (authState.isAuthenticated) {
            Text("Signed in as: ${authState.me.ifBlank { "(unknown me)" }}")
            Text("Scope: ${authState.scope.ifBlank { "(not returned)" }}")
            Button(onClick = onLogout) { Text("Logout") }
        }

        authState.authError?.let { Text("Auth error: $it") }
    }
}
