package com.kanak.app.ui

import com.kanak.app.ui.entry.ExpenseDateOption
import com.kanak.app.ui.entry.ExpenseDateRules
import com.kanak.app.ui.entry.QuickEntryUiState
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class ExpenseDateRulesTest {
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun localMidnightUsesLocalDateForPickerSelectionAndLimit() {
        val now = time("2026-11-05T19:00:00Z")
        val kolkata = TimeZone.getTimeZone("Asia/Kolkata")
        val newYork = TimeZone.getTimeZone("America/New_York")
        val picker = time("2026-11-06T00:00:00Z")
        assertEquals(picker, ExpenseDateRules.localMillisToPickerUtc(now, kolkata))
        assertTrue(ExpenseDateRules.isPickerDateSelectable(picker, now, kolkata))
        assertFalse(ExpenseDateRules.isPickerDateSelectable(picker, now, newYork))
    }

    @Test fun yesterdayUsesSaveTimeAcrossMidnightAndYearBoundary() {
        val state = QuickEntryUiState(selectedDateOption = ExpenseDateOption.YESTERDAY,
            selectedDateMillis = time("2025-12-30T23:59:00Z"))
        assertEquals(time("2025-12-31T00:01:00Z"), ExpenseDateRules.resolveOccurredAt(
            state, time("2026-01-01T00:01:00Z"), utc))
    }

    @Test fun yesterdayHandlesLeapDay() {
        assertEquals(time("2024-02-29T12:00:00Z"), ExpenseDateRules.resolveOccurredAt(
            QuickEntryUiState(selectedDateOption = ExpenseDateOption.YESTERDAY),
            time("2024-03-01T12:00:00Z"), utc))
    }

    @Test fun chosenDateRetainsCalendarDateButUsesSaveTime() {
        val state = QuickEntryUiState(selectedDateOption = ExpenseDateOption.CHOSEN_DATE,
            selectedDateMillis = time("2026-10-15T12:00:00Z"))
        assertEquals(time("2026-10-15T13:30:00Z"), ExpenseDateRules.resolveOccurredAt(
            state, time("2026-11-07T13:30:00Z"), utc))
    }

    @Test fun todayUsesActualSaveInstant() {
        val now = time("2026-11-06T12:34:56Z")
        assertEquals(now, ExpenseDateRules.resolveOccurredAt(QuickEntryUiState(), now, utc))
    }

    @Test fun pickerRoundTripsCalendarDateInBothOffsetDirections() {
        val picker = time("2024-05-24T00:00:00Z")
        for (zone in listOf("Asia/Kolkata", "America/New_York")) {
            val tz = TimeZone.getTimeZone(zone)
            val local = ExpenseDateRules.convertPickerUtcToLocalMillis(picker,
                { time("2024-05-25T12:30:00Z") }, tz)
            assertEquals(picker, ExpenseDateRules.localMillisToPickerUtc(local, tz))
        }
    }

    @Test fun dstGapMovesToNextValidLocalTimeOnSelectedDate() {
        val zone = TimeZone.getTimeZone("America/New_York")
        // Save at 02:30 EDT on March 11; March 10 has no 02:30 local time.
        val result = ExpenseDateRules.resolveOccurredAt(
            QuickEntryUiState(selectedDateOption = ExpenseDateOption.YESTERDAY),
            time("2024-03-11T06:30:00Z"), zone)
        assertEquals(time("2024-03-10T07:30:00Z"), result) // 03:30 EDT
    }

    @Test fun futureComparisonUsesCalendarDayRatherThanTimeOfDay() {
        val now = time("2026-11-06T12:00:00Z")
        assertFalse(ExpenseDateRules.isDateInFuture(time("2026-11-06T23:59:00Z"), { now }, utc))
        assertTrue(ExpenseDateRules.isDateInFuture(time("2026-11-07T00:00:00Z"), { now }, utc))
    }
}
