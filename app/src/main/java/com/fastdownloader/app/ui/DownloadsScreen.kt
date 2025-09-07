package com.fastdownloader.app.ui

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DownloadsScreen() {
    val ctx = LocalContext.current
    val entries by remember { mutableStateOf(loadDownloads(ctx)) }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries.size) { i ->
            val e = entries[i]
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable {
                    // Try to open; DownloadManager files are public so ACTION_VIEW typically works
                    runCatching {
                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(e.uri, e.mimeType ?: "*/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }
                }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(e.title ?: e.fileName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    if (!e.statusText.isNullOrBlank()) Text(e.statusText!!, style = MaterialTheme.typography.bodySmall)
                    if (!e.sizeText.isNullOrBlank()) Text(e.sizeText!!, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private data class DMEntry(
    val id: Long,
    val title: String?,
    val uri: Uri,
    val mimeType: String?,
    val statusText: String?,
    val sizeText: String?,
    val fileName: String?
)

private fun loadDownloads(ctx: Context): List<DMEntry> {
    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val q = DownloadManager.Query().setFilterByStatus(
        DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED
    )
    val out = mutableListOf<DMEntry>()
    dm.query(q)?.use { c ->
        val idIdx = c.getColumnIndex(DownloadManager.COLUMN_ID)
        val titleIdx = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
        val uriIdx = c.getColumnIndex(DownloadManager.COLUMN_URI)
        val mimeIdx = c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
        val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val totalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val soFarIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        while (c.moveToNext()) {
            val id = c.getLong(idIdx)
            val title = c.getString(titleIdx)
            val uri = Uri.parse(c.getString(uriIdx))
            val mime = c.getString(mimeIdx)
            val status = when (c.getInt(statusIdx)) {
                DownloadManager.STATUS_PENDING -> "Pending"
                DownloadManager.STATUS_RUNNING -> "Downloading"
                DownloadManager.STATUS_PAUSED -> "Paused"
                DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                DownloadManager.STATUS_FAILED -> "Failed"
                else -> null
            }
            val total = c.getLong(totalIdx)
            val soFar = c.getLong(soFarIdx)
            val sizeText =
                if (total > 0) "${formatMB(soFar)} / ${formatMB(total)}"
                else if (soFar > 0) "${formatMB(soFar)}"
                else null
            val fileName = guessFileNameFromUri(ctx, uri)
            out += DMEntry(id, title, uri, mime, status, sizeText, fileName)
        }
    }
    return out
}

private fun formatMB(bytes: Long): String = String.format("%.1f MB", bytes / 1024f / 1024f)

private fun guessFileNameFromUri(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
        val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cur.moveToFirst()) cur.getString(idx) else null
    }
}.getOrNull()
