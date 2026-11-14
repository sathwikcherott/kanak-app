package com.kanak.app.data.backup

import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Document IDs are opaque; the store must limit enumeration to immediate children of the chosen folder. */
interface BackupDocumentStore {
    suspend fun children(folder: String): List<BackupDocument>
    suspend fun read(document: String): ByteArray
    suspend fun delete(document: String): Boolean
}

data class BackupDocument(val id: String, val name: String, val canDelete: Boolean)
data class BackupFile(val document: BackupDocument, val exportedAt: Long, val bytes: Int, val fingerprint: String)
data class BackupFileListing(val files: List<BackupFile>, val skipped: Int)
data class BackupDeletionResult(val deleted: Int, val failed: Int, val protected: Int)

class BackupFileCatalog(
    private val store: BackupDocumentStore,
    private val acceptsName: (String) -> Boolean = BackupFileNames::isCandidate,
) {
    suspend fun scan(folder: String): BackupFileListing {
        val candidates = store.children(folder).filter { acceptsName(it.name) }
        require(candidates.size <= MAX_BACKUPS) { "This folder has more than $MAX_BACKUPS backup files. Choose a smaller backup folder." }
        val files = mutableListOf<BackupFile>()
        var skipped = 0
        for (document in candidates) {
            currentCoroutineContext().ensureActive()
            try {
                val bytes = store.read(document.id)
                val backup = KanakBackupSerializer.fromInputStream(bytes.inputStream())
                files += BackupFile(document, backup.exportedAt, bytes.size, fingerprint(bytes))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                throw e
            } catch (_: Exception) {
                skipped++ // Invalid, oversized or unreadable files are never deletion candidates.
            }
        }
        return BackupFileListing(files.sortedWith(compareByDescending<BackupFile> { it.exportedAt }.thenBy { it.document.id }), skipped)
    }

    /** Recheck membership, content and retention at execution time, not just when the dialog was opened. */
    suspend fun deleteSelected(folder: String, selected: List<BackupFile>, keep: Int = 1): BackupDeletionResult {
        val current = scan(folder).files
        val protectedIds = current.take(keep.coerceAtLeast(1)).map { it.document.id }.toSet()
        val byId = current.associateBy { it.document.id }
        var deleted = 0
        var failed = 0
        var protected = 0
        for (file in selected.distinctBy { it.document.id }) {
            currentCoroutineContext().ensureActive()
            val id = file.document.id
            val latest = byId[id]
            if (id in protectedIds) {
                protected++ // Retain the required newest verified backups.
                continue
            }
            if (latest == null || !latest.document.canDelete || latest.fingerprint != file.fingerprint || latest.document.name != file.document.name) {
                failed++
                continue
            }
            try {
                // A provider may change a file after enumeration. Never intentionally delete changed data.
                if (fingerprint(store.read(id)) != file.fingerprint) failed++
                else if (store.delete(id)) deleted++ else failed++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed++
            }
        }
        return BackupDeletionResult(deleted, failed, protected)
    }

    companion object {
        const val MAX_BACKUPS = 100
        fun olderFiles(files: List<BackupFile>, keep: Int = 3): Set<String> =
            files.drop(keep.coerceAtLeast(1)).filter { it.document.canDelete }.map { it.document.id }.toSet()
        private fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
