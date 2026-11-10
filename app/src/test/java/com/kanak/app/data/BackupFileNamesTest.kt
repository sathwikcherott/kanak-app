package com.kanak.app.data

import com.kanak.app.data.backup.BackupFileNames
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class BackupFileNamesTest {
    @Test fun namesUseReadableLocalDateMillisecondsAndVersion() {
        assertEquals("Kanak_Backup_2026-11-06_02-30-15-123_v3.json", BackupFileNames.create(Instant.parse("2026-11-05T21:00:15.123Z").toEpochMilli(), ZoneId.of("Asia/Kolkata")))
    }
    @Test fun namesSortByCreationTimeAndAvoidSameSecondCollisions() {
        val first = BackupFileNames.create(1000L, ZoneId.of("UTC"))
        val second = BackupFileNames.create(1001L, ZoneId.of("UTC"))
        assertTrue(first < second)
        assertNotEquals(first, second)
        assertFalse(first.contains(':'))
    }
    @Test fun namingDoesNotDependOnDeviceLanguage() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("Kanak_Backup_1970-01-01_00-00-00-000_v3.json", BackupFileNames.create(0L, ZoneId.of("UTC")))
        } finally { Locale.setDefault(old) }
    }
    @Test fun legacyAndNewBackupNamesAreRecognizedButUnrelatedFilesAreNot() {
        assertTrue(BackupFileNames.isCandidate("kanak_backup_20261106_023015.json"))
        assertTrue(BackupFileNames.isCandidate("Kanak_Backup_2026-11-06_02-30-15-123_v1.json"))
        assertFalse(BackupFileNames.isCandidate("expenses.json"))
        assertFalse(BackupFileNames.isCandidate("kanak_backup_photo.jpg"))
    }
}
