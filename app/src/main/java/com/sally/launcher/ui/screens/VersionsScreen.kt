package com.sally.launcher.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import com.sally.launcher.R
import kotlinx.coroutines.launch

@Composable
fun VersionsScreen() {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val soonMessage = stringResource(R.string.versions_import_soon)

    SallyScreen(
        title = stringResource(R.string.versions_title),
        snackbarHostState = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { snackbar.showSnackbar(soonMessage) } },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.versions_import)) },
            )
        },
    ) { padding ->
        EmptyState(
            icon = Icons.Rounded.Layers,
            title = stringResource(R.string.versions_empty_title),
            body = stringResource(R.string.versions_empty_body),
            modifier = Modifier.padding(padding),
        )
    }
}
