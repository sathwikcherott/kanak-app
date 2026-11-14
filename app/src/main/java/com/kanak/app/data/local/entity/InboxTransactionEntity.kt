package com.kanak.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inbox_transactions",
    indices = [Index(value = ["notification_key"], unique = true), Index(value = ["fingerprint"], unique = true)]
)
data class InboxTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "notification_key") val notificationKey: String,
    val fingerprint: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String = "INR",
    val merchant: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "suggested_category") val suggestedCategory: String?,
    @ColumnInfo(name = "suggestion_reason") val suggestionReason: String?,
    val status: String = InboxStatus.PENDING.name,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

enum class InboxStatus { PENDING, APPROVED, DISMISSED }
