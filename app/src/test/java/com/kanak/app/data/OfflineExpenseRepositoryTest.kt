package com.kanak.app.data

import com.kanak.app.data.local.dao.ExpenseDao
import com.kanak.app.data.local.datastore.UserPreferencesDataSource
import com.kanak.app.data.local.entity.ExpenseEntity
import com.kanak.app.data.repository.OfflineExpenseRepository
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineExpenseRepositoryTest {

    @Test
    fun getAllExpenses_mapsEntitiesToDomainModels() = runTest {
        val fakeDao = FakeExpenseDao()
        val fakePrefRepo = FakeUserPreferencesDataSource()
        val repository = OfflineExpenseRepository(fakeDao, fakePrefRepo)

        val entity = ExpenseEntity(
            id = 1L,
            amountMinor = 1500L,
            currency = "USD",
            occurredAt = 1000L,
            note = "Lunch",
            category = "FOOD",
            source = "TYPED",
            categorizationState = "CONFIRMED"
        )
        fakeDao.expenses.value = listOf(entity)

        val expenses = repository.getAllExpenses().first()
        assertEquals(1, expenses.size)
        val expense = expenses[0]
        assertEquals(1500L, expense.amountMinor)
        assertEquals("USD", expense.currency)
        assertEquals("Lunch", expense.note)
        assertEquals(ExpenseCategory.FOOD, expense.category)
        assertEquals(EntrySource.TYPED, expense.source)
        assertEquals(CategorizationState.CONFIRMED, expense.categorizationState)
    }

    @Test
    fun saveExpense_executesInRoomTransaction() = runTest {
        val fakeDao = FakeExpenseDao()
        val fakePrefRepo = FakeUserPreferencesDataSource()
        val repository = OfflineExpenseRepository(fakeDao, fakePrefRepo)

        val newExpense = Expense(
            amountMinor = 2500L,
            currency = "INR",
            note = "Taxi ride",
            category = ExpenseCategory.TRANSPORT
        )

        val id = repository.saveExpense(newExpense)
        assertEquals(1L, id)
        assertTrue(fakeDao.wasTransactionCalled)
        assertEquals(1, fakeDao.expenses.value.size)
        val saved = fakeDao.expenses.value[0]
        assertEquals(2500L, saved.amountMinor)
        assertEquals("INR", saved.currency)
        assertEquals("TRANSPORT", saved.category)
    }

    private class FakeExpenseDao : ExpenseDao {
        val expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
        var wasTransactionCalled = false

        override fun getAllExpenses(): Flow<List<ExpenseEntity>> = expenses
        override fun getExpenseById(id: Long): Flow<ExpenseEntity?> = flowOf(expenses.value.find { it.id == id })
        override fun getExpenseByIdSync(id: Long): ExpenseEntity? = expenses.value.find { it.id == id }
        override fun insertExpense(expense: ExpenseEntity): Long {
            val nextId = (expenses.value.maxOfOrNull { it.id } ?: 0L) + 1L
            val inserted = expense.copy(id = nextId)
            expenses.value = expenses.value + inserted
            return nextId
        }
        override fun insertExpenseInTransaction(expense: ExpenseEntity): Long {
            wasTransactionCalled = true
            return insertExpense(expense)
        }
        override fun updateExpense(expense: ExpenseEntity) {}
        override fun deleteExpense(expense: ExpenseEntity) {}
        override fun deleteExpenseById(id: Long) {
            expenses.value = expenses.value.filter { it.id != id }
        }
    }

    private class FakeUserPreferencesDataSource : UserPreferencesDataSource {
        private val _currency = MutableStateFlow("USD")
        override val defaultCurrency: Flow<String> = _currency

        override suspend fun setDefaultCurrency(currency: String) {
            _currency.value = currency
        }
    }
}
