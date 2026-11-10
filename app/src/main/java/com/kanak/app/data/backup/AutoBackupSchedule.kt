package com.kanak.app.data.backup

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

enum class AutoBackupFrequency(val label: String) {
    DAILY("Daily"),
    THREE_TIMES_A_WEEK("Three times a week (Mon, Wed, Fri)"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    fun isDue(lastBackupAt: Long?, now: Long, zone: ZoneId): Boolean {
        if (lastBackupAt == null) return true
        val previous = Instant.ofEpochMilli(lastBackupAt).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val due = when (this) {
            DAILY -> previous.plusDays(1)
            THREE_TIMES_A_WEEK -> generateSequence(previous.plusDays(1)) { it.plusDays(1) }
                .first { it.dayOfWeek in backupDays }
            WEEKLY -> previous.plusWeeks(1)
            MONTHLY -> previous.plusMonths(1)
        }
        return !today.isBefore(due)
    }

    private companion object {
        val backupDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    }
}

data class AutoBackupSettings(
    val enabled: Boolean = false,
    val frequency: AutoBackupFrequency = AutoBackupFrequency.DAILY,
    val folder: String? = null,
    val owner: String? = null,
    val lastBackupAt: Long? = null,
    val warning: String? = null,
)
