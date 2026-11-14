package com.kanak.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kanak.app.data.backup.AutoBackupFrequency
import com.kanak.app.data.backup.AutoBackupSettings
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kanak_user_preferences")

interface UserPreferencesDataSource {
    val defaultCurrency: Flow<String>
    suspend fun setDefaultCurrency(currency: String)
    val themePreference: Flow<ThemePreference>
    suspend fun setThemePreference(theme: ThemePreference)
    val lastBackupExportedTime: Flow<Long?>
    suspend fun setLastBackupExportedTime(timestamp: Long)
    val backupFolderUri: Flow<String?> get() = kotlinx.coroutines.flow.flowOf(null)
    suspend fun setBackupFolderUri(uri: String) {
        throw UnsupportedOperationException("Backup folder preference is not configured")
    }
    // Production must apply both preferences in a single DataStore edit.
    suspend fun restorePreferences(currency: String, theme: ThemePreference) {
        throw UnsupportedOperationException("Atomic preference restoration is not configured")
    }
}

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) : UserPreferencesDataSource {
    constructor(context: Context) : this(context.dataStore)

    private object PreferenceKeys {
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_FREQUENCY = stringPreferencesKey("auto_backup_frequency")
        val AUTO_BACKUP_OWNER = stringPreferencesKey("auto_backup_owner")
        val AUTO_BACKUP_LAST_FOLDER = stringPreferencesKey("auto_backup_last_folder")
        val AUTO_BACKUP_LAST_TIME = longPreferencesKey("auto_backup_last_time")
        val AUTO_BACKUP_WARNING = stringPreferencesKey("auto_backup_warning")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val LAST_BACKUP_EXPORTED_TIME = longPreferencesKey("last_backup_exported_time")
    }

    override val backupFolderUri: Flow<String?> = dataStore.data.map { it[PreferenceKeys.BACKUP_FOLDER_URI] }

    override suspend fun setBackupFolderUri(uri: String) {
        dataStore.edit { it[PreferenceKeys.BACKUP_FOLDER_URI] = uri }
    }

    val autoBackupSettings: Flow<AutoBackupSettings> = dataStore.data.map { prefs ->
        val folder = prefs[PreferenceKeys.BACKUP_FOLDER_URI]
        val matchingFolder = folder != null && folder == prefs[PreferenceKeys.AUTO_BACKUP_LAST_FOLDER]
        AutoBackupSettings(
            enabled = prefs[PreferenceKeys.AUTO_BACKUP_ENABLED] ?: false,
            frequency = AutoBackupFrequency.entries.firstOrNull {
                it.name == prefs[PreferenceKeys.AUTO_BACKUP_FREQUENCY]
            } ?: AutoBackupFrequency.DAILY,
            folder = folder,
            owner = prefs[PreferenceKeys.AUTO_BACKUP_OWNER],
            lastBackupAt = if (matchingFolder) prefs[PreferenceKeys.AUTO_BACKUP_LAST_TIME] else null,
            warning = if (matchingFolder) prefs[PreferenceKeys.AUTO_BACKUP_WARNING] else null,
        )
    }.distinctUntilChanged()

    suspend fun setAutoBackupSchedule(enabled: Boolean, frequency: AutoBackupFrequency) {
        dataStore.edit { prefs ->
            require(!enabled || prefs[PreferenceKeys.BACKUP_FOLDER_URI] != null) { "Choose a backup folder first." }
            if (enabled && prefs[PreferenceKeys.AUTO_BACKUP_OWNER] == null) {
                prefs[PreferenceKeys.AUTO_BACKUP_OWNER] = UUID.randomUUID().toString()
            }
            prefs[PreferenceKeys.AUTO_BACKUP_ENABLED] = enabled
            prefs[PreferenceKeys.AUTO_BACKUP_FREQUENCY] = frequency.name
        }
    }

    suspend fun recordAutoBackup(folder: String, lastBackupAt: Long?, warning: String?) {
        dataStore.edit { prefs ->
            if (prefs[PreferenceKeys.BACKUP_FOLDER_URI] != folder) return@edit
            if (prefs[PreferenceKeys.AUTO_BACKUP_LAST_FOLDER] != folder) {
                prefs.remove(PreferenceKeys.AUTO_BACKUP_LAST_TIME)
            }
            prefs[PreferenceKeys.AUTO_BACKUP_LAST_FOLDER] = folder
            if (lastBackupAt != null) prefs[PreferenceKeys.AUTO_BACKUP_LAST_TIME] = lastBackupAt
            if (warning == null) prefs.remove(PreferenceKeys.AUTO_BACKUP_WARNING)
            else prefs[PreferenceKeys.AUTO_BACKUP_WARNING] = warning
        }
    }

    override val defaultCurrency: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DEFAULT_CURRENCY] ?: "INR"
    }

    override suspend fun setDefaultCurrency(currency: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_CURRENCY] = currency
        }
    }

    override val themePreference: Flow<ThemePreference> = dataStore.data.map { preferences ->
        val raw = preferences[PreferenceKeys.THEME_PREFERENCE]
        try {
            if (raw != null) ThemePreference.valueOf(raw) else ThemePreference.DARK
        } catch (_: Exception) {
            ThemePreference.DARK
        }
    }

    override suspend fun setThemePreference(theme: ThemePreference) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.THEME_PREFERENCE] = theme.name
        }
    }

    override val lastBackupExportedTime: Flow<Long?> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.LAST_BACKUP_EXPORTED_TIME]
    }

    override suspend fun restorePreferences(currency: String, theme: ThemePreference) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_CURRENCY] = currency
            preferences[PreferenceKeys.THEME_PREFERENCE] = theme.name
        }
    }

    override suspend fun setLastBackupExportedTime(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_BACKUP_EXPORTED_TIME] = timestamp
        }
    }
}
