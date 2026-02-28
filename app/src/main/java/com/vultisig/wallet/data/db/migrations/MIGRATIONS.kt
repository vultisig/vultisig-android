package com.vultisig.wallet.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
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

val MIGRATION_2_3 = object : Migration(2, 3) {
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


val MIGRATION_3_4 = object : Migration(3, 4) {
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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE `chainOrder`".trimMargin())
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
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


val MIGRATION_6_7 = object : Migration(6, 7) {
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


val MIGRATION_7_8 = object : Migration(7, 8) {
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

val MIGRATION_8_9 = object : Migration(8, 9) {
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

val MIGRATION_9_10 = object : Migration(9, 10) {
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

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.updateChainNameValue("Gaia", "Cosmos")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
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

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.updateCoinDecimals("UNI", 18)
        db.updateCoinDecimals("MATIC", 18)
        db.updateCoinDecimals("WBTC", 8)
        db.updateCoinDecimals("LINK", 18)
        db.updateCoinDecimals("FLIP", 18)
    }
}

val MIGRATION_13_14 = object : Migration(13, 14){
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_coin_vaultId` ON `coin` (`vaultId`)")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15){
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vaultFolder` (
                `id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimMargin()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `folderOrder` (
            `value` TEXT PRIMARY KEY NOT NULL,
            `order` REAL NOT NULL)
            """.trimMargin()
        )
        db.execSQL(
            """
            ALTER TABLE `vaultOrder` ADD COLUMN `parentId` TEXT
            """.trimMargin()
        )
    }
}

val MIGRATION_15_16=object :Migration(15,16) {
    override fun migrate(db: SupportSQLiteDatabase) {

        db.execSQL(
            """
            DELETE FROM tokenvalue 
            WHERE chain = "BSC" 
            AND ticker = "WETH"
            """.trimIndent()
        )

        db.execSQL(
            """
            DELETE FROM coin
            WHERE id = 'WETH-BSC'
            """.trimIndent()
        )
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `signer_new` (
                `index` INT NOT NULL,
                `vaultId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                PRIMARY KEY(`vaultId`, `title`),
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO signer_new (`index`, vaultId, title)
            SELECT 0 AS `index`, vaultId, title FROM signer
        """.trimIndent())

        db.execSQL("""
            DROP TABLE signer
        """.trimIndent())

        db.execSQL("""
            ALTER TABLE signer_new RENAME TO signer
        """.trimIndent())
    }
}
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE coin 
            SET ticker = 'POL' , logo = 'pol'
            WHERE chain = 'Polygon' and ticker='MATIC'
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE coin 
            SET ticker = 'POL' , logo = 'pol'
            WHERE chain = 'Ethereum' and ticker='MATIC'
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

internal val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vaultMetadata` (
                `vaultId` TEXT NOT NULL,
                `isServerBackupVerified` INTEGER DEFAULT NULL,
                PRIMARY KEY(`vaultId`),
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )
    }
}

internal val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `vault` ADD COLUMN `libType` TEXT NOT NULL DEFAULT 'GG20'
            """.trimIndent()
        )
    }
}

internal val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.updatePriceProviderId("USDT", "Arbitrum", "tether")
        db.updatePriceProviderId("USDC.e", "Arbitrum", "usd-coin-ethereum-bridged")
        db.updatePriceProviderId("USDC", "Arbitrum", "usd-coin")
    }
}

private fun SupportSQLiteDatabase.updatePriceProviderId(
    ticker: String,
    chain: String,
    priceProviderId: String,
) {
    execSQL(
        """
        UPDATE coin SET priceProviderId = "$priceProviderId"
        WHERE ticker = "$ticker" AND chain = "$chain"
    """.trimIndent()
    )
}

internal val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // delete vault metadata isServerBackupVerified
        // add fastVaultPasswordReminderShownDate as int
        // Create a new table without the `isServerBackupVerified` column
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vaultMetadata_new` (
                `vaultId` TEXT NOT NULL,
                `fastVaultPasswordReminderShownDate` INTEGER DEFAULT NULL,
                PRIMARY KEY(`vaultId`),
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )

        // Copy data from the old table to the new table
        db.execSQL(
            """
            INSERT INTO `vaultMetadata_new` (`vaultId`, `fastVaultPasswordReminderShownDate`)
            SELECT `vaultId`, NULL AS `fastVaultPasswordReminderShownDate`
            FROM `vaultMetadata`
            """.trimIndent()
        )

        // Drop the old table
        db.execSQL("DROP TABLE `vaultMetadata`")

        // Rename the new table to the original table name
        db.execSQL("ALTER TABLE `vaultMetadata_new` RENAME TO `vaultMetadata`")
    }
}

internal val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `disabledCoin` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `coinId` TEXT NOT NULL,
                `chain` TEXT NOT NULL,
                `vaultId` TEXT NOT NULL,
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
        """.trimIndent()
        )
    }
}

internal val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create active_bonded_nodes table for storing bonded node information
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `active_bonded_nodes` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `node_address` TEXT NOT NULL,
                `node_state` TEXT NOT NULL,
                `coin_id` TEXT NOT NULL,
                `vault_id` TEXT NOT NULL,
                `amount` TEXT NOT NULL,
                `apy` REAL NOT NULL,
                `next_reward` REAL NOT NULL,
                `next_churn` INTEGER,
                FOREIGN KEY(`vault_id`) REFERENCES `vault`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_active_bonded_nodes_vault_id` 
            ON `active_bonded_nodes` (`vault_id`)
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `staking_details` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `vault_id` TEXT NOT NULL,
                `coin_id` TEXT NOT NULL,
                `stake_amount` TEXT NOT NULL,
                `apr` REAL,
                `estimated_rewards` TEXT,
                `next_payout_date` INTEGER,
                `rewards` TEXT,
                `rewards_coin_id` TEXT,
                FOREIGN KEY(`vault_id`) REFERENCES `vault`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_staking_details_vault_id` 
            ON `staking_details` (`vault_id`)
            """.trimIndent()
        )
    }
}

internal val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Update price provider ID from matic-network to polygon-ecosystem-token
        database.execSQL(
            """
            UPDATE coin
            SET priceProviderId = 'polygon-ecosystem-token'
            WHERE priceProviderId = 'matic-network'
            """.trimIndent()
        )
    }
}

internal val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chainPublicKey` (
            `vaultId` TEXT NOT NULL,
            `chain` TEXT NOT NULL,
            `publicKey` TEXT NOT NULL,
            `isEddsa` INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(`vaultId`, `chain`),
            FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON UPDATE CASCADE ON DELETE CASCADE)
            """.trimIndent()
        )
    }
}

internal val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE vault ADD COLUMN pubKeyMldsa TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )
    }
}
