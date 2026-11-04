package com.kanak.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory

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
    val categorizationState: String
)

fun ExpenseEntity.toDomainModel(): Expense {
    return Expense(
        id = id,
        amountMinor = amountMinor,
        currency = currency,
        occurredAt = occurredAt,
        note = note,
        category = category?.let { name ->
            try { ExpenseCategory.valueOf(name) } catch (_: Exception) { null }
        },
        source = try { EntrySource.valueOf(source) } catch (_: Exception) { EntrySource.TYPED },
        categorizationState = try { CategorizationState.valueOf(categorizationState) } catch (_: Exception) { CategorizationState.UNCATEGORIZED }
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
        categorizationState = categorizationState.name
    )
}
