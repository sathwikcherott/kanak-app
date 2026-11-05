package com.kanak.app.data.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object BackupFileNames {
    // Local creation time, milliseconds to avoid routine duplicate-name suffixes, schema version.
    fun create(timestamp: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val time = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS", Locale.ROOT)
            .format(Instant.ofEpochMilli(timestamp).atZone(zone))
        return "Kanak_Backup_${time}_v${KanakBackup.CURRENT_BACKUP_VERSION}.json"
    }

    fun isCandidate(name: String): Boolean = Regex("^kanak[_-]backup[_-].+\\.json$", RegexOption.IGNORE_CASE).matches(name)
}
