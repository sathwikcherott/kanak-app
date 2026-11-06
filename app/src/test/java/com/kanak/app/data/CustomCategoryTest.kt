package com.kanak.app.data

import com.kanak.app.data.backup.*
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.voice.VoiceDraftParser
import org.junit.Assert.*
import org.junit.Test

class CustomCategoryTest {
    private val id = "custom:12345678-1234-1234-1234-123456789012"
    private fun category(name: String = "Pet care") = ExpenseCategory.custom(id, name)

    @Test fun builtInIdentitiesRemainExactlyCompatible() {
        assertEquals("GIRLFRIEND", ExpenseCategory.GIRLFRIEND.name)
        assertEquals(10, ExpenseCategory.entries.size)
        assertEquals(ExpenseCategory.FOOD, ExpenseCategory.valueOf("FOOD"))
        assertFalse(ExpenseCategory.GIRLFRIEND.isCustom)
    }
    @Test fun categoryNamesTrimAndNormalizeUnicodeWhitespace() {
        assertEquals("Pet care", category("  Pet\u00a0  care  ").displayName)
        assertEquals(ExpenseCategory.normalizedName("Ｐｅｔ care"), ExpenseCategory.normalizedName("pet CARE"))
        assertTrue(category().isCustom)
    }
    @Test fun invalidNamesAndBuiltInDuplicatesAreRejected() {
        for (name in listOf("", "   ", "a".repeat(41), "Food & Dining", "food", "Girlfriend", "Bad\nname", "Bad\u202ename")) {
            try { category(name); fail("Accepted $name") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun invalidIdentifiersCannotMasqueradeAsBuiltIns() {
        try { ExpenseCategory.custom("FOOD", "Pets"); fail("Expected invalid ID") } catch (_: IllegalArgumentException) { }
    }
    @Test fun versionTwoPreservesCustomCategoryIdentityAndUnusedCategories() {
        val custom = category("Pet care 🐈")
        val backup = KanakBackup(exportedAt = 1000, customCategories = listOf(custom),
            expenses = listOf(BackupExpense(1, Long.MAX_VALUE, "INR", 1000, category = custom)))
        val restored = KanakBackupSerializer.fromJson(KanakBackupSerializer.toJson(backup))
        assertEquals(2, restored.version)
        assertEquals(custom.name, restored.expenses.single().category!!.name)
        assertEquals(custom.displayName, restored.expenses.single().category!!.displayName)
        assertEquals(Long.MAX_VALUE, restored.expenses.single().amountMinor)
        val unused = KanakBackupSerializer.fromJson(KanakBackupSerializer.toJson(backup.copy(expenses = emptyList())))
        assertEquals(custom.displayName, unused.customCategories.single().displayName)
    }
    @Test fun legacyVersionOneRemainsReadable() {
        val backup = KanakBackup(version = 1, expenses = listOf(BackupExpense(1, 100, "INR", 1000, category = ExpenseCategory.GIRLFRIEND)))
        val parsed = KanakBackupSerializer.fromJson(KanakBackupSerializer.toJson(backup))
        assertEquals(1, parsed.version)
        assertTrue(parsed.customCategories.isEmpty())
        assertEquals(ExpenseCategory.GIRLFRIEND, parsed.expenses.single().category)
    }
    @Test fun danglingCustomReferenceIsRejectedBeforeRestore() {
        val backup = KanakBackup(expenses = listOf(BackupExpense(1, 100, "INR", 1000, category = category())))
        try { KanakBackupSerializer.fromJson(KanakBackupSerializer.toJson(backup)); fail("Expected dangling reference") } catch (_: BackupException) { }
    }
    @Test fun duplicateCustomIdsAndNormalizedNamesAreRejected() {
        val sameName = ExpenseCategory.custom("custom:22222222-1234-1234-1234-123456789012", "PET CARE")
        for (categories in listOf(listOf(category(), category()), listOf(category(), sameName))) {
            try { KanakBackupSerializer.fromJson(KanakBackupSerializer.toJson(KanakBackup(customCategories = categories))); fail("Expected duplicate") } catch (_: BackupException) { }
        }
    }
    @Test fun versionOneCannotSilentlyDropCustomCategories() {
        try { KanakBackupSerializer.toJson(KanakBackup(version = 1, customCategories = listOf(category()))); fail("Expected version guard") } catch (_: BackupException) { }
    }
    @Test fun versionTwoRequiresCategoryCatalogAndValidNames() {
        val json = KanakBackupSerializer.toJson(KanakBackup(customCategories = listOf(category())))
        for (bad in listOf(json.replace("Pet care", "Food & Dining"), json.replace("\"id\": \"$id\"", "\"id\": \"bad\""),
            """{"format":"kanak_backup","version":2,"exportedAt":1000,"preferences":{"defaultCurrency":"INR","themePreference":"DARK"},"expenses":[]}""")) {
            try { KanakBackupSerializer.fromJson(bad); fail("Expected invalid catalog") } catch (_: BackupException) { }
        }
    }
    @Test fun categoryCatalogLimitIsEnforced() {
        try { KanakBackupSerializer.toJson(KanakBackup(customCategories = List(101) { category() })); fail("Expected limit") } catch (_: BackupException.LimitExceeded) { }
    }
    @Test fun voiceRecognizesCustomLabelsWithoutChangingSwathiPriority() {
        assertEquals(category(), VoiceDraftParser.parseTranscript("150 pet care", listOf(category())).category)
        assertNull(VoiceDraftParser.inferCategory("150 carpet care", listOf(category())))
        assertEquals(ExpenseCategory.GIRLFRIEND, VoiceDraftParser.inferCategory("Pet care for Swathi", listOf(category())))
    }
}
