package com.kanak.app.ui

import com.kanak.app.data.backup.*
import com.kanak.app.ui.settings.BackupFilesViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupFilesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = Store()
    private val folders = Folders()
    private lateinit var vm: BackupFilesViewModel
    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = BackupFilesViewModel(BackupFileCatalog(store), folders, dispatcher)
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun noFolderDoesNotRequestOrDeleteAnything() = runTest(dispatcher) {
        folders.folder = null
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.uiState.value.folder)
        assertFalse(vm.uiState.value.busy)
        assertEquals(0, store.deletes)
    }

    @Test fun cancelledConfirmationDoesNotDeleteAndNewestCannotBeSelected() = runTest(dispatcher) {
        vm.refresh(); advanceUntilIdle()
        vm.toggle("4")
        assertTrue(vm.uiState.value.selected.isEmpty())
        vm.selectOlder()
        assertEquals(setOf("1"), vm.uiState.value.selected)
        vm.requestDeletion()
        assertEquals(1, vm.uiState.value.confirmation.size)
        assertEquals(0, store.deletes)
        vm.cancelDeletion()
        vm.confirmDeletion(); advanceUntilIdle()
        assertEquals(0, store.deletes)
        assertFalse(vm.uiState.value.busy)
    }

    @Test fun repeatedConfirmationStartsOneDeleteAndRefreshesList() = runTest(dispatcher) {
        vm.refresh(); advanceUntilIdle()
        vm.selectOlder(); vm.requestDeletion()
        store.gate = CompletableDeferred()
        vm.confirmDeletion(); vm.confirmDeletion(); vm.cancelDeletion()
        runCurrent()
        assertTrue(vm.uiState.value.busy)
        assertTrue(vm.uiState.value.confirmation.isNotEmpty())
        store.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, store.deletes)
        assertEquals(3, vm.uiState.value.files.size)
        assertTrue(vm.uiState.value.confirmation.isEmpty())
        assertTrue(vm.uiState.value.selected.isEmpty())
        assertFalse(vm.uiState.value.busy)
        assertTrue(vm.uiState.value.message!!.contains("Deleted 1"))
    }

    @Test fun revokedFolderAccessResetsBusyAndOffersActionableError() = runTest(dispatcher) {
        store.revoked = true
        vm.refresh(); advanceUntilIdle()
        assertFalse(vm.uiState.value.busy)
        assertTrue(vm.uiState.value.files.isEmpty())
        assertTrue(vm.uiState.value.error!!.contains("Choose the folder again"))
        assertEquals(0, store.deletes)
    }

    @Test fun folderCannotChangeDuringPendingConfirmation() = runTest(dispatcher) {
        vm.refresh(); advanceUntilIdle()
        vm.selectOlder(); vm.requestDeletion()
        vm.chooseFolder("other"); advanceUntilIdle()
        assertEquals("folder", folders.folder)
        assertEquals(0, store.deletes)
    }

    private class Folders : BackupFolderAccess {
        var folder: String? = "folder"
        override suspend fun currentFolder() = folder
        override suspend fun chooseFolder(folder: String) { this.folder = folder }
        override suspend fun name(folder: String) = "Kanak"
    }
    private class Store : BackupDocumentStore {
        private val files = (1..4).associate { "$it" to BackupDocument("$it", "kanak_backup_$it.json", true) }.toMutableMap()
        var deletes = 0
        var revoked = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun children(folder: String): List<BackupDocument> {
            gate?.await()
            if (revoked) throw SecurityException("Revoked")
            return files.values.toList()
        }
        override suspend fun read(document: String) = KanakBackupSerializer.toJson(KanakBackup(exportedAt = document.toLong() * 1000)).toByteArray()
        override suspend fun delete(document: String): Boolean {
            deletes++
            return files.remove(document) != null
        }
    }
}
