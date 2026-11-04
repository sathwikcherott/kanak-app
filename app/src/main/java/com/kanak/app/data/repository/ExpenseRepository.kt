package com.kanak.app.data.repository

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.model.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    fun getExpenseById(id: Long): Flow<Expense?>
    suspend fun saveExpense(expense: Expense): Long
    suspend fun deleteExpense(id: Long)
    fun getDefaultCurrency(): Flow<String>
    suspend fun setDefaultCurrency(currency: String)
    fun getThemePreference(): Flow<ThemePreference>
    suspend fun setThemePreference(theme: ThemePreference)
    fun getEnrichmentJobForExpense(expenseId: Long): Flow<AiEnrichmentJobEntity?>
}
