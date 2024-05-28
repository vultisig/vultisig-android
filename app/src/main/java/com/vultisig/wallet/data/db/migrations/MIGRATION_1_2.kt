package com.vultisig.wallet.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chainOrder` (
            `value` TEXT PRIMARY KEY NOT NULL,
            `order` REAL NOT NULL
            """.trimMargin()
        )
    }
}