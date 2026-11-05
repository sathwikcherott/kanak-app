package com.kanak.app.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface BackupRepository {
    suspend fun createBackup(): KanakBackup
    suspend fun exportBackupToUri(context: Context, uri: Uri): Result<Unit>
    suspend fun parseAndValidateBackupFromUri(context: Context, uri: Uri): Result<BackupSummary>
    suspend fun restoreBackup(backup: KanakBackup): Result<RestoreOutcome>
    fun getLastBackupExportedTime(): Flow<Long?>
    suspend fun recordBackupExportedTime(timestamp: Long)
}
