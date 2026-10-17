package com.sally.launcher.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sally.launcher.R

@Composable
fun ContentScreen() {
    SallyScreen(title = stringResource(R.string.content_title)) { padding ->
        EmptyState(
            icon = Icons.Rounded.Folder,
            title = stringResource(R.string.content_empty_title),
            body = stringResource(R.string.content_empty_body),
            modifier = Modifier.padding(padding),
        )
    }
}
