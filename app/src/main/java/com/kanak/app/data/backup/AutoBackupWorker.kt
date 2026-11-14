package com.kanak.app.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import com.kanak.app.KanakApp
import com.kanak.app.data.local.datastore.UserPreferencesRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class AutoBackupController(
    private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val folderAccess: AndroidBackupFolderAccess,
    private val automaticBackup: AutomaticBackup,
) {
    private val lock = folderAccess.changeLock
    val settings = preferences.autoBackupSettings

    suspend fun setSchedule(enabled: Boolean, frequency: AutoBackupFrequency) = lock.withLock {
        preferences.setAutoBackupSchedule(enabled, frequency)
        val manager = WorkManager.getInstance(context)
        if (enabled) enqueue(manager) else manager.cancelUniqueWork(WORK_NAME).await()
        Unit
    }

    suspend fun chooseFolder(folder: String) = folderAccess.chooseFolder(folder)

    suspend fun folderName(folder: String): String = folderAccess.name(folder)

    suspend fun restoreSchedule() = lock.withLock {
        val state = settings.first()
        if (state.enabled && state.folder != null) enqueue(WorkManager.getInstance(context))
    }

    private suspend fun enqueue(manager: WorkManager) {
        // Check twice per day. The calendar rule controls when a snapshot is actually written.
        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(12, TimeUnit.HOURS).build()
        ).await()
    }

    suspend fun runBackup(): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val state = settings.first()
            val folder = state.folder
            val owner = state.owner
            if (!state.enabled || folder == null || owner == null) return@withLock true
            try {
                val outcome = automaticBackup.run(folder, owner, state.frequency)
                val warning = if (outcome.cleanupFailed) {
                    "Backup saved, but the folder could not be trimmed to three files."
                } else null
                preferences.recordAutoBackup(folder, outcome.lastBackupAt, warning)
                !outcome.cleanupFailed
            } catch (e: CancellationException) {
                throw e
            } catch (_: SecurityException) {
                preferences.recordAutoBackup(folder, null, "Choose the backup folder again to restore access.")
                true
            } catch (_: Exception) {
                preferences.recordAutoBackup(folder, null, "Automatic backup failed. Check the folder and available storage.")
                false
            }
        }
    }

    private companion object {
        const val WORK_NAME = "kanak-automatic-backup"
    }
}

class AutoBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KanakApp
        return try {
            if (app.autoBackupController.runBackup() || runAttemptCount >= 2) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }
}
