package com.sally.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sally.launcher.R
import com.sally.launcher.data.versions.GameVersion

@Composable
fun HomeScreen(
    selected: GameVersion?,
    prepared: Boolean,
    onPrepare: () -> Unit,
    onOpenVersions: () -> Unit,
) {
    SallyScreen(title = stringResource(R.string.home_title)) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = selected?.displayName ?: stringResource(R.string.home_no_version_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = onContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (selected != null) {
                            stringResource(R.string.home_selected_meta, selected.versionName)
                        } else {
                            stringResource(R.string.home_no_version_body)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer,
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onOpenVersions) {
                        Text(
                            stringResource(
                                if (selected != null) R.string.home_change_version else R.string.home_open_versions,
                            ),
                        )
                    }
                }
            }

            if (selected != null && !prepared) {
                Button(onClick = onPrepare, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Build, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_prepare))
                }
                Text(
                    stringResource(R.string.home_prepare_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Tahap 3b menyalakan tombol ini.
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_launch))
                }
                Text(
                    stringResource(R.string.home_launch_soon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
