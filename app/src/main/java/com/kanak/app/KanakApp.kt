package com.kanak.app

import android.app.Application
import com.kanak.app.data.backup.BackupRepository
import com.kanak.app.data.backup.OfflineBackupRepository
import com.kanak.app.data.local.KanakDatabase
import com.kanak.app.data.local.datastore.UserPreferencesRepository
import com.kanak.app.data.repository.OfflineExpenseRepository
import com.kanak.app.voice.AndroidVoiceInputRepository
import com.kanak.app.voice.VoiceInputRepository

class KanakApp : Application() {
    val database by lazy { KanakDatabase.getDatabase(this) }
    val userPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val voiceInputRepository: VoiceInputRepository by lazy { AndroidVoiceInputRepository(this) }
    val expenseRepository: OfflineExpenseRepository by lazy {
        OfflineExpenseRepository(
            expenseDao = database.expenseDao(),
            userPreferencesDataSource = userPreferencesRepository,
            aiEnrichmentJobDao = database.aiEnrichmentJobDao(),
            database = database
        )
    }
    val backupRepository: BackupRepository by lazy {
        OfflineBackupRepository(
            persistence = expenseRepository,
            userPreferencesDataSource = userPreferencesRepository
        )
    }
}
