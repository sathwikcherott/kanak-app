package com.kanak.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kanak.app.model.ExpenseCategory

@Entity(tableName = "custom_categories", indices = [Index(value = ["normalizedName"], unique = true)])
data class CustomCategoryEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val normalizedName: String
) {
    fun toDomainModel(): ExpenseCategory = ExpenseCategory.custom(id, displayName)
    companion object {
        fun from(category: ExpenseCategory) = CustomCategoryEntity(category.name, category.displayName, ExpenseCategory.normalizedName(category.displayName))
    }
}


// Single-query snapshot keeps expense labels consistent during replacement restores.
data class CategorizedExpenseRow(
    @Embedded val expense: ExpenseEntity,
    val customCategoryName: String?
)
