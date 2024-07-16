package com.vultisig.wallet.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chainOrder` (
            `value` TEXT PRIMARY KEY NOT NULL,
            `order` REAL NOT NULL)
            """.trimMargin()
        )
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vaultOrder` (
            `value` TEXT PRIMARY KEY NOT NULL,
            `order` REAL NOT NULL)
            """.trimMargin()
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `chainOrderCopy` (
                `value` TEXT NOT NULL,
                `order` REAL NOT NULL ,
                `parentId` TEXT NOT NULL,
                 PRIMARY KEY(`value`,`parentId`)
                 ) """.trimMargin()
        )

        db.execSQL("DROP TABLE `chainOrder`".trimMargin())

        db.execSQL("ALTER TABLE `chainOrderCopy` RENAME TO `chainOrder`".trimMargin())
    }
}


internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tokenValue` (
                `chain` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `ticker` TEXT NOT NULL,
                `tokenValue` TEXT NOT NULL,
                PRIMARY KEY(`chain`, `address`, `ticker`)
            )
            """.trimMargin()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tokenPrice` (
                `priceProviderId` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `price` TEXT NOT NULL,
                PRIMARY KEY(`priceProviderId`, `currency`)
            )
            """.trimMargin()
        )
    }
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE `chainOrder`".trimMargin())
    }
}

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `coin` ADD COLUMN `logo` TEXT NOT NULL DEFAULT ""
            """.trimMargin()
        )

        // just drop and recreate token price, as it is temporary cache
        db.execSQL(
            """
                DROP TABLE IF EXISTS `tokenPrice`
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tokenPrice` (
                `tokenId` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `price` TEXT NOT NULL,
                PRIMARY KEY(`tokenId`, `currency`)
            )
            """.trimMargin()
        )
    }
}

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `customToken` (
                `id` TEXT NOT NULL,
                `chain` TEXT NOT NULL,
                `ticker` TEXT NOT NULL,
                `decimals` INTEGER NOT NULL,
                `logo` TEXT NOT NULL,
                `priceProviderId` TEXT NOT NULL,
                `contractAddress` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `hexPublicKey` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimMargin()
        )
    }
}
