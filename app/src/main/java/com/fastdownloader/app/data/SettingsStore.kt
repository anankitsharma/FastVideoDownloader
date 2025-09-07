// app/src/main/java/com/fastdownloader/app/data/SettingsStore.kt
package com.fastdownloader.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

private val Context.dataStore by preferencesDataStore("settings")

object Keys {
    val DOWNLOAD_TREE_URI = stringPreferencesKey("download_tree_uri")
}

class SettingsStore(private val context: Context) {
    val downloadTreeUri: Flow<String?> =
        context.dataStore.data.map { it[Keys.DOWNLOAD_TREE_URI] }

    suspend fun setDownloadTreeUri(uri: String) {
        context.dataStore.edit { it[Keys.DOWNLOAD_TREE_URI] = uri }
    }
}
