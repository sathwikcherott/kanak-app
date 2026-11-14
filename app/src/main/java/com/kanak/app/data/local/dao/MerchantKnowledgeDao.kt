package com.kanak.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kanak.app.data.local.entity.MerchantKnowledgeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantKnowledgeDao {
    @Query("SELECT * FROM merchant_knowledge ORDER BY alias COLLATE NOCASE")
    fun observeAll(): Flow<List<MerchantKnowledgeEntity>>

    @Query("SELECT * FROM merchant_knowledge WHERE normalized_alias = :alias LIMIT 1")
    fun findByNormalizedAlias(alias: String): MerchantKnowledgeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: MerchantKnowledgeEntity): Long

    @Update fun update(item: MerchantKnowledgeEntity)
    @Query("DELETE FROM merchant_knowledge WHERE id = :id") fun deleteById(id: Long)
    @Query("SELECT * FROM merchant_knowledge") fun getAll(): List<MerchantKnowledgeEntity>
    @Query("DELETE FROM merchant_knowledge") fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertAll(items: List<MerchantKnowledgeEntity>)
}
