package com.kanak.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kanak_user_preferences")

interface UserPreferencesDataSource {
    val defaultCurrency: Flow<String>
    suspend fun setDefaultCurrency(currency: String)
}

class UserPreferencesRepository(private val context: Context) : UserPreferencesDataSource {

    private object PreferenceKeys {
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
    }

    override val defaultCurrency: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DEFAULT_CURRENCY] ?: "USD"
    }

    override suspend fun setDefaultCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_CURRENCY] = currency
        }
    }
}
