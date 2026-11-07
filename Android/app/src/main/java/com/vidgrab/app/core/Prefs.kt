package com.vidgrab.app.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

val Context.dataStore by preferencesDataStore(name = "vidgrab_prefs")

@Serializable
data class StoredDownloads(
    val active: List<DownloadItem> = emptyList(),
    val history: List<HistoryEntry> = emptyList()
)

object Prefs {
    private val KEY_THEME = stringPreferencesKey("theme_mode")
    private val KEY_LAST_VERSION = stringPreferencesKey("last_app_version")
    private val KEY_LAST_CHECK = longPreferencesKey("last_engine_check")

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    sealed class ThemeMode(val id: String, val label: String) {
        data object System : ThemeMode("system", "System")
        data object Dark : ThemeMode("dark", "Dark")
        data object Light : ThemeMode("light", "Light")
        companion object {
            fun from(id: String?): ThemeMode = when (id) {
                "dark" -> Dark; "light" -> Light; else -> System
            }
        }
    }

    fun themeFlow(context: Context): Flow<ThemeMode> =
        context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { ThemeMode.from(it[KEY_THEME]) }

    suspend fun setTheme(context: Context, mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.id }
    }

    suspend fun wasVersionSeen(context: Context, version: String): Boolean {
        val seen = context.dataStore.data.first()[KEY_LAST_VERSION]
        return seen == version
    }

    suspend fun markVersionSeen(context: Context, version: String) {
        context.dataStore.edit { it[KEY_LAST_VERSION] = version }
    }

    // ---- persisted download queue / history (service restart survival) ----
    suspend fun saveDownloads(context: Context, active: List<DownloadItem>, history: List<HistoryEntry>) {
        val payload = json.encodeToString(StoredDownloads(active = active, history = history))
        context.dataStore.edit { it[stringPreferencesKey("downloads_blob")] = payload }
    }

    suspend fun loadDownloads(context: Context): StoredDownloads {
        return try {
            val raw = context.dataStore.data.first()[stringPreferencesKey("downloads_blob")]
            if (raw != null) json.decodeFromString<StoredDownloads>(raw) else StoredDownloads()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
            StoredDownloads()
        }
    }
}
