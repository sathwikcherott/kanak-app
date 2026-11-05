package com.kanak.app.ui.entry

import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

internal object ExpenseDateRules {
        fun convertPickerUtcToLocalMillis(
            utcMillis: Long,
            timeProvider: () -> Long = { System.currentTimeMillis() },
            timeZone: TimeZone = TimeZone.getDefault()
        ): Long {
            val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val now = Instant.ofEpochMilli(timeProvider()).atZone(timeZone.toZoneId())
            return date.atTime(now.toLocalTime()).atZone(timeZone.toZoneId()).toInstant().toEpochMilli()
        }

        fun localMillisToPickerUtc(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
            Instant.ofEpochMilli(millis).atZone(timeZone.toZoneId()).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        fun isPickerDateSelectable(
            utcMillis: Long,
            nowMillis: Long = System.currentTimeMillis(),
            timeZone: TimeZone = TimeZone.getDefault()
        ): Boolean = !Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .isAfter(Instant.ofEpochMilli(nowMillis).atZone(timeZone.toZoneId()).toLocalDate())

        fun isDateInFuture(
            targetMillis: Long,
            timeProvider: () -> Long = { System.currentTimeMillis() },
            timeZone: TimeZone = TimeZone.getDefault()
        ): Boolean = Instant.ofEpochMilli(targetMillis).atZone(timeZone.toZoneId()).toLocalDate()
            .isAfter(Instant.ofEpochMilli(timeProvider()).atZone(timeZone.toZoneId()).toLocalDate())

        fun resolveOccurredAt(state: QuickEntryUiState, nowMillis: Long, timeZone: TimeZone): Long {
            val zone = timeZone.toZoneId()
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val date = when (state.selectedDateOption) {
                ExpenseDateOption.TODAY -> return nowMillis
                // Relative shortcuts remain relative if an open draft crosses midnight.
                ExpenseDateOption.YESTERDAY -> now.toLocalDate().minusDays(1)
                ExpenseDateOption.CHOSEN_DATE -> Instant.ofEpochMilli(state.selectedDateMillis)
                    .atZone(zone).toLocalDate()
            }
            // java.time moves gap times forward and uses the earlier offset for overlaps.
            return date.atTime(now.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        }
    }

