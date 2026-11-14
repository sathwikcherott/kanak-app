package com.kanak.app.data.repository

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Applies the temporary selected profile to normal expense screens and entries. */
class ProfiledExpenseRepository(
    private val delegate: ExpenseRepository,
    private val profiles: ExpenseProfileManager
) : ExpenseRepository {
    override fun getCategories(): Flow<List<ExpenseCategory>> = delegate.getCategories()
    override suspend fun createCategory(name: String): ExpenseCategory = delegate.createCategory(name)

    override fun getAllExpenses(): Flow<List<Expense>> = combine(
        delegate.getAllExpenses(), profiles.activeProfile
    ) { expenses, profile -> expenses.filter { it.profile == profile } }

    override fun getExpenseById(id: Long): Flow<Expense?> = combine(
        delegate.getExpenseById(id), profiles.activeProfile
    ) { expense, profile -> expense?.takeIf { it.profile == profile } }

    override suspend fun saveExpense(expense: Expense): Long =
        delegate.saveExpense(expense.copy(profile = profiles.activeProfile.value))

    override suspend fun deleteExpense(id: Long) = delegate.deleteExpense(id)
    override fun getDefaultCurrency(): Flow<String> = delegate.getDefaultCurrency()
    override suspend fun setDefaultCurrency(currency: String) = delegate.setDefaultCurrency(currency)
    override fun getThemePreference(): Flow<ThemePreference> = delegate.getThemePreference()
    override suspend fun setThemePreference(theme: ThemePreference) = delegate.setThemePreference(theme)
    override fun getEnrichmentJobForExpense(expenseId: Long): Flow<AiEnrichmentJobEntity?> = delegate.getEnrichmentJobForExpense(expenseId)
    override suspend fun replaceAllExpensesInTransaction(expenses: List<Expense>) = delegate.replaceAllExpensesInTransaction(expenses)
}
