package com.kanak.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.datastore.UserPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class RestoredPreferencesTest {
    @Test fun currencyAndThemeArePublishedInOneDataStoreUpdateWithoutChangingBackupTimestamp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.cacheDir, "restore-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val preferences = UserPreferencesRepository(store)
        try {
            preferences.setLastBackupExportedTime(1234L)
            val old = store.data.first()
            val observed = async(start = CoroutineStart.UNDISPATCHED) {
                store.data.first { it != old }
            }
            preferences.restorePreferences("USD", ThemePreference.LIGHT)
            val updated = withTimeout(5000) { observed.await() }
            assertEquals("USD", updated[stringPreferencesKey("default_currency")])
            assertEquals("LIGHT", updated[stringPreferencesKey("theme_preference")])
            assertEquals(1234L, preferences.lastBackupExportedTime.first())
        } finally {
            scope.cancel()
            scope.coroutineContext.job.join()
            file.delete()
        }
    }
}
