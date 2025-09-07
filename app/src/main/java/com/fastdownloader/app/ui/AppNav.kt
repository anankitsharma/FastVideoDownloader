package com.fastdownloader.app.ui
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Web   // needs material-icons-extended
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*

sealed class Route(val path: String, val label: String, val icon: ImageVector) {
    data object Browser : Route("browser", "Browser", Icons.Default.Web)
    data object Downloads : Route("downloads", "Downloads", Icons.Default.Download)
    data object Files : Route("files", "Files", Icons.Default.Folder)
    data object Settings : Route("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val items = listOf(Route.Browser, Route.Downloads, Route.Files, Route.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination?.route
                items.forEach { r ->
                    NavigationBarItem(
                        selected = current == r.path,
                        onClick = {
                            nav.navigate(r.path) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(r.icon, r.label) },
                        label = { Text(r.label) }
                    )
                }
            }
        }
    ) { inner ->
        NavHost(nav, startDestination = Route.Browser.path, modifier = Modifier.padding(inner)) {
            composable(Route.Browser.path) { BrowserScreen(startUrl = "") }
            composable(Route.Downloads.path) { DownloadsScreen() }   // stub next step
            composable(Route.Files.path) { FilesScreen() }           // stub
            composable(Route.Settings.path) { SettingsScreen() }     // stub
        }
    }
}
