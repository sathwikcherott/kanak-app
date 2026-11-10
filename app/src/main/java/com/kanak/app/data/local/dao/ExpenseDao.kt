package com.kanak.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kanak.app.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY occurred_at DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun getExpenseById(id: Long): Flow<ExpenseEntity?>

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun getExpenseByIdSync(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses")
    fun getAllExpensesSync(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertExpense(expense: ExpenseEntity): Long

    @Transaction
    fun insertExpenseInTransaction(expense: ExpenseEntity): Long {
        return insertExpense(expense)
    }

    @Update
    fun updateExpense(expense: ExpenseEntity)

    @Delete
    fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    fun deleteExpenseById(id: Long)

    @Query("DELETE FROM expenses")
    fun deleteAllExpenses()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertExpenses(expenses: List<ExpenseEntity>)
}
