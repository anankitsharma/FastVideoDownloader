// app/src/main/java/com/fastdownloader/app/ui/FolderPicker.kt
package com.fastdownloader.app.ui
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fastdownloader.app.data.SettingsStore
import com.fastdownloader.app.storage.StorageRepository
import kotlinx.coroutines.launch

@Composable
fun FolderPicker() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(ctx) }
    val storage = remember { StorageRepository(ctx, settings) }

    var treeUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Make it the root (optional – allows access to subfolders)
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ctx.contentResolver.takePersistableUriPermission(uri, takeFlags)
            scope.launch {
                settings.setDownloadTreeUri(uri.toString())
                treeUri = uri
            }
        }
    }

    LaunchedEffect(Unit) {
        settings.downloadTreeUri.collect { saved ->
            treeUri = saved?.let(Uri::parse)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Download folder")
        Spacer(Modifier.height(8.dp))
        Text(treeUri?.toString() ?: "Not set")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { launcher.launch(null) }) {
            Text(if (treeUri == null) "Choose folder" else "Change folder")
        }
    }
}
