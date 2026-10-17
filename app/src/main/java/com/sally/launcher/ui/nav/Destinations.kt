package com.sally.launcher.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.sally.launcher.R

enum class Destination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Rounded.Home),
    VERSIONS("versions", R.string.nav_versions, Icons.Rounded.Layers),
    CONTENT("content", R.string.nav_content, Icons.Rounded.Folder),
    SETTINGS("settings", R.string.nav_settings, Icons.Rounded.Settings),
}
