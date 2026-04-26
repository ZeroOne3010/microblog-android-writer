package com.example.microblogwriter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.microblogwriter.ui.AppViewModel.UiEvent
import com.example.microblogwriter.ui.AppViewModel
import com.example.microblogwriter.ui.screens.ComposeScreen
import com.example.microblogwriter.ui.screens.DraftsScreen
import com.example.microblogwriter.ui.screens.SettingsScreen
import com.example.microblogwriter.ui.screens.SignInScreen
import com.example.microblogwriter.ui.theme.MicroblogWriterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MicroblogWriterApp(
                authRedirectUri = intent?.data,
                onAuthRedirectConsumed = ::clearCallbackIntentData
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setContent {
            MicroblogWriterApp(
                authRedirectUri = intent.data,
                onAuthRedirectConsumed = ::clearCallbackIntentData
            )
        }
    }

    private fun clearCallbackIntentData() {
        val currentIntent = intent ?: return
        if (currentIntent.data != null) {
            currentIntent.data = null
            setIntent(currentIntent)
        }
    }
}

data class NavItem(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun MicroblogWriterApp(
    authRedirectUri: Uri? = null,
    onAuthRedirectConsumed: () -> Unit = {},
    appViewModel: AppViewModel = viewModel()
) {
    val navController = rememberNavController()
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(authRedirectUri) {
        authRedirectUri?.let { uri ->
            appViewModel.handleAuthRedirect(uri)
            onAuthRedirectConsumed()
        }
    }
    LaunchedEffect(Unit) {
        appViewModel.events.collect { event ->
            when (event) {
                UiEvent.NavigateToPosts -> {
                    navController.navigate("drafts") {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                is UiEvent.PromptOpenInBrowser -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Post published.",
                        actionLabel = "Open published post",
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val browserIntent = Intent(Intent.ACTION_VIEW, event.url.toUri())
                        navController.context.startActivity(browserIntent)
                    }
                }
            }
        }
    }

    val items = listOf(
        NavItem("auth", "Sign In") { Icon(Icons.Filled.AccountCircle, null) },
        NavItem("drafts", "Posts") { Icon(Icons.AutoMirrored.Filled.List, null) },
        NavItem("settings", "Settings") { Icon(Icons.Filled.Settings, null) }
    )

    MicroblogWriterTheme(theme = uiState.settings.theme) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val destination = navBackStackEntry?.destination
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = item.icon,
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(navController = navController, startDestination = "auth", modifier = Modifier.padding(padding)) {
                composable("auth") {
                    SignInScreen(
                        authState = uiState.auth,
                        defaultMe = uiState.auth.me.ifBlank { "https://micro.blog" },
                        onStartSignIn = { me ->
                            appViewModel.beginSignIn(me) { url ->
                                val viewIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                                navController.context.startActivity(viewIntent)
                            }
                        },
                        onLogout = appViewModel::logout
                    )
                }
                composable("drafts") {
                    DraftsScreen(
                        uiState = uiState,
                        vm = appViewModel,
                        onOpenEditor = { navController.navigate("compose") },
                        onRequireAuth = { navController.navigate("auth") }
                    )
                }
                composable("compose") {
                    ComposeScreen(uiState, appViewModel, onRequireAuth = { navController.navigate("auth") })
                }
                composable("settings") { SettingsScreen(uiState, appViewModel) }
            }
        }
    }
}
