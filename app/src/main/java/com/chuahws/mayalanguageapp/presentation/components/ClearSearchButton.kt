package com.chuahws.mayalanguageapp.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

@Composable
internal fun ClearSearchButton(query: String, onClear: () -> Unit) {
    if (query.isNotEmpty()) {
        IconButton(onClick = onClear) {
            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
        }
    }
}
