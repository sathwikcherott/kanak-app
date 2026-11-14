package com.kanak.app.data

import com.kanak.app.data.backup.BackupExpense
import com.kanak.app.data.backup.KanakBackup
import com.kanak.app.data.backup.KanakBackupSerializer
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.repository.ExpenseProfileManager
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.data.repository.ProfiledExpenseRepository
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.model.ExpenseProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseProfileTest {
    @Test fun profileManager_startsInMyExpenses_andCanReturnThere() {
        val profiles = ExpenseProfileManager()
        assertEquals(ExpenseProfile.MY_EXPENSES, profiles.activeProfile.value)
        profiles.select(ExpenseProfile.GIRLFRIEND_EXPENSES)
        profiles.returnToMyExpenses()
        assertEquals(ExpenseProfile.MY_EXPENSES, profiles.activeProfile.value)
    }

    @Test fun version4Backup_roundTripsExpenseProfile() {
        val backup = KanakBackup(expenses = listOf(
            BackupExpense(1L, 100L, "INR", 1000L, profile = ExpenseProfile.GIRLFRIEND_EXPENSES)
        ))
        assertEquals(ExpenseProfile.GIRLFRIEND_EXPENSES,
            KanakBackupSerializer.fromJson(KanakBackupSerializer.toJson(backup)).expenses.single().profile)
    }

    @Test fun olderBackup_defaultsExpensesToMyExpenses() {
        val backup = KanakBackup(version = 3, expenses = listOf(BackupExpense(1L, 100L, "INR", 1000L)))
        assertEquals(ExpenseProfile.MY_EXPENSES,
            KanakBackupSerializer.fromJson(KanakBackupSerializer.toJson(backup)).expenses.single().profile)
    }

    @Test fun profiledRepository_filtersViews_and_marksNewEntriesWithActiveProfile() = runBlocking {
        val profiles = ExpenseProfileManager()
        val base = FakeExpenseRepository(listOf(
            Expense(id = 1, amountMinor = 100, profile = ExpenseProfile.MY_EXPENSES),
            Expense(id = 2, amountMinor = 200, profile = ExpenseProfile.GIRLFRIEND_EXPENSES)
        ))
        val repository = ProfiledExpenseRepository(base, profiles)

        assertEquals(listOf(1L), repository.getAllExpenses().first().map { it.id })
        profiles.select(ExpenseProfile.GIRLFRIEND_EXPENSES)
        assertEquals(listOf(2L), repository.getAllExpenses().first().map { it.id })

        repository.saveExpense(Expense(amountMinor = 300))
        assertEquals(ExpenseProfile.GIRLFRIEND_EXPENSES, base.saved.profile)
    }

    private class FakeExpenseRepository(expenses: List<Expense>) : ExpenseRepository {
        private val rows = MutableStateFlow(expenses)
        lateinit var saved: Expense
        override fun getCategories(): Flow<List<ExpenseCategory>> = flowOf(ExpenseCategory.entries)
        override fun getAllExpenses(): Flow<List<Expense>> = rows
        override fun getExpenseById(id: Long): Flow<Expense?> = flowOf(rows.value.firstOrNull { it.id == id })
        override suspend fun saveExpense(expense: Expense): Long { saved = expense; return 3L }
        override suspend fun deleteExpense(id: Long) = Unit
        override fun getDefaultCurrency(): Flow<String> = flowOf("INR")
        override suspend fun setDefaultCurrency(currency: String) = Unit
        override fun getThemePreference(): Flow<ThemePreference> = flowOf(ThemePreference.DARK)
        override suspend fun setThemePreference(theme: ThemePreference) = Unit
        override fun getEnrichmentJobForExpense(expenseId: Long): Flow<AiEnrichmentJobEntity?> = flowOf(null)
        override suspend fun replaceAllExpensesInTransaction(expenses: List<Expense>) = Unit
    }
}
