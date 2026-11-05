package com.kanak.app.data.backup

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory

sealed class BackupException(message: String) : Exception(message) {
    class CorruptedJson(message: String) : BackupException(message)
    class UnsupportedSchemaVersion(val version: Int) : BackupException("Unsupported backup schema version: $version")
    class DuplicateExpenseIds(message: String) : BackupException(message)
    class InvalidMonetaryValue(message: String) : BackupException(message)
    class MissingRequiredField(message: String) : BackupException(message)
    class FileReadError(message: String) : BackupException(message)
}

data class BackupPreferences(
    val defaultCurrency: String = "INR",
    val themePreference: ThemePreference = ThemePreference.DARK
)

data class BackupExpense(
    val id: Long,
    val amountMinor: Long,
    val currency: String,
    val occurredAt: Long,
    val note: String? = null,
    val category: ExpenseCategory? = null,
    val source: EntrySource = EntrySource.TYPED,
    val categorizationState: CategorizationState = CategorizationState.CONFIRMED
) {
    fun toDomainModel(): Expense = Expense(
        id = id,
        amountMinor = amountMinor,
        currency = currency,
        occurredAt = occurredAt,
        note = note,
        category = category,
        source = source,
        categorizationState = categorizationState
    )

    companion object {
        fun fromDomainModel(expense: Expense): BackupExpense = BackupExpense(
            id = expense.id,
            amountMinor = expense.amountMinor,
            currency = expense.currency,
            occurredAt = expense.occurredAt,
            note = expense.note,
            category = expense.category,
            source = expense.source,
            categorizationState = expense.categorizationState
        )
    }
}

data class KanakBackup(
    val version: Int = CURRENT_BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val preferences: BackupPreferences = BackupPreferences(),
    val expenses: List<BackupExpense> = emptyList()
) {
    companion object {
        const val CURRENT_BACKUP_VERSION = 1
    }
}

data class BackupSummary(
    val version: Int,
    val exportedAt: Long,
    val totalExpenses: Int,
    val totalCategoriesCount: Int,
    val defaultCurrency: String,
    val themePreference: ThemePreference,
    val backup: KanakBackup
)
