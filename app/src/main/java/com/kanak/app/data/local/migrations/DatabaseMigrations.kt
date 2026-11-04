package com.kanak.app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Encapsulates schema migrations for KanakDatabase to support migration testing and schema evolution.
 */
object DatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_enrichment_jobs` (
                    `jobId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `expense_id` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `suggested_category` TEXT,
                    `confidence_score` REAL,
                    `ai_reasoning` TEXT,
                    `suggested_tags` TEXT,
                    `retry_count` INTEGER NOT NULL DEFAULT 0,
                    `last_attempt_at` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `error_message` TEXT,
                    FOREIGN KEY(`expense_id`) REFERENCES `expenses`(`id`) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_enrichment_jobs_expense_id` ON `ai_enrichment_jobs` (`expense_id`)")
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ai_enrichment_jobs` ADD COLUMN `retry_count` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `ai_enrichment_jobs` ADD COLUMN `last_attempt_at` INTEGER")
        }
    }
}
