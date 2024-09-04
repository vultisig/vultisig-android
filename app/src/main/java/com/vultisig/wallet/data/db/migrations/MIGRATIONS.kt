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
            CREATE TABLE IF NOT EXISTS `address_book_entry` (
                `chainId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                PRIMARY KEY(`chainId`, `address`)
            )
       """.trimIndent()
        )
    }
}


internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.updateChainNameValue("Maya Chain", "MayaChain")
        db.updateChainNameValue("Cronos Chain", "CronosChain")
        db.updateChainNameValue("Bitcoin Cash", "Bitcoin-Cash")
        db.updateChainNameValue("Gaia Chain", "Gaia")
    }
}


private fun SupportSQLiteDatabase.updateChainNameValue(before: String, after: String) {
    execSQL(
        """
            UPDATE coin SET
                id = REPLACE(id, '$before', '$after'),
                chain = REPLACE(chain, '$before', '$after')
                WHERE id LIKE '%$before'
            """.trimIndent()
    )
    execSQL(
        """
            UPDATE tokenPrice SET
                tokenId = REPLACE(tokenId, '$before', '$after')
                WHERE tokenId LIKE '%$before'
            """.trimIndent()
    )
    execSQL(
        """
            UPDATE tokenValue SET
                chain = REPLACE(chain, '$before', '$after')
                WHERE chain LIKE '%$before'
            """.trimIndent()
    )
    execSQL(
        """
            UPDATE address_book_entry SET
                chainId = REPLACE(chainId, '$before', '$after')
                WHERE chainId LIKE '%$before'
            """.trimIndent()
    )
}

internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `addressBookOrder` (
            `value` TEXT PRIMARY KEY NOT NULL,
            `order` REAL NOT NULL)
            """.trimIndent()
        )
    }
}

internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE coin
            SET address = replace(address, 'bitcoincash:', '')
            WHERE chain = 'Bitcoin-Cash'
       """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE tokenValue
            SET address = replace(address, 'bitcoincash:', '')
            WHERE chain = 'Bitcoin-Cash'
       """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE address_book_entry
            SET address = replace(address, 'bitcoincash:', '')
            WHERE chainId = 'Bitcoin-Cash'
       """.trimIndent()
        )
    }
}

internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.updateChainNameValue("Gaia", "Cosmos")
    }
}

internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE coin 
            SET logo = 'polygon' 
            WHERE logo = 'matic'
            """.trimIndent()
        )
    }
}

internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.updateCoinDecimals("UNI", 18)
        db.updateCoinDecimals("MATIC", 18)
        db.updateCoinDecimals("WBTC", 8)
        db.updateCoinDecimals("LINK", 18)
        db.updateCoinDecimals("FLIP", 18)
    }
}

internal val MIGRATION_13_14=object :Migration(13,14){
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_coin_vaultId` ON `coin` (`vaultId`)")
    }
}

internal val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cmcId` (
                `contractAddress` TEXT NOT NULL,
                `id` INTEGER DEFAULT NULL,
                PRIMARY KEY(`contractAddress`)
            )
            """.trimIndent()
        )
    }
}

private fun SupportSQLiteDatabase.updateCoinDecimals(ticker: String, decimal: Int) {
    execSQL(
        """
        UPDATE coin SET decimals = $decimal
        WHERE ticker = "$ticker"
    """.trimIndent()
    )
}
