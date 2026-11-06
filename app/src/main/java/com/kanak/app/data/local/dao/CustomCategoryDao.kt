package com.kanak.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kanak.app.data.local.entity.CustomCategoryEntity
import com.kanak.app.data.local.entity.CategorizedExpenseRow
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {
    @Query("SELECT expenses.*, custom_categories.displayName AS customCategoryName FROM expenses LEFT JOIN custom_categories ON expenses.category = custom_categories.id ORDER BY occurred_at DESC")
    fun observeExpenses(): Flow<List<CategorizedExpenseRow>>
    @Query("SELECT expenses.*, custom_categories.displayName AS customCategoryName FROM expenses LEFT JOIN custom_categories ON expenses.category = custom_categories.id WHERE expenses.id = :id")
    fun observeExpense(id: Long): Flow<CategorizedExpenseRow?>

    @Query("SELECT * FROM custom_categories ORDER BY normalizedName")
    fun observeAll(): Flow<List<CustomCategoryEntity>>
    @Query("SELECT * FROM custom_categories ORDER BY normalizedName")
    fun getAll(): List<CustomCategoryEntity>
    @Query("SELECT * FROM custom_categories WHERE id = :id")
    fun getById(id: String): CustomCategoryEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(category: CustomCategoryEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAll(categories: List<CustomCategoryEntity>)
    @Query("DELETE FROM custom_categories")
    fun deleteAll()
}
