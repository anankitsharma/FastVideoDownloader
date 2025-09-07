package com.fastdownloader.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// If you already have this helper from your project, keep the import.
// Otherwise comment it and use your own SettingsStore.setDownloadTreeUri(...) call.
import com.fastdownloader.app.data.saveTreeUri

// ------------------------------------------------------------
// Helper: a remembered launcher that opens the SAF folder picker
// and persists permissions. It returns a () -> Unit you can call.
// ------------------------------------------------------------
@Composable
fun rememberFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        pending = false
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            // Persist read/write access to the chosen tree
            ctx.contentResolver.takePersistableUriPermission(uri, flags)
            onPicked(uri)
        }
    }

    return {
        if (!pending) {
            pending = true
            launcher.launch(null)  // open the folder picker
        }
    }
}

// ------------------------------------------------------------
// Settings screen
// ------------------------------------------------------------
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // For showing the currently selected folder in UI
    var lastUri by remember { mutableStateOf<String?>(null) }

    val pickFolder = rememberFolderPicker { uri: Uri ->
        lastUri = uri.toString()
        // Save using your existing project helper (DataStore/SharedPrefs behind the scenes)
        scope.launch { saveTreeUri(ctx, uri.toString()) }
    }

    // TODO: If you have a getter for the saved URI, load it here.
    // For example, if you added a SettingsStore, collect the flow in LaunchedEffect and set lastUri.

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.titleLarge)
        Text(text = "Download folder:")
        Text(text = lastUri ?: "Not set")

        Spacer(Modifier.height(8.dp))

        Button(onClick = pickFolder) {
            Text(text = if (lastUri == null) "Choose download folder" else "Change download folder")
        }
    }
}
