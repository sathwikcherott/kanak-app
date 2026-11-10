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
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `custom_categories` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_categories_normalizedName` ON `custom_categories` (`normalizedName`)")
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Preserve assignments to earlier custom categories before removing duplicate labels.
            for ((label, builtInId) in listOf("tech" to "TECH", "games" to "GAMES")) {
                db.execSQL(
                    "UPDATE expenses SET category = ? WHERE category IN (SELECT id FROM custom_categories WHERE normalizedName = ?)",
                    arrayOf(builtInId, label)
                )
                db.execSQL(
                    "UPDATE ai_enrichment_jobs SET suggested_category = ? WHERE suggested_category IN (SELECT id FROM custom_categories WHERE normalizedName = ?)",
                    arrayOf(builtInId, label)
                )
                db.execSQL("DELETE FROM custom_categories WHERE normalizedName = ?", arrayOf(label))
            }
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `inbox_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `notification_key` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `merchant` TEXT NOT NULL, `occurred_at` INTEGER NOT NULL, `suggested_category` TEXT, `suggestion_reason` TEXT, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inbox_transactions_notification_key` ON `inbox_transactions` (`notification_key`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inbox_transactions_fingerprint` ON `inbox_transactions` (`fingerprint`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `merchant_knowledge` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alias` TEXT NOT NULL, `normalized_alias` TEXT NOT NULL, `merchant_label` TEXT NOT NULL, `category` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_merchant_knowledge_normalized_alias` ON `merchant_knowledge` (`normalized_alias`)")
        }
    }
}
