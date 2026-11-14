package com.kanak.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.model.ExpenseProfile

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "category")
    val category: String?,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "categorization_state")
    val categorizationState: String,
    @ColumnInfo(name = "profile")
    val profile: String = ExpenseProfile.MY_EXPENSES.name
)

fun ExpenseEntity.toDomainModel(categories: Map<String, ExpenseCategory> = emptyMap()): Expense {
    return Expense(
        id = id,
        amountMinor = amountMinor,
        currency = currency,
        occurredAt = occurredAt,
        note = note,
        category = category?.let { name ->
            categories[name] ?: try { ExpenseCategory.valueOf(name) } catch (_: Exception) { null }
        },
        source = try { EntrySource.valueOf(source) } catch (_: Exception) { EntrySource.TYPED },
        categorizationState = try { CategorizationState.valueOf(categorizationState) } catch (_: Exception) { CategorizationState.UNCATEGORIZED },
        profile = try { ExpenseProfile.valueOf(profile) } catch (_: Exception) { ExpenseProfile.MY_EXPENSES }
    )
}

fun Expense.toEntity(): ExpenseEntity {
    return ExpenseEntity(
        id = id,
        amountMinor = amountMinor,
        currency = currency,
        occurredAt = occurredAt,
        note = note,
        category = category?.name,
        source = source.name,
        categorizationState = categorizationState.name,
        profile = profile.name
    )
}
