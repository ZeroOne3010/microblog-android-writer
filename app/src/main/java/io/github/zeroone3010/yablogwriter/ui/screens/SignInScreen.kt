package io.github.zeroone3010.yablogwriter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zeroone3010.yablogwriter.auth.AuthState
import io.github.zeroone3010.yablogwriter.ui.theme.destructiveButtonColors

@Composable
fun SignInScreen(
    authState: AuthState,
    defaultMe: String,
    onStartSignIn: (String) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AccountSection(
            authState = authState,
            defaultMe = defaultMe,
            onStartSignIn = onStartSignIn,
            onLogout = onLogout
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AccountSection(
    authState: AuthState,
    defaultMe: String,
    onStartSignIn: (String) -> Unit,
    onLogout: () -> Unit,
    autofocus: Boolean = false
) {
    var meInput by remember(defaultMe) { mutableStateOf(defaultMe) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(autofocus) {
        if (autofocus) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Account")
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
            Button(
                onClick = onLogout,
                colors = destructiveButtonColors()
            ) { Text("Logout") }
        } else {
            Text("Auth: not signed in")
        }

        authState.authError?.let { Text("Auth error: $it") }
    }
}
