package com.kanak.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "merchant_knowledge", indices = [Index(value = ["normalized_alias"], unique = true)])
data class MerchantKnowledgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val alias: String,
    @ColumnInfo(name = "normalized_alias") val normalizedAlias: String,
    @ColumnInfo(name = "merchant_label") val merchantLabel: String,
    val category: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
