package com.kanak.app.data.backup

import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface WritableBackupDocumentStore : BackupDocumentStore {
    suspend fun create(folder: String, name: String): String
    suspend fun write(document: String, bytes: ByteArray)
}

internal class AutomaticBackup(
    private val store: WritableBackupDocumentStore,
    private val createBackup: suspend () -> KanakBackup,
) {
    data class Run(val lastBackupAt: Long?, val cleanupFailed: Boolean)

    suspend fun run(
        folder: String,
        owner: String,
        frequency: AutoBackupFrequency,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Run {
        // An install-specific prefix separates automatic backups from manual and other-device files.
        val prefix = "Kanak_Auto_${owner}_"
        val pattern = Regex(Regex.escape(prefix) + """\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}-\d{3}_v\d+_[0-9a-f-]{36}\.json""")
        val catalog = BackupFileCatalog(store, pattern::matches)
        var files = catalog.scan(folder).files
        var latest = files.firstOrNull()?.exportedAt
        if (frequency.isDue(latest, now, zone)) {
            val backup = createBackup()
            val bytes = KanakBackupSerializer.toJson(backup).toByteArray(Charsets.UTF_8)
            KanakBackupSerializer.fromInputStream(bytes.inputStream())
            val readableName = BackupFileNames.create(backup.exportedAt, zone)
                .removePrefix("Kanak_Backup_").removeSuffix(".json")
            val id = store.create(folder, "$prefix${readableName}_${UUID.randomUUID()}.json")
            try {
                store.write(id, bytes)
                check(store.read(id).contentEquals(bytes)) { "The backup could not be verified." }
            } catch (e: Exception) {
                withContext(NonCancellable) { runCatching { store.delete(id) } }
                throw e
            }
            latest = backup.exportedAt
            files = try {
                catalog.scan(folder).files
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return Run(latest, true)
            }
            // Some document providers change filenames. Keep the new file, but don't prune
            // unless it was recognized and verified again alongside the older copies.
            if (files.none { it.document.id == id }) return Run(latest, true)
        }

        val old = files.drop(3)
        if (old.isEmpty()) return Run(latest, false)
        return try {
            val result = catalog.deleteSelected(folder, old, keep = 3)
            Run(latest, result.failed > 0 || result.protected > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Run(latest, true)
        }
    }
}
