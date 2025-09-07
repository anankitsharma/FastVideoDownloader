package com.fastdownloader.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("app_prefs")
private val KEY_TREE_URI = stringPreferencesKey("tree_uri")

suspend fun saveTreeUri(ctx: Context, uri: String) {
    ctx.ds.edit { it[KEY_TREE_URI] = uri }
}
fun readTreeUri(ctx: Context) = ctx.ds.data.map { it[KEY_TREE_URI] }
