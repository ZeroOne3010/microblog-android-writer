package com.example.microblogwriter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.microblogwriter.ui.AppViewModel
import com.example.microblogwriter.ui.screens.ComposeScreen
import com.example.microblogwriter.ui.screens.DraftsScreen
import com.example.microblogwriter.ui.screens.PublishedScreen
import com.example.microblogwriter.ui.screens.SettingsScreen
import com.example.microblogwriter.ui.theme.MicroblogWriterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MicroblogWriterApp() }
    }
}

data class NavItem(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun MicroblogWriterApp(appViewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()

    val items = listOf(
        NavItem("drafts", "Drafts") { Icon(Icons.AutoMirrored.Filled.List, null) },
        NavItem("compose", "New Post") { Icon(Icons.Filled.Add, null) },
        NavItem("published", "Published") { Icon(Icons.Filled.Publish, null) },
        NavItem("settings", "Settings") { Icon(Icons.Filled.Settings, null) }
    )

    MicroblogWriterTheme(theme = uiState.settings.theme) {
        Scaffold(
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
            NavHost(navController = navController, startDestination = "compose", modifier = Modifier.padding(padding)) {
                composable("drafts") { DraftsScreen(uiState, appViewModel) }
                composable("compose") { ComposeScreen(uiState, appViewModel) }
                composable("published") { PublishedScreen(uiState, appViewModel) }
                composable("settings") { SettingsScreen(uiState, appViewModel) }
            }
        }
    }
}
