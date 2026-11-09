package com.kanak.app

import android.app.Application
import com.kanak.app.ai.AiConnectionStore
import com.kanak.app.ai.HttpsCloudAiCategorizer
import com.kanak.app.data.backup.AndroidBackupDocumentStore
import com.kanak.app.data.backup.AndroidBackupFolderAccess
import com.kanak.app.data.backup.AutoBackupController
import com.kanak.app.data.backup.AutomaticBackup
import com.kanak.app.data.backup.BackupFileCatalog
import com.kanak.app.data.backup.BackupRepository
import com.kanak.app.data.backup.OfflineBackupRepository
import com.kanak.app.data.local.KanakDatabase
import com.kanak.app.data.local.datastore.UserPreferencesRepository
import com.kanak.app.data.repository.OfflineExpenseRepository
import com.kanak.app.voice.AndroidVoiceInputRepository
import com.kanak.app.voice.VoiceInputRepository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KanakApp : Application() {
    val aiConnectionStore by lazy { AiConnectionStore(this) }
    val cloudAiCategorizer by lazy { HttpsCloudAiCategorizer(aiConnectionStore) }
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
    private val backupDocumentStore by lazy { AndroidBackupDocumentStore(this) }
    val backupFileCatalog by lazy { BackupFileCatalog(backupDocumentStore) }
    internal val autoBackupController by lazy {
        AutoBackupController(
            this, userPreferencesRepository, backupFolderAccess,
            AutomaticBackup(backupDocumentStore) { backupRepository.createBackup() }
        )
    }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                autoBackupController.restoreSchedule()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Kanak", "Could not restore automatic backup schedule", e)
            }
        }
    }
    val backupFolderAccess by lazy { AndroidBackupFolderAccess(this, userPreferencesRepository) }
    val backupRepository: BackupRepository by lazy {
        OfflineBackupRepository(
            persistence = expenseRepository,
            userPreferencesDataSource = userPreferencesRepository
        )
    }
}
