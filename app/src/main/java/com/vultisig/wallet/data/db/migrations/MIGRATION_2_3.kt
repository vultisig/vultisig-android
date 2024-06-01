package com.vultisig.wallet.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vaultOrder` (
            `value` TEXT PRIMARY KEY NOT NULL,
            `order` REAL NOT NULL)
            """.trimMargin()
        )
    }
}