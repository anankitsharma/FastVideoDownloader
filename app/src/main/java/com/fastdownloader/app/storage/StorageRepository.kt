// app/src/main/java/com/fastdownloader/app/storage/StorageRepository.kt
package com.fastdownloader.app.storage

import android.content.ContentResolver
import android.content.Intent           // ✅ add this
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.fastdownloader.app.data.SettingsStore
import kotlinx.coroutines.flow.first


class StorageRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    suspend fun getTreeUri(): Uri? {
        val s = settings.downloadTreeUri.first() ?: return null
        return Uri.parse(s)
    }

    fun persistTreePermission(uri: Uri) {
        val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }

    suspend fun createFile(filename: String, mime: String): Pair<DocumentFile?, Uri?> {
        val tree = getTreeUri() ?: return (null to null)
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return (null to null)

        // Avoid duplicates: if exists, append (1), (2)...
        var base = filename
        var ext = ""
        val dot = filename.lastIndexOf('.')
        if (dot > 0) { base = filename.substring(0, dot); ext = filename.substring(dot) }

        var nameTry = filename
        var i = 1
        while (dir.findFile(nameTry) != null && i < 1000) {
            nameTry = "$base ($i)$ext"
            i++
        }

        val doc = dir.createFile(mime.ifBlank { "application/octet-stream" }, nameTry)
        return doc?.let { (it to it.uri) } ?: (null to null)
    }

    fun openOutput(uri: Uri) = context.contentResolver.openOutputStream(uri)
    fun openInput(uri: Uri) = context.contentResolver.openInputStream(uri)
    fun resolver(): ContentResolver = context.contentResolver
}
