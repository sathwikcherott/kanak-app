package com.kanak.app.data

import com.kanak.app.data.backup.AutoBackupFrequency
import com.kanak.app.data.backup.AutomaticBackup
import com.kanak.app.data.backup.BackupDocument
import com.kanak.app.data.backup.KanakBackup
import com.kanak.app.data.backup.KanakBackupSerializer
import com.kanak.app.data.backup.WritableBackupDocumentStore
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticBackupTest {
    private val store = Store()
    private val folder = "backups"
    private val owner = "test-owner"
    private val zone = ZoneOffset.UTC
    private var now = at("2026-11-09")
    private val backup = AutomaticBackup(store) { KanakBackup(exportedAt = now) }

    @Test fun rotationKeepsThreeVerifiedBackupsAndPreservesOtherFiles() = runBlocking {
        store.add("manual", "Kanak_Backup_manual.json", now)
        backup.run(folder, "another-owner", AutoBackupFrequency.DAILY, now, zone)
        val other = store.documents.keys.last()
        val timestamps = (10..13).map { at("2026-11-$it") }
        for (timestamp in timestamps) {
            now = timestamp
            assertFalse(backup.run(folder, owner, AutoBackupFrequency.DAILY, now, zone).cleanupFailed)
        }
        val own = store.documents.values.filter { it.name.startsWith("Kanak_Auto_${owner}_") }
        assertEquals(timestamps.drop(1), own.map { KanakBackupSerializer.fromJson(store.read(it.id).decodeToString()).exportedAt })
        assertTrue("manual" in store.documents)
        assertTrue(other in store.documents)
        assertEquals(1, store.deleted.size)
        assertEquals(5, store.created)
        backup.run(folder, owner, AutoBackupFrequency.DAILY, now, zone)
        assertEquals(5, store.created)
    }

    @Test fun corruptWriteCannotDeletePreviousBackups() = runBlocking {
        for (day in 10..12) {
            now = at("2026-11-$day")
            backup.run(folder, owner, AutoBackupFrequency.DAILY, now, zone)
        }
        val before = store.documents.keys.toSet()
        now = at("2026-11-13")
        store.corruptWrites = true
        val error = runCatching { backup.run(folder, owner, AutoBackupFrequency.DAILY, now, zone) }
        assertTrue(error.isFailure)
        assertEquals(before, store.documents.keys.toSet())
        assertTrue(store.deleted.none { it in before })
    }

    @Test fun retentionCanRetryWithoutCreatingAnExtraBackup() = runBlocking {
        for (day in 10..12) {
            now = at("2026-11-$day")
            backup.run(folder, owner, AutoBackupFrequency.DAILY, now, zone)
        }
        now = at("2026-11-13")
        store.blockedDeletion = store.documents.keys.first()
        assertTrue(backup.run(folder, owner, AutoBackupFrequency.DAILY, now, zone).cleanupFailed)
        assertEquals(4, store.documents.size)
        store.blockedDeletion = null
        assertFalse(backup.run(folder, owner, AutoBackupFrequency.DAILY, now, zone).cleanupFailed)
        assertEquals(3, store.documents.size)
        assertEquals(4, store.created)
    }

    @Test fun frequenciesUseLocalCalendarAndFirstBackupIsDue() {
        for (frequency in AutoBackupFrequency.entries) assertTrue(frequency.isDue(null, now, zone))
        assertFalse(AutoBackupFrequency.DAILY.isDue(now, now + 12 * 60 * 60_000, zone))
        assertTrue(AutoBackupFrequency.DAILY.isDue(now, at("2026-11-10"), zone))
        assertFalse(AutoBackupFrequency.THREE_TIMES_A_WEEK.isDue(now, at("2026-11-10"), zone))
        assertTrue(AutoBackupFrequency.THREE_TIMES_A_WEEK.isDue(now, at("2026-11-11"), zone))
        assertFalse(AutoBackupFrequency.THREE_TIMES_A_WEEK.isDue(at("2026-11-13"), at("2026-11-15"), zone))
        assertTrue(AutoBackupFrequency.THREE_TIMES_A_WEEK.isDue(at("2026-11-13"), at("2026-11-16"), zone))
        assertFalse(AutoBackupFrequency.WEEKLY.isDue(now, at("2026-11-15"), zone))
        assertTrue(AutoBackupFrequency.WEEKLY.isDue(now, at("2026-11-16"), zone))
        assertFalse(AutoBackupFrequency.MONTHLY.isDue(at("2026-01-31"), at("2026-02-27"), zone))
        assertTrue(AutoBackupFrequency.MONTHLY.isDue(at("2026-01-31"), at("2026-02-28"), zone))
    }

    private fun at(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    private class Store : WritableBackupDocumentStore {
        val documents = linkedMapOf<String, BackupDocument>()
        private val bytes = mutableMapOf<String, ByteArray>()
        val deleted = mutableListOf<String>()
        var created = 0
        var corruptWrites = false
        var blockedDeletion: String? = null

        fun add(id: String, name: String, timestamp: Long) {
            documents[id] = BackupDocument(id, name, true)
            bytes[id] = KanakBackupSerializer.toJson(KanakBackup(exportedAt = timestamp)).encodeToByteArray()
        }

        override suspend fun children(folder: String) = documents.values.toList()

        override suspend fun create(folder: String, name: String): String {
            val id = (++created).toString()
            documents[id] = BackupDocument(id, name, true)
            return id
        }

        override suspend fun write(document: String, bytes: ByteArray) {
            this.bytes[document] = if (corruptWrites) byteArrayOf(0) else bytes
        }

        override suspend fun read(document: String) = bytes.getValue(document)

        override suspend fun delete(document: String): Boolean {
            if (document == blockedDeletion) return false
            documents.remove(document)
            bytes.remove(document)
            deleted += document
            return true
        }
    }
}
