package com.sally.launcher.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sally.launcher.data.settings.AppSettings
import com.sally.launcher.data.settings.ThemeMode
import com.sally.launcher.ui.nav.Destination
import com.sally.launcher.ui.screens.ContentScreen
import com.sally.launcher.R
import com.sally.launcher.ui.screens.HomeScreen
import com.sally.launcher.ui.screens.ProgressDialog
import com.sally.launcher.ui.screens.SettingsScreen
import com.sally.launcher.ui.screens.VersionsScreen
import com.sally.launcher.ui.versions.VersionsViewModel

@Composable
fun LauncherRoot(
    settings: AppSettings,
    storageRoot: String,
    versionsViewModel: VersionsViewModel,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val versionsState by versionsViewModel.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    fun go(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        // Tiap layar mengurus inset atasnya sendiri lewat top app bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { go(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.HOME.route) {
                HomeScreen(
                    selected = versionsState.selected,
                    prepared = versionsState.selectedPrepared,
                    onPrepare = { versionsState.selected?.let(versionsViewModel::prepare) },
                    onOpenVersions = { go(Destination.VERSIONS) },
                )
            }
            composable(Destination.VERSIONS.route) { VersionsScreen(versionsViewModel) }
            composable(Destination.CONTENT.route) { ContentScreen() }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    settings = settings,
                    storageRoot = storageRoot,
                    onThemeMode = onThemeMode,
                    onDynamicColor = onDynamicColor,
                )
            }
        }
    }

    // Progres persiapan tampil di tab mana pun.
    versionsState.preparing?.let { progress ->
        ProgressDialog(
            title = stringResource(R.string.prepare_title),
            label = stringResource(R.string.prepare_label),
            fraction = progress.fraction,
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = versionsViewModel::cancelPrepare,
        )
    }
}
