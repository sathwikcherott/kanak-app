package com.kanak.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kanak.app.data.local.entity.InboxTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartInboxDao {
    @Query("SELECT * FROM inbox_transactions WHERE status = 'PENDING' ORDER BY occurred_at DESC")
    fun observePending(): Flow<List<InboxTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore(item: InboxTransactionEntity): Long

    @Query("SELECT * FROM inbox_transactions WHERE id = :id")
    fun getById(id: Long): InboxTransactionEntity?

    @Update
    fun update(item: InboxTransactionEntity)

    @Query("SELECT * FROM inbox_transactions")
    fun getAll(): List<InboxTransactionEntity>

    @Query("DELETE FROM inbox_transactions")
    fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<InboxTransactionEntity>)
}
