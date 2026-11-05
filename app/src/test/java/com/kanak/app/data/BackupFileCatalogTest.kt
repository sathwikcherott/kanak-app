package com.kanak.app.data

import com.kanak.app.data.backup.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackupFileCatalogTest {
    private val store = Store()
    private val catalog = BackupFileCatalog(store)

    @Test fun scanVerifiesContentAndSortsNewestFirstWithoutDeletingAnything() = runTest {
        store.add("old", 1000L)
        store.add("new", 2000L)
        store.documents["fake"] = BackupDocument("fake", "kanak_backup_fake.json", true)
        store.bytes["fake"] = "not a backup".toByteArray()
        store.documents["other"] = BackupDocument("other", "notes.json", true)
        store.bytes["other"] = "private unrelated notes".toByteArray()
        val result = catalog.scan("folder")
        assertEquals(listOf("new", "old"), result.files.map { it.document.id })
        assertEquals(1, result.skipped)
        assertTrue(store.deleted.isEmpty())
    }

    @Test fun selectOlderKeepsThreeAndSkipsReadOnlyDocuments() = runTest {
        for (i in 1..6) store.add("$i", i * 1000L, canDelete = i != 2)
        val files = catalog.scan("folder").files
        assertEquals(setOf("3", "1"), BackupFileCatalog.olderFiles(files))
        assertFalse(BackupFileCatalog.olderFiles(files, 0).contains("6"))
    }

    @Test fun deletionOnlyTouchesConfirmedFilesAndRetainsNewest() = runTest {
        for (i in 1..4) store.add("$i", i * 1000L)
        val files = catalog.scan("folder").files
        val result = catalog.deleteSelected("folder", files.filter { it.document.id != "2" })
        assertEquals(BackupDeletionResult(2, 0, 1), result)
        assertEquals(setOf("1", "3"), store.deleted.toSet())
        assertTrue(store.documents.containsKey("4"))
        assertTrue(store.documents.containsKey("2"))
    }

    @Test fun changedOrRenamedFileAfterPreviewIsNeverDeleted() = runTest {
        store.add("new", 4000L)
        store.add("changed", 1000L)
        store.add("renamed", 2000L)
        val preview = catalog.scan("folder").files.drop(1)
        store.add("changed", 1500L)
        store.documents["renamed"] = store.documents.getValue("renamed").copy(name = "Kanak_Backup_renamed.json")
        assertEquals(BackupDeletionResult(0, 2, 0), catalog.deleteSelected("folder", preview))
        assertTrue(store.deleted.isEmpty())
    }

    @Test fun fileOutsideChosenFolderAndReadOnlyFileCannotBeDeleted() = runTest {
        store.add("new", 4000L)
        store.add("old", 1000L)
        store.add("readonly", 2000L, false)
        val preview = catalog.scan("folder").files.drop(1)
        store.documents.remove("old")
        assertEquals(BackupDeletionResult(0, 2, 0), catalog.deleteSelected("folder", preview))
        assertTrue(store.deleted.isEmpty())
    }

    @Test fun revalidationProtectsLastRemainingBackupAfterOtherFileIsRemoved() = runTest {
        store.add("new", 2000L)
        store.add("old", 1000L)
        val selected = catalog.scan("folder").files.drop(1)
        store.documents.remove("new")
        assertEquals(BackupDeletionResult(0, 0, 1), catalog.deleteSelected("folder", selected))
        assertTrue(store.deleted.isEmpty())
    }

    @Test fun providerDeleteFailuresProduceAccuratePartialCountsWithoutRetries() = runTest {
        for (i in 1..4) store.add("$i", i * 1000L)
        store.refuseDelete = "2"
        val selected = catalog.scan("folder").files.drop(1)
        assertEquals(BackupDeletionResult(2, 1, 0), catalog.deleteSelected("folder", selected + selected))
        assertEquals(listOf("3", "1"), store.deleted)
    }

    @Test fun secondContentCheckCatchesChangesDuringDeletionScan() = runTest {
        store.add("new", 2000L)
        store.add("old", 1000L)
        val selected = catalog.scan("folder").files.drop(1)
        store.onRead = { id, count -> if (id == "old" && count == 3) store.add("old", 1500L) }
        assertEquals(BackupDeletionResult(0, 1, 0), catalog.deleteSelected("folder", selected))
        assertTrue(store.deleted.isEmpty())
    }

    @Test fun candidateLimitDoesNotOfferIncompleteCleanup() = runTest {
        repeat(101) { store.add("$it", it + 1000L) }
        try { catalog.scan("folder"); fail("Expected limit") } catch (_: IllegalArgumentException) { }
        assertTrue(store.deleted.isEmpty())
    }

    @Test fun revokedPermissionPropagatesAndDoesNotDeleteFiles() = runTest {
        store.add("old", 1000L)
        store.onRead = { _, _ -> throw SecurityException("Revoked") }
        try { catalog.scan("folder"); fail("Expected permission error") } catch (_: SecurityException) { }
        assertTrue(store.deleted.isEmpty())
    }

    @Test fun cancellationPropagatesWithoutDeleting() = runTest {
        store.add("old", 1000L)
        store.onRead = { _, _ -> throw CancellationException("Cancelled") }
        try { catalog.scan("folder"); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertTrue(store.deleted.isEmpty())
    }

    private class Store : BackupDocumentStore {
        val documents = linkedMapOf<String, BackupDocument>()
        val bytes = mutableMapOf<String, ByteArray>()
        val deleted = mutableListOf<String>()
        val reads = mutableMapOf<String, Int>()
        var refuseDelete: String? = null
        var onRead: (String, Int) -> Unit = { _, _ -> }
        fun add(id: String, time: Long, canDelete: Boolean = true) {
            documents[id] = BackupDocument(id, "kanak_backup_$id.json", canDelete)
            bytes[id] = KanakBackupSerializer.toJson(KanakBackup(exportedAt = time)).toByteArray()
        }
        override suspend fun children(folder: String) = documents.values.toList()
        override suspend fun read(document: String): ByteArray {
            val count = reads.getOrDefault(document, 0) + 1
            reads[document] = count
            onRead(document, count)
            return bytes.getValue(document)
        }
        override suspend fun delete(document: String): Boolean {
            if (document == refuseDelete) return false
            documents.remove(document)
            bytes.remove(document)
            deleted += document
            return true
        }
    }
}
