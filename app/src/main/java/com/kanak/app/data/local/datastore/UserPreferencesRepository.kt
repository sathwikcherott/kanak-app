package com.kanak.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kanak_user_preferences")

interface UserPreferencesDataSource {
    val defaultCurrency: Flow<String>
    suspend fun setDefaultCurrency(currency: String)
    val themePreference: Flow<ThemePreference>
    suspend fun setThemePreference(theme: ThemePreference)
    val lastBackupExportedTime: Flow<Long?>
    suspend fun setLastBackupExportedTime(timestamp: Long)
    // Production must apply both preferences in a single DataStore edit.
    suspend fun restorePreferences(currency: String, theme: ThemePreference) {
        throw UnsupportedOperationException("Atomic preference restoration is not configured")
    }
}

class UserPreferencesRepository(private val context: Context) : UserPreferencesDataSource {

    private object PreferenceKeys {
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val LAST_BACKUP_EXPORTED_TIME = longPreferencesKey("last_backup_exported_time")
    }

    override val defaultCurrency: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DEFAULT_CURRENCY] ?: "INR"
    }

    override suspend fun setDefaultCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_CURRENCY] = currency
        }
    }

    override val themePreference: Flow<ThemePreference> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferenceKeys.THEME_PREFERENCE]
        try {
            if (raw != null) ThemePreference.valueOf(raw) else ThemePreference.DARK
        } catch (_: Exception) {
            ThemePreference.DARK
        }
    }

    override suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.THEME_PREFERENCE] = theme.name
        }
    }

    override val lastBackupExportedTime: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.LAST_BACKUP_EXPORTED_TIME]
    }

    override suspend fun restorePreferences(currency: String, theme: ThemePreference) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_CURRENCY] = currency
            preferences[PreferenceKeys.THEME_PREFERENCE] = theme.name
        }
    }

    override suspend fun setLastBackupExportedTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_BACKUP_EXPORTED_TIME] = timestamp
        }
    }
}
