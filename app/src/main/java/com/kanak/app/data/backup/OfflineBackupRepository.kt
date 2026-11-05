package com.kanak.app.data.backup

import android.content.Context
import android.net.Uri
import com.kanak.app.data.local.datastore.UserPreferencesDataSource
import com.kanak.app.data.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class OfflineBackupRepository(
    private val expenseRepository: ExpenseRepository,
    private val userPreferencesDataSource: UserPreferencesDataSource
) : BackupRepository {

    override suspend fun createBackup(): KanakBackup = withContext(Dispatchers.IO) {
        val expenses = expenseRepository.getAllExpenses().first()
        val currency = expenseRepository.getDefaultCurrency().first()
        val theme = expenseRepository.getThemePreference().first()

        val backupExpenses = expenses.map { BackupExpense.fromDomainModel(it) }
        val backupPrefs = BackupPreferences(
            defaultCurrency = currency,
            themePreference = theme
        )

        KanakBackup(
            version = KanakBackup.CURRENT_BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = "1.0",
            preferences = backupPrefs,
            expenses = backupExpenses
        )
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

    // Stage 1 provides export/validation only. Never expose unverified destructive replacement.
    override suspend fun restoreBackup(backup: KanakBackup): Result<Unit> = Result.failure(
        UnsupportedOperationException("Restore is not available in this build. Your current data has not been changed.")
    )

    override fun getLastBackupExportedTime(): Flow<Long?> {
        return userPreferencesDataSource.lastBackupExportedTime
    }

    override suspend fun recordBackupExportedTime(timestamp: Long) {
        userPreferencesDataSource.setLastBackupExportedTime(timestamp)
    }
}
