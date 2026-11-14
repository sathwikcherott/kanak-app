package com.kanak.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.kanak.app.data.local.datastore.UserPreferencesDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface BackupFolderAccess {
    suspend fun currentFolder(): String?
    suspend fun chooseFolder(folder: String)
    suspend fun name(folder: String): String
}

class AndroidBackupFolderAccess(context: Context, private val preferences: UserPreferencesDataSource) : BackupFolderAccess {
    internal val changeLock = Mutex()
    private val resolver = context.applicationContext.contentResolver
    override suspend fun currentFolder(): String? = preferences.backupFolderUri.first()

    override suspend fun chooseFolder(folder: String) = withContext(Dispatchers.IO) {
        changeLock.withLock {
            val uri = Uri.parse(folder)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val old = currentFolder()
            resolver.takePersistableUriPermission(uri, flags)
            preferences.setBackupFolderUri(folder)
            if (old != null && old != folder) {
                try { resolver.releasePersistableUriPermission(Uri.parse(old), flags) } catch (_: SecurityException) { }
            }
        }
    }

    override suspend fun name(folder: String): String = withContext(Dispatchers.IO) {
        val tree = Uri.parse(folder)
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) ?: "Backup folder" else "Backup folder"
        } ?: "Backup folder"
    }
}
