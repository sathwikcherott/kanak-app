package com.kanak.app.backup

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.util.UUID

/** SAF protocol fixture, isolated in the instrumentation APK's cache. No real user files. */
class TestBackupDocumentsProvider : DocumentsProvider() {
    private val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS)
    private val root get() = File(context!!.cacheDir, "test-backup-documents").apply { mkdirs() }
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf("root_id", "document_id", "title", "flags")).apply { addRow(arrayOf("root", "root", "Test backups", 0)) }
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = cursor(listOf(documentId), projection)
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        require(parentDocumentId == "root")
        return cursor(root.listFiles()?.map { it.name }.orEmpty(), projection)
    }
    private fun cursor(ids: List<String>, projection: Array<out String>?): Cursor {
        val names = projection ?: columns
        return MatrixCursor(names).apply {
            for (id in ids) {
                val directory = id == "root" || file(id).isDirectory
                val display = if (id == "root") "Test backups" else id.substringAfter('_')
                addRow(names.map { column -> when (column) {
                    Document.COLUMN_DOCUMENT_ID -> id
                    Document.COLUMN_DISPLAY_NAME -> display
                    Document.COLUMN_MIME_TYPE -> if (directory) Document.MIME_TYPE_DIR else "application/json"
                    Document.COLUMN_FLAGS -> Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_WRITE or (if (directory) Document.FLAG_DIR_SUPPORTS_CREATE else 0)
                    else -> null
                } }.toTypedArray())
            }
        }
    }
    private fun file(id: String): File {
        require(!id.contains('/') && !id.contains(".."))
        return if (id == "root") root else File(root, id)
    }
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        require(parentDocumentId == "root")
        val id = "${UUID.randomUUID()}_$displayName"
        if (mimeType == Document.MIME_TYPE_DIR) check(file(id).mkdir()) else check(file(id).createNewFile())
        return id
    }
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = parentDocumentId == "root" && documentId != "root" && file(documentId).exists()
    override fun deleteDocument(documentId: String) {
        require(documentId != "root")
        check(file(documentId).delete())
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
}
