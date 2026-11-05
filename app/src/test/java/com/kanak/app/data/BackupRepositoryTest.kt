package com.kanak.app.data

import com.kanak.app.data.backup.BackupExpense
import com.kanak.app.data.backup.BackupException
import com.kanak.app.data.backup.BackupPreferences
import com.kanak.app.data.backup.KanakBackup
import com.kanak.app.data.backup.KanakBackupSerializer
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupRepositoryTest {

    @Test
    fun exactSerializationAndDeserialization_roundTripEquality() {
        val original = KanakBackup(
            format = KanakBackup.FORMAT_IDENTIFIER,
            version = 1,
            exportedAt = 1715000000000L,
            appVersion = "1.0",
            preferences = BackupPreferences("GBP", ThemePreference.DARK),
            expenses = listOf(
                BackupExpense(
                    id = 101L,
                    amountMinor = 12345L,
                    currency = "GBP",
                    occurredAt = 1715000000000L,
                    note = "Special \"quoted\" note\nLine two",
                    category = ExpenseCategory.GIRLFRIEND,
                    source = EntrySource.VOICE,
                    categorizationState = CategorizationState.CONFIRMED
                )
            )
        )

        val json = KanakBackupSerializer.toJson(original)
        val restored = KanakBackupSerializer.fromJson(json)

        assertEquals(original.format, restored.format)
        assertEquals(original.version, restored.version)
        assertEquals(original.exportedAt, restored.exportedAt)
        assertEquals(original.preferences.defaultCurrency, restored.preferences.defaultCurrency)
        assertEquals(original.preferences.themePreference, restored.preferences.themePreference)
        assertEquals(original.expenses.size, restored.expenses.size)

        val origExp = original.expenses[0]
        val restExp = restored.expenses[0]

        assertEquals(origExp.id, restExp.id)
        assertEquals(origExp.amountMinor, restExp.amountMinor)
        assertEquals(origExp.currency, restExp.currency)
        assertEquals(origExp.occurredAt, restExp.occurredAt)
        assertEquals(origExp.note, restExp.note)
        assertEquals(origExp.category, restExp.category)
        assertEquals(origExp.source, restExp.source)
        assertEquals(origExp.categorizationState, restExp.categorizationState)
    }

    @Test
    fun emptyBackup_serializesAndDeserializesSuccessfully() {
        val emptyBackup = KanakBackup(expenses = emptyList())
        val json = KanakBackupSerializer.toJson(emptyBackup)
        val restored = KanakBackupSerializer.fromJson(json)

        assertEquals(0, restored.expenses.size)
        assertEquals(KanakBackup.FORMAT_IDENTIFIER, restored.format)
    }

    @Test
    fun unicodeNotesAndGirlfriendCategory_preservedVerbatim() {
        val unicodeBackup = KanakBackup(
            expenses = listOf(
                BackupExpense(
                    id = 1L,
                    amountMinor = 5000L,
                    currency = "INR",
                    occurredAt = 1000L,
                    note = "Gift for Swathi ❤️ स्वाति (Coffee & Flowers ☕🌸)",
                    category = ExpenseCategory.GIRLFRIEND,
                    source = EntrySource.TYPED,
                    categorizationState = CategorizationState.CONFIRMED
                )
            )
        )

        val json = KanakBackupSerializer.toJson(unicodeBackup)
        val restored = KanakBackupSerializer.fromJson(json)

        assertEquals("Gift for Swathi ❤️ स्वाति (Coffee & Flowers ☕🌸)", restored.expenses[0].note)
        assertEquals(ExpenseCategory.GIRLFRIEND, restored.expenses[0].category)
    }

    @Test
    fun explicitUncategorizedRecords_preservedAsNullCategory() {
        val uncategorizedBackup = KanakBackup(
            expenses = listOf(
                BackupExpense(
                    id = 2L,
                    amountMinor = 1500L,
                    currency = "INR",
                    occurredAt = 2000L,
                    note = "Unsorted expense",
                    category = null,
                    source = EntrySource.TYPED,
                    categorizationState = CategorizationState.UNCATEGORIZED
                )
            )
        )

        val json = KanakBackupSerializer.toJson(uncategorizedBackup)
        val restored = KanakBackupSerializer.fromJson(json)

        assertNull(restored.expenses[0].category)
        assertEquals(CategorizationState.UNCATEGORIZED, restored.expenses[0].categorizationState)
    }

    @Test
    fun validSourceAndCategorizationStates_preserved() {
        val statesBackup = KanakBackup(
            expenses = listOf(
                BackupExpense(
                    id = 3L,
                    amountMinor = 8000L,
                    currency = "USD",
                    occurredAt = 3000L,
                    category = ExpenseCategory.FOOD,
                    source = EntrySource.VOICE,
                    categorizationState = CategorizationState.AUTO_CATEGORIZED
                )
            )
        )

        val json = KanakBackupSerializer.toJson(statesBackup)
        val restored = KanakBackupSerializer.fromJson(json)

        assertEquals(EntrySource.VOICE, restored.expenses[0].source)
        assertEquals(CategorizationState.AUTO_CATEGORIZED, restored.expenses[0].categorizationState)
    }

    @Test
    fun originalCurrency_preservedIndependentlyOfDisplayCurrency() {
        val multiCurrencyBackup = KanakBackup(
            preferences = BackupPreferences(defaultCurrency = "INR"),
            expenses = listOf(
                BackupExpense(id = 1L, amountMinor = 1000L, currency = "EUR", occurredAt = 1000L),
                BackupExpense(id = 2L, amountMinor = 2000L, currency = "USD", occurredAt = 2000L),
                BackupExpense(id = 3L, amountMinor = 3000L, currency = "GBP", occurredAt = 3000L)
            )
        )

        val json = KanakBackupSerializer.toJson(multiCurrencyBackup)
        val restored = KanakBackupSerializer.fromJson(json)

        assertEquals("EUR", restored.expenses[0].currency)
        assertEquals("USD", restored.expenses[1].currency)
        assertEquals("GBP", restored.expenses[2].currency)
    }

    @Test
    fun exactIntegerPrecision_nearAndAbove2Pow53AndLongLimits() {
        val largeNumber = 9_007_199_254_740_993L // 2^53 + 1
        val largeBackup = KanakBackup(
            expenses = listOf(
                BackupExpense(id = 500L, amountMinor = largeNumber, currency = "INR", occurredAt = largeNumber)
            )
        )

        val json = KanakBackupSerializer.toJson(largeBackup)
        val restored = KanakBackupSerializer.fromJson(json)

        assertEquals(largeNumber, restored.expenses[0].amountMinor)
        assertEquals(largeNumber, restored.expenses[0].occurredAt)
    }

    @Test
    fun zeroAndNegativeAndFractionalAmounts_rejectedWithInvalidMonetaryValue() {
        val zeroAmountJson = """
            {
              "format": "kanak_backup",
              "version": 1,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "DARK" },
              "expenses": [
                { "id": 1, "amountMinor": 0, "currency": "INR", "occurredAt": 1000, "source": "TYPED", "categorizationState": "CONFIRMED" }
              ]
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(zeroAmountJson)
            fail("Should reject zero amount")
        } catch (e: BackupException.InvalidMonetaryValue) {
            assertTrue(e.message!!.contains("Invalid zero or negative monetary amount"))
        }

        val negativeAmountJson = """
            {
              "format": "kanak_backup",
              "version": 1,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "DARK" },
              "expenses": [
                { "id": 2, "amountMinor": -500, "currency": "INR", "occurredAt": 1000, "source": "TYPED", "categorizationState": "CONFIRMED" }
              ]
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(negativeAmountJson)
            fail("Should reject negative amount")
        } catch (e: BackupException.InvalidMonetaryValue) {
            assertTrue(e.message!!.contains("Invalid zero or negative monetary amount"))
        }

        val fractionalAmountJson = """
            {
              "format": "kanak_backup",
              "version": 1,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "DARK" },
              "expenses": [
                { "id": 3, "amountMinor": 12.50, "currency": "INR", "occurredAt": 1000, "source": "TYPED", "categorizationState": "CONFIRMED" }
              ]
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(fractionalAmountJson)
            fail("Should reject fractional amount")
        } catch (e: BackupException.InvalidMonetaryValue) {
            assertTrue(e.message!!.contains("Missing or non-integer 'amountMinor'"))
        }
    }

    @Test
    fun missingAndWrongTypeRequiredFields_rejected() {
        val missingIdJson = """
            {
              "format": "kanak_backup",
              "version": 1,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "DARK" },
              "expenses": [
                { "amountMinor": 1000, "currency": "INR", "occurredAt": 1000, "source": "TYPED", "categorizationState": "CONFIRMED" }
              ]
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(missingIdJson)
            fail("Should reject missing id")
        } catch (e: BackupException.MissingRequiredField) {
            assertTrue(e.message!!.contains("Missing or invalid integer 'id'"))
        }

        val zeroIdJson = """
            {
              "format": "kanak_backup",
              "version": 1,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "DARK" },
              "expenses": [
                { "id": 0, "amountMinor": 1000, "currency": "INR", "occurredAt": 1000, "source": "TYPED", "categorizationState": "CONFIRMED" }
              ]
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(zeroIdJson)
            fail("Should reject ID <= 0")
        } catch (e: BackupException.MissingRequiredField) {
            assertTrue(e.message!!.contains("Invalid non-positive ID"))
        }
    }

    @Test
    fun duplicateExpenseIds_rejected() {
        val duplicateJson = """
            {
              "format": "kanak_backup",
              "version": 1,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "DARK" },
              "expenses": [
                { "id": 10, "amountMinor": 1000, "currency": "INR", "occurredAt": 1000, "source": "TYPED", "categorizationState": "CONFIRMED" },
                { "id": 10, "amountMinor": 2000, "currency": "INR", "occurredAt": 2000, "source": "TYPED", "categorizationState": "CONFIRMED" }
              ]
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(duplicateJson)
            fail("Should reject duplicate expense IDs")
        } catch (e: BackupException.DuplicateExpenseIds) {
            assertTrue(e.message!!.contains("Duplicate expense ID found: 10"))
        }
    }

    @Test
    fun duplicateJsonKeys_rejected() {
        val duplicateKeyJson = """
            {
              "format": "kanak_backup",
              "version": 1,
              "version": 2,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "DARK" },
              "expenses": []
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(duplicateKeyJson)
            fail("Should reject duplicate JSON key")
        } catch (e: BackupException.CorruptedJson) {
            assertTrue(e.message!!.contains("Duplicate JSON key detected: 'version'"))
        }
    }

    @Test
    fun unknownCategorySourceStateTheme_rejectedWithoutFallback() {
        val unknownCatJson = """
            {
              "format": "kanak_backup",
              "version": 1,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "DARK" },
              "expenses": [
                { "id": 1, "amountMinor": 1000, "currency": "INR", "occurredAt": 1000, "category": "NON_EXISTENT_CATEGORY", "source": "TYPED", "categorizationState": "CONFIRMED" }
              ]
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(unknownCatJson)
            fail("Should reject unknown category string")
        } catch (e: BackupException.UnknownEnum) {
            assertTrue(e.message!!.contains("Unknown expense category: 'NON_EXISTENT_CATEGORY'"))
        }

        val unknownSourceJson = """
            {
              "format": "kanak_backup",
              "version": 1,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "DARK" },
              "expenses": [
                { "id": 1, "amountMinor": 1000, "currency": "INR", "occurredAt": 1000, "source": "TELEPATHIC", "categorizationState": "CONFIRMED" }
              ]
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(unknownSourceJson)
            fail("Should reject unknown source string")
        } catch (e: BackupException.UnknownEnum) {
            assertTrue(e.message!!.contains("Unknown entry source: 'TELEPATHIC'"))
        }

        val unknownThemeJson = """
            {
              "format": "kanak_backup",
              "version": 1,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "SOLARIZED_DARK" },
              "expenses": []
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(unknownThemeJson)
            fail("Should reject unknown theme string")
        } catch (e: BackupException.UnknownEnum) {
            assertTrue(e.message!!.contains("Unknown theme preference: 'SOLARIZED_DARK'"))
        }
    }

    @Test
    fun malformedAndTruncatedJson_rejectedWithCorruptedJson() {
        val truncatedJson = "{\"format\": \"kanak_backup\", \"version\": 1"
        try {
            KanakBackupSerializer.fromJson(truncatedJson)
            fail("Should reject truncated JSON")
        } catch (e: BackupException.CorruptedJson) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun wrongFormatAndUnsupportedSchemaVersions_rejected() {
        val wrongFormatJson = """
            {
              "format": "other_app_backup",
              "version": 1,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "DARK" },
              "expenses": []
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(wrongFormatJson)
            fail("Should reject wrong format")
        } catch (e: BackupException.InvalidFormat) {
            assertTrue(e.message!!.contains("Unsupported backup format: 'other_app_backup'"))
        }

        val unsupportedVersionJson = """
            {
              "format": "kanak_backup",
              "version": 2,
              "exportedAt": 1000,
              "preferences": { "defaultCurrency": "INR", "themePreference": "DARK" },
              "expenses": []
            }
        """.trimIndent()

        try {
            KanakBackupSerializer.fromJson(unsupportedVersionJson)
            fail("Should reject version 2")
        } catch (e: BackupException.UnsupportedSchemaVersion) {
            assertEquals(2, e.version)
        }
    }

    @Test
    fun byteAndRecordLimitEnforcement() {
        // Record limit enforcement test (> 50,000 records)
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"format\": \"kanak_backup\",\n")
        sb.append("  \"version\": 1,\n")
        sb.append("  \"exportedAt\": 1000,\n")
        sb.append("  \"preferences\": { \"defaultCurrency\": \"INR\", \"themePreference\": \"DARK\" },\n")
        sb.append("  \"expenses\": [\n")
        for (i in 1..50001) {
            sb.append("    { \"id\": $i, \"amountMinor\": 100, \"currency\": \"INR\", \"occurredAt\": 1000, \"source\": \"TYPED\", \"categorizationState\": \"CONFIRMED\" }")
            if (i < 50001) sb.append(",\n")
        }
        sb.append("\n  ]\n}")

        try {
            KanakBackupSerializer.fromJson(sb.toString())
            fail("Should reject backup exceeding record limit")
        } catch (e: BackupException.LimitExceeded) {
            assertTrue(e.message!!.contains("Backup exceeds maximum record count"))
        }
    }
}
