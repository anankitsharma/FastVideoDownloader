package com.fastdownloader.app.ui

import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.fastdownloader.app.data.saveTreeUri
import androidx.compose.ui.unit.dp


@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastUri by remember { mutableStateOf<String?>(null) }
    val pickFolder = rememberFolderPicker { uri ->
        lastUri = uri.toString()
        scope.launch { saveTreeUri(ctx, uri.toString()) }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Button(onClick = pickFolder) { Text("Choose download folder") }
        if (lastUri != null) Text("Selected: $lastUri", style = MaterialTheme.typography.bodySmall)
    }
}
