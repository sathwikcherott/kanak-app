package com.kanak.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kanak.app.data.backup.*
import com.kanak.app.data.local.KanakDatabase
import com.kanak.app.data.local.datastore.*
import com.kanak.app.data.local.migrations.DatabaseMigrations
import com.kanak.app.data.repository.OfflineExpenseRepository
import com.kanak.app.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CustomCategoryPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class Preferences : UserPreferencesDataSource {
        override val defaultCurrency = MutableStateFlow("INR")
        override val themePreference = MutableStateFlow(ThemePreference.DARK)
        override val lastBackupExportedTime = MutableStateFlow<Long?>(null)
        override suspend fun setDefaultCurrency(currency: String) { defaultCurrency.value = currency }
        override suspend fun setThemePreference(theme: ThemePreference) { themePreference.value = theme }
        override suspend fun setLastBackupExportedTime(timestamp: Long) { lastBackupExportedTime.value = timestamp }
        override suspend fun restorePreferences(currency: String, theme: ThemePreference) { defaultCurrency.value = currency; themePreference.value = theme }
    }
    private fun repository(db: KanakDatabase) = OfflineExpenseRepository(db.expenseDao(), Preferences(), db.aiEnrichmentJobDao(), db)
    private fun open(name: String) = Room.databaseBuilder(context, KanakDatabase::class.java, name)
        .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4).build()

    @Test fun migrationsFromEveryExistingSchemaPreserveFinancialDataAndBuiltInCategory() = runBlocking {
        for (version in 1..3) {
            val name = "migration-" + version + "-" + UUID.randomUUID() + ".db"
            val path = context.getDatabasePath(name)
            path.parentFile!!.mkdirs()
            val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
                .open("com.kanak.app.data.local.KanakDatabase/$version.json").bufferedReader().use { it.readText() }).getJSONObject("database")
            SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    sqlite.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.getJSONArray("indices")
                    for (i in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(i).getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
                }
                sqlite.execSQL("INSERT INTO expenses(id, amount_minor, currency, occurred_at, note, category, source, categorization_state) VALUES (1, 9007199254740991, 'INR', 1715000000000, 'For Swathi', 'GIRLFRIEND', 'VOICE', 'CONFIRMED')")
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
                sqlite.version = version
            }
            val db = open(name)
            try {
                val repo = repository(db)
                val expense = repo.getAllExpenses().first().single()
                assertEquals(9007199254740991L, expense.amountMinor)
                assertEquals(ExpenseCategory.GIRLFRIEND, expense.category)
                assertEquals("For Swathi", expense.note)
                assertEquals(1715000000000, expense.occurredAt)
                repo.createCategory("Pets")
                assertEquals(11, repo.getCategories().first().size)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun categoriesAndAssignmentsPersistAfterDatabaseReopenAndDuplicateNamesAreBlocked() = runBlocking {
        val name = "categories-" + UUID.randomUUID() + ".db"
        var db = open(name)
        try {
            var repo = repository(db)
            val category = repo.createCategory("  Pet   care  ")
            val id = repo.saveExpense(Expense(amountMinor = 12345, category = category, note = "Food for pets"))
            for (duplicate in listOf("pet care", "Ｐｅｔ care", "Food & Dining", "Girlfriend", " ", "a".repeat(41))) {
                try { repo.createCategory(duplicate); fail("Accepted duplicate/invalid name") } catch (_: IllegalArgumentException) { }
            }
            db.close()
            db = open(name)
            repo = repository(db)
            val saved = repo.getExpenseById(id).first()!!
            assertEquals(category.name, saved.category!!.name)
            assertEquals("Pet care", saved.category!!.displayName)
            assertEquals(12345L, saved.amountMinor)
            assertEquals(1, repo.getCategories().first().count { it.isCustom })
            repo.saveExpense(saved.copy(note = "Changed note"))
            assertEquals(category, repo.getExpenseById(id).first()!!.category)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun categoryCreationAndRestoreAreAtomicAndOldBackupsClearCustomCatalog() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KanakDatabase::class.java).build()
        try {
            val repo = repository(db)
            val category = repo.createCategory("Pets")
            repo.saveExpense(Expense(amountMinor = 100, category = category))
            val snapshot = repo.snapshotForBackup()
            assertEquals(category.name, snapshot.customCategories.single().name)
            val restored = KanakBackupSerializer.fromJson(KanakBackupSerializer.toJson(snapshot))
            repo.restoreValidatedBackup(restored)
            assertEquals("Pets", repo.getAllExpenses().first().single().category!!.displayName)
            repo.restoreValidatedBackup(KanakBackup(version = 1))
            assertTrue(repo.getCategories().first().none { it.isCustom })
            assertTrue(repo.getAllExpenses().first().isEmpty())
            try { repo.saveExpense(Expense(amountMinor = 100, category = category)); fail("Stale category must not save") } catch (_: IllegalArgumentException) { }
        } finally { db.close() }
    }

    @Test fun restoreFailureRollsBackCategoryCatalogExpensesAndQueueTogether() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KanakDatabase::class.java).build()
        try {
            val repo = repository(db)
            val oldCategory = repo.createCategory("Pets")
            val expenseId = repo.saveExpense(Expense(amountMinor = 200, category = oldCategory))
            val before = repo.getAllExpenses().first()
            val job = db.aiEnrichmentJobDao().getJobForExpenseSync(expenseId)
            val newCategory = ExpenseCategory.custom("custom:12345678-1234-1234-1234-123456789012", "Donations")
            val backup = KanakBackup(customCategories = listOf(newCategory),
                expenses = listOf(BackupExpense(20, 500, "INR", 1000, category = newCategory)))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER abort_restore BEFORE INSERT ON expenses BEGIN SELECT RAISE(ABORT, 'Injected failure'); END")
            try { repo.restoreValidatedBackup(backup); fail("Expected transaction failure") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(before, repo.getAllExpenses().first())
            assertEquals(listOf(oldCategory), repo.getCategories().first().filter { it.isCustom })
            assertEquals(job, db.aiEnrichmentJobDao().getJobForExpenseSync(expenseId))
        } finally { db.close() }
    }

    @Test fun concurrentCategoryCreationCannotProduceNormalizedDuplicates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KanakDatabase::class.java).build()
        try {
            val repo = repository(db)
            val results = listOf("Pets", " pets ").map { label -> async(Dispatchers.IO) { runCatching { repo.createCategory(label) } } }.awaitAll()
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, repo.getCategories().first().count { it.isCustom })
        } finally { db.close() }
    }
}
