package com.kanak.app.data.backup

import android.content.Context
import android.net.Uri
import com.kanak.app.data.local.datastore.UserPreferencesDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class OfflineBackupRepository(
    private val persistence: BackupPersistence,
    private val userPreferencesDataSource: UserPreferencesDataSource
) : BackupRepository {

    override suspend fun createBackup(): KanakBackup = withContext(Dispatchers.IO) {
        persistence.snapshotForBackup()
    }

    override suspend fun exportBackupToUri(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backup = createBackup()
            val jsonString = KanakBackupSerializer.toJson(backup)
            // Do not report a successful export for a file our validator cannot restore.
            KanakBackupSerializer.fromJson(jsonString)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(BackupException.FileReadError("Unable to open output stream for URI"))

            recordBackupExportedTime(backup.exportedAt)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun parseAndValidateBackupFromUri(context: Context, uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val coroutineContext = currentCoroutineContext()
            val backup = context.contentResolver.openInputStream(uri)?.use {
                KanakBackupSerializer.fromInputStream(it) { coroutineContext.ensureActive() }
            } ?: return@withContext Result.failure(BackupException.FileReadError("Unable to open input stream for URI"))

            val summary = KanakBackupSerializer.summarize(backup)
            Result.success(summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreBackup(backup: KanakBackup): Result<RestoreOutcome> = withContext(Dispatchers.IO) {
        try {
            Result.success(persistence.restoreValidatedBackup(backup))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getLastBackupExportedTime(): Flow<Long?> {
        return userPreferencesDataSource.lastBackupExportedTime
    }

    override suspend fun recordBackupExportedTime(timestamp: Long) {
        userPreferencesDataSource.setLastBackupExportedTime(timestamp)
    }
}
