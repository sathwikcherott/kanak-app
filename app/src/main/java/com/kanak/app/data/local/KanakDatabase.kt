package com.kanak.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kanak.app.data.local.dao.AiEnrichmentJobDao
import com.kanak.app.data.local.dao.ExpenseDao
import com.kanak.app.data.local.dao.CustomCategoryDao
import com.kanak.app.data.local.entity.CustomCategoryEntity
import com.kanak.app.data.local.entity.AiEnrichmentJobEntity
import com.kanak.app.data.local.entity.ExpenseEntity
import com.kanak.app.data.local.migrations.DatabaseMigrations

@Database(
    entities = [ExpenseEntity::class, AiEnrichmentJobEntity::class, CustomCategoryEntity::class],
    version = 4,
    exportSchema = true
)
abstract class KanakDatabase : RoomDatabase() {
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun aiEnrichmentJobDao(): AiEnrichmentJobDao

    companion object {
        @Volatile
        private var INSTANCE: KanakDatabase? = null

        fun getDatabase(context: Context): KanakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KanakDatabase::class.java,
                    "kanak_expenses.db"
                )
                    .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
