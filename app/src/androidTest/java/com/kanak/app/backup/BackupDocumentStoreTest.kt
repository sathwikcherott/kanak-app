package com.kanak.app.backup

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.kanak.app.data.backup.*
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackupDocumentStoreTest {
    @Test fun selectedFolderListsAndDeletesOnlyVerifiedOlderBackupsThroughDocumentsContract() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = TestBackupDocumentsProvider()
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply {
            authority = "com.kanak.app.test.backupdocuments"
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        })
        val resolver = android.content.ContentResolver.wrap(provider)
        val tree = Uri.parse("content://com.kanak.app.test.backupdocuments/tree/root")
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, "root")
        val files = mutableListOf<Uri>()
        fun create(name: String, text: String, mime: String = "application/json"): Uri {
            val uri = requireNotNull(DocumentsContract.createDocument(resolver, root, mime, name))
            files += uri
            if (mime != DocumentsContract.Document.MIME_TYPE_DIR) resolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            return uri
        }
        try {
            val old = create("kanak_backup_old.json", KanakBackupSerializer.toJson(KanakBackup(exportedAt = 1000L)))
            val newest = create("Kanak_Backup_2026-11-06_02-30-15-123_v1.json", KanakBackupSerializer.toJson(KanakBackup(exportedAt = 2000L)))
            val unrelated = create("notes.json", "Personal notes")
            val invalid = create("kanak_backup_corrupt.json", "{invalid")
            create("kanak_backup_nested.json", "", DocumentsContract.Document.MIME_TYPE_DIR)
            val store = AndroidBackupDocumentStore(resolver)
            val catalog = BackupFileCatalog(store)
            val listing = catalog.scan(tree.toString())
            assertEquals(2, listing.files.size)
            assertEquals(1, listing.skipped)
            assertEquals(newest.toString(), listing.files.first().document.id)
            assertEquals(BackupDeletionResult(1, 0, 1), catalog.deleteSelected(tree.toString(), listing.files))
            assertEquals(1, catalog.scan(tree.toString()).files.size)
            assertEquals("Personal notes", resolver.openInputStream(unrelated)!!.bufferedReader().use { it.readText() })
            assertEquals("{invalid", resolver.openInputStream(invalid)!!.bufferedReader().use { it.readText() })
            assertTrue(store.children(tree.toString()).none { it.id == old.toString() })

            val owner = UUID.randomUUID().toString()
            var timestamp = 0L
            val automatic = AutomaticBackup(store) { KanakBackup(exportedAt = timestamp) }
            try {
                for (day in 1L..4L) {
                    timestamp = day * 86_400_000
                    assertFalse(automatic.run(tree.toString(), owner, AutoBackupFrequency.DAILY, timestamp, ZoneOffset.UTC).cleanupFailed)
                }
                val kept = store.children(tree.toString()).filter { it.name.startsWith("Kanak_Auto_${owner}_") }
                assertEquals(
                    listOf(2L, 3L, 4L).map { it * 86_400_000 },
                    kept.map { KanakBackupSerializer.fromInputStream(store.read(it.id).inputStream()).exportedAt }.sorted()
                )
                assertEquals(newest.toString(), catalog.scan(tree.toString()).files.single().document.id)
            } finally {
                store.children(tree.toString()).filter { it.name.startsWith("Kanak_Auto_${owner}_") }
                    .forEach { store.delete(it.id) }
            }
        } finally {
            for (uri in files) try { DocumentsContract.deleteDocument(resolver, uri) } catch (_: Exception) { }
        }
    }
}
