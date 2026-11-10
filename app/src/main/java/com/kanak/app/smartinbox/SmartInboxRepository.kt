package com.kanak.app.smartinbox

import com.kanak.app.data.local.KanakDatabase
import com.kanak.app.data.local.entity.InboxStatus
import com.kanak.app.data.local.entity.InboxTransactionEntity
import com.kanak.app.data.local.entity.MerchantKnowledgeEntity
import com.kanak.app.data.local.entity.toEntity
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Calendar

data class PendingInboxTransaction(
    val id: Long, val amountMinor: Long, val currency: String, val merchant: String,
    val occurredAt: Long, val suggestedCategory: ExpenseCategory?, val suggestionReason: String?
)
data class KnowledgeEntry(
    val id: Long, val alias: String, val merchantLabel: String, val category: ExpenseCategory?
)

/** Local queue: notification capture never calls the normal expense save path. */
class SmartInboxRepository(private val database: KanakDatabase) {
    private val inboxDao = database.smartInboxDao()
    private val knowledgeDao = database.merchantKnowledgeDao()

    fun pending(): Flow<List<PendingInboxTransaction>> = inboxDao.observePending().map { rows -> rows.map(::toPending) }
    fun knowledge(): Flow<List<KnowledgeEntry>> = knowledgeDao.observeAll().map { rows -> rows.map(::toKnowledge) }

    suspend fun capture(notificationKey: String, amountMinor: Long, merchant: String, occurredAt: Long) = withContext(Dispatchers.IO) {
        val normalizedMerchant = normalize(merchant)
        val suggestion = suggest(normalizedMerchant, amountMinor, occurredAt)
        val fingerprint = sha256("$normalizedMerchant|$amountMinor|${occurredAt / 60_000L}")
        inboxDao.insertIgnore(InboxTransactionEntity(
            notificationKey = notificationKey, fingerprint = fingerprint, amountMinor = amountMinor,
            merchant = merchant.trim(), occurredAt = occurredAt, suggestedCategory = suggestion.category?.name,
            suggestionReason = suggestion.reason
        ))
    }

    /** An explicit share is user intent; store only extracted fields and a dedupe hash, never receipt text. */
    suspend fun captureSharedText(text: String): Boolean = withContext(Dispatchers.IO) {
        val receipt = SharedReceiptParser.parse(text) ?: return@withContext false
        capture(
            notificationKey = "share:${sha256(text)}",
            amountMinor = receipt.amountMinor,
            merchant = receipt.merchant,
            occurredAt = System.currentTimeMillis()
        )
        true
    }

    suspend fun approve(id: Long, category: ExpenseCategory?, note: String? = null): Boolean = withContext(Dispatchers.IO) {
        var approved = false
        database.runInTransaction {
            val item = inboxDao.getById(id) ?: return@runInTransaction
            if (item.status != InboxStatus.PENDING.name) return@runInTransaction
            val chosen = category ?: item.suggestedCategory?.toCategory()
            database.expenseDao().insertExpenseInTransaction(Expense(
                amountMinor = item.amountMinor, currency = item.currency, occurredAt = item.occurredAt,
                note = note?.trim()?.takeIf { it.isNotEmpty() } ?: item.merchant,
                category = chosen, source = EntrySource.NOTIFICATION,
                categorizationState = if (chosen == null) CategorizationState.UNCATEGORIZED else CategorizationState.CONFIRMED
            ).toEntity())
            inboxDao.update(item.copy(status = InboxStatus.APPROVED.name))
            if (chosen != null) upsertKnowledgeInternal(item.merchant, item.merchant, chosen)
            approved = true
        }
        approved
    }

    suspend fun dismiss(id: Long) = withContext(Dispatchers.IO) {
        database.runInTransaction {
            inboxDao.getById(id)?.takeIf { it.status == InboxStatus.PENDING.name }?.let {
                inboxDao.update(it.copy(status = InboxStatus.DISMISSED.name))
            }
        }
    }

    suspend fun teach(alias: String, merchantLabel: String, category: ExpenseCategory?) = withContext(Dispatchers.IO) {
        require(alias.trim().length >= 2) { "Use at least 2 characters for an alias" }
        database.runInTransaction { upsertKnowledgeInternal(alias, merchantLabel, category) }
    }

    suspend fun removeKnowledge(id: Long) = withContext(Dispatchers.IO) { knowledgeDao.deleteById(id) }

    private fun upsertKnowledgeInternal(alias: String, merchantLabel: String, category: ExpenseCategory?) {
        val cleanedAlias = alias.trim()
        val normalized = normalize(cleanedAlias)
        val now = System.currentTimeMillis()
        val existing = knowledgeDao.findByNormalizedAlias(normalized)
        val item = MerchantKnowledgeEntity(
            id = existing?.id ?: 0L, alias = cleanedAlias, normalizedAlias = normalized,
            merchantLabel = merchantLabel.trim().ifEmpty { cleanedAlias }, category = category?.name,
            createdAt = existing?.createdAt ?: now, updatedAt = now
        )
        knowledgeDao.insert(item)
    }

    private fun suggest(normalizedMerchant: String, amountMinor: Long, occurredAt: Long): Suggestion {
        val exact = knowledgeDao.findByNormalizedAlias(normalizedMerchant)
        val contains = exact ?: knowledgeDao.getAll().firstOrNull { normalizedMerchant.contains(it.normalizedAlias) || it.normalizedAlias.contains(normalizedMerchant) }
        val category = contains?.category?.toCategory()
        return if (category != null) Suggestion(category, "Based on your rule for ${contains.alias}")
        else {
            // History remains on device. Exact merchant is strongest; matching amount and
            // time-of-day break ties for recurring payments such as commute or meals.
            val incomingHour = Calendar.getInstance().apply { timeInMillis = occurredAt }.get(Calendar.HOUR_OF_DAY)
            val votes = database.expenseDao().getAllExpensesSync().asSequence()
                .filter { expense -> expense.note?.let(::normalize)?.let { it.contains(normalizedMerchant) || normalizedMerchant.contains(it) } == true }
                .mapNotNull { expense -> expense.category?.toCategory()?.let { category ->
                    val hour = Calendar.getInstance().apply { timeInMillis = expense.occurredAt }.get(Calendar.HOUR_OF_DAY)
                    category to (1 + (if (expense.amountMinor == amountMinor) 2 else 0) + (if (hour == incomingHour) 1 else 0))
                } }
                .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            val historic = votes.maxByOrNull { it.value }?.key
            if (historic != null) Suggestion(historic, "Based on similar approved history") else Suggestion(null, null)
        }
    }

    private data class Suggestion(val category: ExpenseCategory?, val reason: String?)
    private fun toPending(row: InboxTransactionEntity) = PendingInboxTransaction(row.id, row.amountMinor, row.currency, row.merchant, row.occurredAt, row.suggestedCategory?.toCategory(), row.suggestionReason)
    private fun toKnowledge(row: MerchantKnowledgeEntity) = KnowledgeEntry(row.id, row.alias, row.merchantLabel, row.category?.toCategory())
    private fun String.toCategory() = runCatching { ExpenseCategory.valueOf(this) }.getOrNull()
    companion object {
        fun normalize(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
        private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
