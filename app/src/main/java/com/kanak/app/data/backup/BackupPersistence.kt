package com.kanak.app.data.backup

/** App-wide persistence boundary shared with expense writers and enrichment. */
interface BackupPersistence {
    suspend fun snapshotForBackup(): KanakBackup
    suspend fun restoreValidatedBackup(backup: KanakBackup): RestoreOutcome
}

enum class RestoreOutcome {
    COMPLETE,
    EXPENSES_RESTORED_PREFERENCES_FAILED
}
