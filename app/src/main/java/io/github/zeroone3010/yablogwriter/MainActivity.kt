package io.github.zeroone3010.yablogwriter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.zeroone3010.yablogwriter.ui.AppViewModel.UiEvent
import io.github.zeroone3010.yablogwriter.ui.AppViewModel
import io.github.zeroone3010.yablogwriter.ui.screens.ComposeScreen
import io.github.zeroone3010.yablogwriter.ui.screens.DraftsScreen
import io.github.zeroone3010.yablogwriter.ui.screens.SettingsScreen
import io.github.zeroone3010.yablogwriter.ui.theme.MicroblogWriterTheme

private const val ROUTE_DRAFTS = "drafts"
private const val ROUTE_COMPOSE = "compose"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SETTINGS_ACCOUNT = "settings?focusAccount=true"

data class NavItem(val route: String, val label: String, val icon: @Composable () -> Unit)

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

@Composable
fun MicroblogWriterApp(
    authRedirectUri: Uri? = null,
    onAuthRedirectConsumed: () -> Unit = {},
    appViewModel: AppViewModel = viewModel()
) {
    val navController = rememberNavController()
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val beginSignIn: (String) -> Unit = { me ->
        appViewModel.beginSignIn(me) { url ->
            val viewIntent = Intent(Intent.ACTION_VIEW, url.toUri())
            navController.context.startActivity(viewIntent)
        }
    }

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
                    navController.navigate(ROUTE_DRAFTS) {
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
    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, withDismissAction = true)
        appViewModel.clearStatusMessage()
    }

    val items = listOf(
        NavItem(ROUTE_DRAFTS, "Posts") { Icon(Icons.AutoMirrored.Filled.List, null) },
        NavItem(ROUTE_SETTINGS, "Settings") { Icon(Icons.Filled.Settings, null) }
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
                            selected = destination?.hierarchy?.any { it.route?.startsWith(item.route) == true } == true,
                            onClick = {
                                val leavingComposeToPosts = destination?.route == ROUTE_COMPOSE && item.route == ROUTE_DRAFTS
                                if (leavingComposeToPosts) {
                                    appViewModel.saveDraft()
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                } else {
                                    if (destination?.route == ROUTE_COMPOSE && item.route != ROUTE_COMPOSE) {
                                        appViewModel.saveDraft()
                                    }
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = item.icon,
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(navController = navController, startDestination = ROUTE_DRAFTS, modifier = Modifier.padding(padding)) {
                composable(ROUTE_DRAFTS) {
                    DraftsScreen(
                        uiState = uiState,
                        vm = appViewModel,
                        onOpenEditor = { navController.navigate(ROUTE_COMPOSE) }
                    )
                }
                composable(ROUTE_COMPOSE) {
                    ComposeScreen(uiState, appViewModel, onRequireAuth = { navController.navigate(ROUTE_SETTINGS_ACCOUNT) })
                }
                composable(
                    route = "settings?focusAccount={focusAccount}",
                    arguments = listOf(navArgument("focusAccount") { type = NavType.BoolType; defaultValue = false })
                ) { backStackEntry ->
                    SettingsScreen(
                        uiState = uiState,
                        vm = appViewModel,
                        focusAccountSection = backStackEntry.arguments?.getBoolean("focusAccount") == true,
                        onStartSignIn = beginSignIn,
                        onLogout = appViewModel::logout
                    )
                }
            }
        }
    }
}
