package com.sally.launcher.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sally.launcher.R
import com.sally.launcher.data.versions.GameVersion
import com.sally.launcher.data.versions.ImportStage
import com.sally.launcher.ui.versions.VersionsViewModel

@Composable
fun VersionsScreen(viewModel: VersionsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var addMenuOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<GameVersion?>(null) }
    var deleteTarget by remember { mutableStateOf<GameVersion?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFromUris(uris)
    }

    val message = state.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(context.getString(message.res, *message.args.toTypedArray()))
            viewModel.consumeMessage(message.id)
        }
    }

    SallyScreen(
        title = stringResource(R.string.versions_title),
        snackbarHostState = snackbar,
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { addMenuOpen = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.versions_import)) },
                )
                DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.versions_pick_files)) },
                        leadingIcon = { Icon(Icons.Rounded.FileOpen, contentDescription = null) },
                        onClick = {
                            addMenuOpen = false
                            picker.launch(arrayOf("*/*"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.versions_from_installed)) },
                        leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                        onClick = {
                            addMenuOpen = false
                            viewModel.importFromInstalled()
                        },
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.versions.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Layers,
                title = stringResource(R.string.versions_empty_title),
                body = stringResource(R.string.versions_empty_body),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.versions, key = { it.id }) { version ->
                    VersionCard(
                        version = version,
                        selected = version.id == state.selectedId,
                        onSelect = { viewModel.select(version.id) },
                        onRename = { renameTarget = version },
                        onDelete = { deleteTarget = version },
                    )
                }
            }
        }
    }

    state.importing?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                Column {
                    val stageRes = when (progress.stage) {
                        ImportStage.COPY -> R.string.import_stage_copy
                        ImportStage.VERIFY -> R.string.import_stage_verify
                        ImportStage.FINISH -> R.string.import_stage_finish
                    }
                    Text(stringResource(stageRes))
                    Spacer(Modifier.height(16.dp))
                    val fraction = progress.fraction
                    if (fraction != null && progress.stage == ImportStage.COPY) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.displayName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.rename_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        viewModel.rename(target.id, text)
                        renameTarget = null
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body, target.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    deleteTarget = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun VersionCard(
    version: GameVersion,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(version.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.version_meta,
                        version.versionName,
                        Formatter.formatShortFileSize(context, version.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (version.abis.isNotEmpty()) {
                    Text(
                        version.abis.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
