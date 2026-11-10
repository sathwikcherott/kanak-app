package com.kanak.app.data.backup

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory

sealed class BackupException(message: String) : Exception(message) {
    class CorruptedJson(message: String) : BackupException(message)
    class InvalidFormat(message: String) : BackupException(message)
    class UnsupportedSchemaVersion(val version: Long) : BackupException("Unsupported backup schema version: $version")
    class DuplicateExpenseIds(message: String) : BackupException(message)
    class InvalidMonetaryValue(message: String) : BackupException(message)
    class MissingRequiredField(message: String) : BackupException(message)
    class UnknownEnum(message: String) : BackupException(message)
    class LimitExceeded(message: String) : BackupException(message)
    class FileReadError(message: String) : BackupException(message)
}

data class BackupPreferences(
    val defaultCurrency: String = "INR",
    val themePreference: ThemePreference = ThemePreference.DARK,
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
    val format: String = FORMAT_IDENTIFIER,
    val version: Int = CURRENT_BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val preferences: BackupPreferences = BackupPreferences(),
    val expenses: List<BackupExpense> = emptyList(),
    val customCategories: List<ExpenseCategory> = emptyList(),
    val inboxTransactions: List<BackupInboxTransaction> = emptyList(),
    val knowledge: List<BackupKnowledgeEntry> = emptyList()
) {
    companion object {
        const val FORMAT_IDENTIFIER = "kanak_backup"
        const val CURRENT_BACKUP_VERSION = 3
        const val MAX_BACKUP_BYTES = 10 * 1024 * 1024L // 10 MiB; enforced before decoding file bytes
        const val MAX_EXPENSE_RECORDS = 50_000
    }
}

data class BackupInboxTransaction(
    val id: Long, val notificationKey: String, val fingerprint: String, val amountMinor: Long,
    val currency: String, val merchant: String, val occurredAt: Long, val suggestedCategory: String?,
    val suggestionReason: String?, val status: String, val createdAt: Long
)

data class BackupKnowledgeEntry(
    val id: Long, val alias: String, val normalizedAlias: String, val merchantLabel: String,
    val category: String?, val createdAt: Long, val updatedAt: Long
)

data class BackupSummary(
    val version: Int,
    val exportedAt: Long,
    val totalExpenses: Int,
    val totalCategoriesCount: Int,
    val defaultCurrency: String,
    val themePreference: ThemePreference,
    val backup: KanakBackup,
    val totalCustomCategories: Int = backup.customCategories.size
)
