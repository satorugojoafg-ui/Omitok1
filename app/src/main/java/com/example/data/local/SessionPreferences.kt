package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.omitokDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "omitok_preferences"
)

data class PlaybackPreferences(
    val autoplay: Boolean = true,
    val loop: Boolean = true,
    val startMuted: Boolean = false
)

data class PersistedSettings(
    val authToken: String? = null,
    val savedUsername: String? = null,
    val language: AppLanguage = AppLanguage.FA,
    val baseUrl: String = DEFAULT_BASE_URL,
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val recentSearches: List<String> = emptyList()
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://omitok07090.pythonanywhere.com"
    }
}

class SessionPreferences(private val context: Context) {

    private object Keys {
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USERNAME = stringPreferencesKey("saved_username")
        val LANGUAGE = stringPreferencesKey("app_language")
        val BASE_URL = stringPreferencesKey("base_url")
        val AUTOPLAY = booleanPreferencesKey("autoplay_enabled")
        val LOOP = booleanPreferencesKey("loop_enabled")
        val START_MUTED = booleanPreferencesKey("start_muted")
        val RECENT_SEARCHES = stringSetPreferencesKey("recent_searches")
    }

    val settingsFlow: Flow<PersistedSettings> = context.omitokDataStore.data.map { prefs ->
        PersistedSettings(
            authToken = prefs[Keys.AUTH_TOKEN]?.takeIf { it.isNotBlank() },
            savedUsername = prefs[Keys.USERNAME]?.takeIf { it.isNotBlank() },
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            baseUrl = prefs[Keys.BASE_URL]
                ?.trim()
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: PersistedSettings.DEFAULT_BASE_URL,
            playback = PlaybackPreferences(
                autoplay = prefs[Keys.AUTOPLAY] ?: true,
                loop = prefs[Keys.LOOP] ?: true,
                startMuted = prefs[Keys.START_MUTED] ?: false
            ),
            recentSearches = prefs[Keys.RECENT_SEARCHES]?.toList()?.sorted() ?: emptyList()
        )
    }

    suspend fun saveSession(token: String?, username: String) {
        context.omitokDataStore.edit { prefs ->
            if (!token.isNullOrBlank()) {
                prefs[Keys.AUTH_TOKEN] = token
            }
            prefs[Keys.USERNAME] = username
        }
    }

    suspend fun clearSession() {
        context.omitokDataStore.edit { prefs ->
            prefs.remove(Keys.AUTH_TOKEN)
            prefs.remove(Keys.USERNAME)
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.omitokDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.code
        }
    }

    suspend fun setBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            context.omitokDataStore.edit { prefs ->
                prefs[Keys.BASE_URL] = normalized
            }
        }
    }

    suspend fun updatePlayback(autoplay: Boolean, loop: Boolean, startMuted: Boolean) {
        context.omitokDataStore.edit { prefs ->
            prefs[Keys.AUTOPLAY] = autoplay
            prefs[Keys.LOOP] = loop
            prefs[Keys.START_MUTED] = startMuted
        }
    }

    suspend fun addRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        context.omitokDataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_SEARCHES]?.toMutableSet() ?: mutableSetOf()
            current.add(clean)
            if (current.size > 8) {
                current.remove(current.first())
            }
            prefs[Keys.RECENT_SEARCHES] = current
        }
    }

    suspend fun clearRecentSearches() {
        context.omitokDataStore.edit { prefs ->
            prefs.remove(Keys.RECENT_SEARCHES)
        }
    }
}
