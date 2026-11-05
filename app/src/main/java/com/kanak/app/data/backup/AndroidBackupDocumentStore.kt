package com.kanak.app.data.backup

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidBackupDocumentStore(private val resolver: ContentResolver) : BackupDocumentStore {
    constructor(context: Context) : this(context.applicationContext.contentResolver)

    override suspend fun children(folder: String): List<BackupDocument> = withContext(Dispatchers.IO) {
        val tree = Uri.parse(folder)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val result = mutableListOf<BackupDocument>()
        resolver.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                require(++count <= 500) { "This folder has more than 500 items. Choose a dedicated backup folder." }
                if (cursor.getString(2) == Document.MIME_TYPE_DIR) continue
                val id = cursor.getString(0) ?: continue
                result += BackupDocument(
                    DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                    cursor.getString(1) ?: "",
                    cursor.getInt(3) and Document.FLAG_SUPPORTS_DELETE != 0
                )
            }
        } ?: error("Cannot read this folder. Choose it again to grant access.")
        result
    }

    override suspend fun read(document: String): ByteArray = withContext(Dispatchers.IO) {
        resolver.openInputStream(Uri.parse(document))?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > KanakBackup.MAX_BACKUP_BYTES) {
                    throw BackupException.LimitExceeded("Backup exceeds 10 MiB")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Cannot open this file")
    }

    override suspend fun delete(document: String): Boolean = withContext(Dispatchers.IO) {
        DocumentsContract.deleteDocument(resolver, Uri.parse(document))
    }
}
