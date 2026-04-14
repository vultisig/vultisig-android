package com.vultisig.wallet.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `chainOrder` (
            `value` TEXT PRIMARY KEY NOT NULL,
            `order` REAL NOT NULL)
            """
                    .trimMargin()
            )
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `vaultOrder` (
            `value` TEXT PRIMARY KEY NOT NULL,
            `order` REAL NOT NULL)
            """
                    .trimMargin()
            )

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `chainOrderCopy` (
                `value` TEXT NOT NULL,
                `order` REAL NOT NULL ,
                `parentId` TEXT NOT NULL,
                 PRIMARY KEY(`value`,`parentId`)
                 ) """
                    .trimMargin()
            )

            db.execSQL("DROP TABLE `chainOrder`".trimMargin())

            db.execSQL("ALTER TABLE `chainOrderCopy` RENAME TO `chainOrder`".trimMargin())
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
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
            """
                    .trimMargin()
            )

            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `tokenPrice` (
                `priceProviderId` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `price` TEXT NOT NULL,
                PRIMARY KEY(`priceProviderId`, `currency`)
            )
            """
                    .trimMargin()
            )
        }
    }

val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE `chainOrder`".trimMargin())
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            ALTER TABLE `coin` ADD COLUMN `logo` TEXT NOT NULL DEFAULT ""
            """
                    .trimMargin()
            )

            // just drop and recreate token price, as it is temporary cache
            db.execSQL(
                """
                DROP TABLE IF EXISTS `tokenPrice`
            """
                    .trimIndent()
            )

            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `tokenPrice` (
                `tokenId` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `price` TEXT NOT NULL,
                PRIMARY KEY(`tokenId`, `currency`)
            )
            """
                    .trimMargin()
            )
        }
    }

val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `address_book_entry` (
                `chainId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                PRIMARY KEY(`chainId`, `address`)
            )
       """
                    .trimIndent()
            )
        }
    }

val MIGRATION_7_8 =
    object : Migration(7, 8) {
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
            """
            .trimIndent()
    )
    execSQL(
        """
            UPDATE tokenPrice SET
                tokenId = REPLACE(tokenId, '$before', '$after')
                WHERE tokenId LIKE '%$before'
            """
            .trimIndent()
    )
    execSQL(
        """
            UPDATE tokenValue SET
                chain = REPLACE(chain, '$before', '$after')
                WHERE chain LIKE '%$before'
            """
            .trimIndent()
    )
    execSQL(
        """
            UPDATE address_book_entry SET
                chainId = REPLACE(chainId, '$before', '$after')
                WHERE chainId LIKE '%$before'
            """
            .trimIndent()
    )
}

val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `addressBookOrder` (
            `value` TEXT PRIMARY KEY NOT NULL,
            `order` REAL NOT NULL)
            """
                    .trimIndent()
            )
        }
    }

val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            UPDATE coin
            SET address = replace(address, 'bitcoincash:', '')
            WHERE chain = 'Bitcoin-Cash'
       """
                    .trimIndent()
            )
            db.execSQL(
                """
            UPDATE tokenValue
            SET address = replace(address, 'bitcoincash:', '')
            WHERE chain = 'Bitcoin-Cash'
       """
                    .trimIndent()
            )
            db.execSQL(
                """
            UPDATE address_book_entry
            SET address = replace(address, 'bitcoincash:', '')
            WHERE chainId = 'Bitcoin-Cash'
       """
                    .trimIndent()
            )
        }
    }

val MIGRATION_10_11 =
    object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.updateChainNameValue("Gaia", "Cosmos")
        }
    }

val MIGRATION_11_12 =
    object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            UPDATE coin
            SET logo = 'polygon'
            WHERE logo = 'matic'
            """
                    .trimIndent()
            )
        }
    }

val MIGRATION_12_13 =
    object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.updateCoinDecimals("UNI", 18)
            db.updateCoinDecimals("MATIC", 18)
            db.updateCoinDecimals("WBTC", 8)
            db.updateCoinDecimals("LINK", 18)
            db.updateCoinDecimals("FLIP", 18)
        }
    }

val MIGRATION_13_14 =
    object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_coin_vaultId` ON `coin` (`vaultId`)")
        }
    }

val MIGRATION_14_15 =
    object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `vaultFolder` (
                `id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """
                    .trimMargin()
            )
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `folderOrder` (
            `value` TEXT PRIMARY KEY NOT NULL,
            `order` REAL NOT NULL)
            """
                    .trimMargin()
            )
            db.execSQL(
                """
            ALTER TABLE `vaultOrder` ADD COLUMN `parentId` TEXT
            """
                    .trimMargin()
            )
        }
    }

val MIGRATION_15_16 =
    object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {

            db.execSQL(
                """
            DELETE FROM tokenvalue
            WHERE chain = "BSC"
            AND ticker = "WETH"
            """
                    .trimIndent()
            )

            db.execSQL(
                """
            DELETE FROM coin
            WHERE id = 'WETH-BSC'
            """
                    .trimIndent()
            )
        }
    }

val MIGRATION_16_17 =
    object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `signer_new` (
                `index` INT NOT NULL,
                `vaultId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                PRIMARY KEY(`vaultId`, `title`),
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
            )
        """
                    .trimIndent()
            )

            db.execSQL(
                """
            INSERT INTO signer_new (`index`, vaultId, title)
            SELECT 0 AS `index`, vaultId, title FROM signer
        """
                    .trimIndent()
            )

            db.execSQL(
                """
            DROP TABLE signer
        """
                    .trimIndent()
            )

            db.execSQL(
                """
            ALTER TABLE signer_new RENAME TO signer
        """
                    .trimIndent()
            )
        }
    }
val MIGRATION_17_18 =
    object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            UPDATE coin
            SET ticker = 'POL' , logo = 'pol'
            WHERE chain = 'Polygon' and ticker='MATIC'
            """
                    .trimIndent()
            )
            db.execSQL(
                """
            UPDATE coin
            SET ticker = 'POL' , logo = 'pol'
            WHERE chain = 'Ethereum' and ticker='MATIC'
            """
                    .trimIndent()
            )
        }
    }

private fun SupportSQLiteDatabase.updateCoinDecimals(ticker: String, decimal: Int) {
    execSQL(
        """
        UPDATE coin SET decimals = $decimal
        WHERE ticker = "$ticker"
    """
            .trimIndent()
    )
}

internal val MIGRATION_18_19 =
    object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `vaultMetadata` (
                `vaultId` TEXT NOT NULL,
                `isServerBackupVerified` INTEGER DEFAULT NULL,
                PRIMARY KEY(`vaultId`),
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """
                    .trimIndent()
            )
        }
    }

internal val MIGRATION_19_20 =
    object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            ALTER TABLE `vault` ADD COLUMN `libType` TEXT NOT NULL DEFAULT 'GG20'
            """
                    .trimIndent()
            )
        }
    }

internal val MIGRATION_20_21 =
    object : Migration(20, 21) {
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
    """
            .trimIndent()
    )
}

internal val MIGRATION_21_22 =
    object : Migration(21, 22) {
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
            """
                    .trimIndent()
            )

            // Copy data from the old table to the new table
            db.execSQL(
                """
            INSERT INTO `vaultMetadata_new` (`vaultId`, `fastVaultPasswordReminderShownDate`)
            SELECT `vaultId`, NULL AS `fastVaultPasswordReminderShownDate`
            FROM `vaultMetadata`
            """
                    .trimIndent()
            )

            // Drop the old table
            db.execSQL("DROP TABLE `vaultMetadata`")

            // Rename the new table to the original table name
            db.execSQL("ALTER TABLE `vaultMetadata_new` RENAME TO `vaultMetadata`")
        }
    }

internal val MIGRATION_22_23 =
    object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `disabledCoin` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `coinId` TEXT NOT NULL,
                `chain` TEXT NOT NULL,
                `vaultId` TEXT NOT NULL,
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
        """
                    .trimIndent()
            )
        }
    }

internal val MIGRATION_23_24 =
    object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create active_bonded_nodes table for storing bonded node information
            db.execSQL(
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
            """
                    .trimIndent()
            )

            db.execSQL(
                """
            CREATE INDEX IF NOT EXISTS `index_active_bonded_nodes_vault_id`
            ON `active_bonded_nodes` (`vault_id`)
            """
                    .trimIndent()
            )

            db.execSQL(
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
            """
                    .trimIndent()
            )

            db.execSQL(
                """
            CREATE INDEX IF NOT EXISTS `index_staking_details_vault_id`
            ON `staking_details` (`vault_id`)
            """
                    .trimIndent()
            )
        }
    }

internal val MIGRATION_24_25 =
    object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Update price provider ID from matic-network to polygon-ecosystem-token
            db.execSQL(
                """
            UPDATE coin
            SET priceProviderId = 'polygon-ecosystem-token'
            WHERE priceProviderId = 'matic-network'
            """
                    .trimIndent()
            )
        }
    }

internal val MIGRATION_25_26 =
    object : Migration(25, 26) {
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
            """
                    .trimIndent()
            )
        }
    }

internal val MIGRATION_26_27 =
    object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            ALTER TABLE vault ADD COLUMN pubKeyMldsa TEXT NOT NULL DEFAULT ''
            """
                    .trimIndent()
            )
        }
    }

internal val MIGRATION_27_28 =
    object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `vault_notification_settings` (
                `vaultId` TEXT NOT NULL,
                `notificationsEnabled` INTEGER NOT NULL DEFAULT 0,
                `notificationsPrompted` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`vaultId`),
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """
                    .trimIndent()
            )
        }
    }

internal val MIGRATION_28_29 =
    object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE INDEX IF NOT EXISTS `index_disabledCoin_vaultId` ON `disabledCoin` (`vaultId`)
            """
                    .trimIndent()
            )
        }
    }

internal val MIGRATION_29_30 =
    object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS transaction_history (
                id TEXT PRIMARY KEY NOT NULL,
                vaultId TEXT NOT NULL,
                type TEXT NOT NULL,
                status TEXT NOT NULL,
                chain TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                txHash TEXT NOT NULL,
                explorerUrl TEXT NOT NULL,
                fiatValue TEXT,
                fromAddress TEXT,
                toAddress TEXT,
                amount TEXT,
                token TEXT,
                tokenLogo TEXT,
                feeEstimate TEXT,
                memo TEXT,
                fromToken TEXT,
                fromAmount TEXT,
                fromChain TEXT,
                fromTokenLogo TEXT,
                toToken TEXT,
                toAmount TEXT,
                toChain TEXT,
                toTokenLogo TEXT,
                provider TEXT,
                route TEXT,
                confirmedAt INTEGER,
                failureReason TEXT,
                lastCheckedAt INTEGER,
                FOREIGN KEY(vaultId) REFERENCES vault(id) ON DELETE CASCADE
            )
        """
                    .trimIndent()
            )

            db.execSQL(
                "CREATE INDEX index_transaction_history_vaultId ON transaction_history(vaultId)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_transaction_history_txHash ON transaction_history(txHash)"
            )
            db.execSQL(
                "CREATE INDEX index_transaction_history_status ON transaction_history(status)"
            )
            db.execSQL("CREATE INDEX index_transaction_history_type ON transaction_history(type)")
            db.execSQL("CREATE INDEX index_transaction_history_chain ON transaction_history(chain)")
            db.execSQL(
                "CREATE INDEX index_transaction_history_timestamp ON transaction_history(timestamp)"
            )
        }
    }

// Replaces the wide table (17 nullable type-specific columns) with a single JSON `payload`
// column via TransactionHistoryDataConverter. Adding future transaction types (deposit, stake,
// etc.) now requires only a new @Serializable subclass — no schema change.
// transaction_history was introduced in 29→30 and holds only display data (no vault keys or
// funds), so DROP + RECREATE is safe.
internal val MIGRATION_30_31 =
    object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS transaction_history")

            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS transaction_history (
                id TEXT PRIMARY KEY NOT NULL,
                vaultId TEXT NOT NULL,
                type TEXT NOT NULL,
                status TEXT NOT NULL,
                chain TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                txHash TEXT NOT NULL,
                explorerUrl TEXT NOT NULL,
                payload TEXT NOT NULL,
                confirmedAt INTEGER,
                failureReason TEXT,
                lastCheckedAt INTEGER,
                FOREIGN KEY(vaultId) REFERENCES vault(id) ON DELETE CASCADE
            )
        """
                    .trimIndent()
            )

            db.execSQL(
                "CREATE INDEX index_transaction_history_vaultId ON transaction_history(vaultId)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_transaction_history_txHash ON transaction_history(txHash)"
            )
            db.execSQL(
                "CREATE INDEX index_transaction_history_status ON transaction_history(status)"
            )
            db.execSQL("CREATE INDEX index_transaction_history_type ON transaction_history(type)")
            db.execSQL("CREATE INDEX index_transaction_history_chain ON transaction_history(chain)")
            db.execSQL(
                "CREATE INDEX index_transaction_history_timestamp ON transaction_history(timestamp)"
            )
        }
    }

// Adds retryCount for exponential backoff and migrates legacy UUID-based ids to
// deterministic "chain:txHash" format.
internal val MIGRATION_31_32 =
    object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE transaction_history ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0"
            )

            // Migrate legacy UUID ids → deterministic "chain:txHash".
            // We query rows whose id does not contain ':' (the separator),
            // then update each row's id in-place to the new format.
            val cursor =
                db.query(
                    "SELECT id, chain, txHash FROM transaction_history WHERE id NOT LIKE '%:%'"
                )
            cursor.use {
                while (it.moveToNext()) {
                    val oldId = it.getString(0)
                    val chain = it.getString(1)
                    val txHash = it.getString(2)
                    val newId = "$chain:$txHash"

                    db.execSQL(
                        "UPDATE transaction_history SET id = ? WHERE id = ?",
                        arrayOf(newId, oldId),
                    )
                }
            }
        }
    }

// Convention: every upward migration N→N+1 must be accompanied by a downgrade migration N+1→N.
// Future PRs that bump the database version must include both directions.

/** Drops the `chainOrder` table introduced in version 2. */
val MIGRATION_2_1 =
    object : Migration(2, 1) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `chainOrder`")
        }
    }

/** Drops `vaultOrder` and recreates `chainOrder` with the v2 single-column schema. */
val MIGRATION_3_2 =
    object : Migration(3, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `vaultOrder`")
            db.execSQL("DROP TABLE IF EXISTS `chainOrder`")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `chainOrder` (
                `value` TEXT PRIMARY KEY NOT NULL,
                `order` REAL NOT NULL)"""
                    .trimIndent()
            )
        }
    }

/** Drops the `tokenValue` and `tokenPrice` tables introduced in version 4. */
val MIGRATION_4_3 =
    object : Migration(4, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `tokenValue`")
            db.execSQL("DROP TABLE IF EXISTS `tokenPrice`")
        }
    }

/** Recreates `chainOrder` with the composite `(value, parentId)` primary key used in version 4. */
val MIGRATION_5_4 =
    object : Migration(5, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `chainOrder` (
                `value` TEXT NOT NULL,
                `order` REAL NOT NULL,
                `parentId` TEXT NOT NULL,
                PRIMARY KEY(`value`, `parentId`))"""
                    .trimIndent()
            )
        }
    }

/** Rebuilds `coin` without the `logo` column and recreates `tokenPrice` with the v5 schema. */
val MIGRATION_6_5 =
    object : Migration(6, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `coin_new` (
                `id` TEXT NOT NULL,
                `vaultId` TEXT NOT NULL,
                `chain` TEXT NOT NULL,
                `ticker` TEXT NOT NULL,
                `decimals` INTEGER NOT NULL,
                `priceProviderId` TEXT NOT NULL,
                `contractAddress` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `hexPublicKey` TEXT NOT NULL,
                PRIMARY KEY(`id`, `vaultId`),
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON DELETE CASCADE ON UPDATE CASCADE)"""
                    .trimIndent()
            )
            db.execSQL(
                "INSERT INTO `coin_new` (`id`,`vaultId`,`chain`,`ticker`,`decimals`,`priceProviderId`,`contractAddress`,`address`,`hexPublicKey`) SELECT `id`,`vaultId`,`chain`,`ticker`,`decimals`,`priceProviderId`,`contractAddress`,`address`,`hexPublicKey` FROM `coin`"
            )
            db.execSQL("DROP TABLE `coin`")
            db.execSQL("ALTER TABLE `coin_new` RENAME TO `coin`")
            db.execSQL("DROP TABLE IF EXISTS `tokenPrice`")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `tokenPrice` (
                `priceProviderId` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `price` TEXT NOT NULL,
                PRIMARY KEY(`priceProviderId`, `currency`))"""
                    .trimIndent()
            )
        }
    }

/** Drops the `address_book_entry` table introduced in version 7. */
val MIGRATION_7_6 =
    object : Migration(7, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `address_book_entry`")
        }
    }

/** Reverts chain display-name renames introduced in version 8 (MayaChain, CronosChain, etc.). */
val MIGRATION_8_7 =
    object : Migration(8, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.updateChainNameValue("MayaChain", "Maya Chain")
            db.updateChainNameValue("CronosChain", "Cronos Chain")
            db.updateChainNameValue("Bitcoin-Cash", "Bitcoin Cash")
            db.updateChainNameValue("Gaia", "Gaia Chain")
        }
    }

/** Drops the `addressBookOrder` table introduced in version 9. */
val MIGRATION_9_8 =
    object : Migration(9, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `addressBookOrder`")
        }
    }

/** No-op: the `bitcoincash:` address prefix stripped in 9→10 cannot be safely re-added. */
val MIGRATION_10_9 =
    object : Migration(10, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No-op: the bitcoincash: prefix removed in 9→10 cannot be safely re-added.
        }
    }

/** Reverts the Cosmos chain name from "Cosmos" back to "Gaia". */
val MIGRATION_11_10 =
    object : Migration(11, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.updateChainNameValue("Cosmos", "Gaia")
        }
    }

/** Reverts the Polygon coin logo from "polygon" back to "matic". */
val MIGRATION_12_11 =
    object : Migration(12, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE coin SET logo = 'matic' WHERE logo = 'polygon'")
        }
    }

/** No-op: original coin decimal values before version 13 cannot be restored. */
val MIGRATION_13_12 =
    object : Migration(13, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No-op: original coin decimal values before 12→13 are unknown and cannot be restored.
        }
    }

/** Drops the `index_coin_vaultId` index introduced in version 14. */
val MIGRATION_14_13 =
    object : Migration(14, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_coin_vaultId`")
        }
    }

/** Drops `vaultFolder`/`folderOrder` and strips the `parentId` column from `vaultOrder`. */
val MIGRATION_15_14 =
    object : Migration(15, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `vaultFolder`")
            db.execSQL("DROP TABLE IF EXISTS `folderOrder`")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `vaultOrder_new` (
                `value` TEXT PRIMARY KEY NOT NULL,
                `order` REAL NOT NULL)"""
                    .trimIndent()
            )
            db.execSQL(
                "INSERT INTO `vaultOrder_new` (`value`, `order`) SELECT `value`, `order` FROM `vaultOrder`"
            )
            db.execSQL("DROP TABLE `vaultOrder`")
            db.execSQL("ALTER TABLE `vaultOrder_new` RENAME TO `vaultOrder`")
        }
    }

/** No-op: WETH-BSC rows deleted in 15→16 cannot be restored. */
val MIGRATION_16_15 =
    object : Migration(16, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No-op: the WETH-BSC rows deleted in 15→16 cannot be restored.
        }
    }

/** Rebuilds `signer` without the `index`/`publicKeyecdsa` columns added in version 17. */
val MIGRATION_17_16 =
    object : Migration(17, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `signer_new` (
                `vaultId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                PRIMARY KEY(`vaultId`, `title`),
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON DELETE CASCADE ON UPDATE CASCADE)"""
                    .trimIndent()
            )
            db.execSQL(
                "INSERT INTO `signer_new` (`vaultId`, `title`) SELECT `vaultId`, `title` FROM `signer`"
            )
            db.execSQL("DROP TABLE `signer`")
            db.execSQL("ALTER TABLE `signer_new` RENAME TO `signer`")
        }
    }

/** Reverts the POL ticker/logo back to MATIC for Polygon and Ethereum chains. */
val MIGRATION_18_17 =
    object : Migration(18, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE coin SET ticker = 'MATIC', logo = 'matic' WHERE chain = 'Polygon' AND ticker = 'POL'"
            )
            db.execSQL(
                "UPDATE coin SET ticker = 'MATIC', logo = 'matic' WHERE chain = 'Ethereum' AND ticker = 'POL'"
            )
        }
    }

/** Drops the `vaultMetadata` table introduced in version 19. */
internal val MIGRATION_19_18 =
    object : Migration(19, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `vaultMetadata`")
        }
    }

/** Rebuilds `vault` without the `libType` column added in version 20. */
internal val MIGRATION_20_19 =
    object : Migration(20, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `vault_new` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `name` TEXT NOT NULL,
                `localPartyId` TEXT NOT NULL,
                `pubKeyEcdsa` TEXT NOT NULL,
                `pubKeyEddsa` TEXT NOT NULL,
                `hexChainCode` TEXT NOT NULL,
                `resharePrefix` TEXT NOT NULL)"""
                    .trimIndent()
            )
            db.execSQL(
                "INSERT INTO `vault_new` (`id`,`name`,`localPartyId`,`pubKeyEcdsa`,`pubKeyEddsa`,`hexChainCode`,`resharePrefix`) SELECT `id`,`name`,`localPartyId`,`pubKeyEcdsa`,`pubKeyEddsa`,`hexChainCode`,`resharePrefix` FROM `vault`"
            )
            db.execSQL("DROP TABLE `vault`")
            db.execSQL("ALTER TABLE `vault_new` RENAME TO `vault`")
        }
    }

/**
 * No-op: original `priceProviderId` values for Arbitrum USDT/USDC before version 21 are unknown.
 */
internal val MIGRATION_21_20 =
    object : Migration(21, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No-op: original priceProviderId values for Arbitrum USDT/USDC before 20→21 are
            // unknown.
        }
    }

/**
 * Rebuilds `vaultMetadata` with the v21 schema (restores `isServerBackupVerified`, drops
 * `fastVaultPasswordReminderShownDate`).
 */
internal val MIGRATION_22_21 =
    object : Migration(22, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `vaultMetadata_new` (
                `vaultId` TEXT NOT NULL,
                `isServerBackupVerified` INTEGER DEFAULT NULL,
                PRIMARY KEY(`vaultId`),
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON DELETE CASCADE ON UPDATE CASCADE)"""
                    .trimIndent()
            )
            db.execSQL(
                "INSERT INTO `vaultMetadata_new` (`vaultId`) SELECT `vaultId` FROM `vaultMetadata`"
            )
            db.execSQL("DROP TABLE `vaultMetadata`")
            db.execSQL("ALTER TABLE `vaultMetadata_new` RENAME TO `vaultMetadata`")
        }
    }

/** Drops the `disabledCoin` table introduced in version 23. */
internal val MIGRATION_23_22 =
    object : Migration(23, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `disabledCoin`")
        }
    }

/** Drops `active_bonded_nodes` and `staking_details` tables introduced in version 24. */
internal val MIGRATION_24_23 =
    object : Migration(24, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `active_bonded_nodes`")
            db.execSQL("DROP TABLE IF EXISTS `staking_details`")
        }
    }

/** Reverts the Polygon `priceProviderId` from "polygon-ecosystem-token" back to "matic-network". */
internal val MIGRATION_25_24 =
    object : Migration(25, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE coin SET priceProviderId = 'matic-network' WHERE priceProviderId = 'polygon-ecosystem-token'"
            )
        }
    }

/** Drops the `chainPublicKey` table introduced in version 26. */
internal val MIGRATION_26_25 =
    object : Migration(26, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `chainPublicKey`")
        }
    }

/** Rebuilds `vault` without the `pubKeyMldsa` column added in version 27. */
internal val MIGRATION_27_26 =
    object : Migration(27, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `vault_new` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `name` TEXT NOT NULL,
                `localPartyId` TEXT NOT NULL,
                `pubKeyEcdsa` TEXT NOT NULL,
                `pubKeyEddsa` TEXT NOT NULL,
                `hexChainCode` TEXT NOT NULL,
                `resharePrefix` TEXT NOT NULL,
                `libType` TEXT NOT NULL DEFAULT 'GG20')"""
                    .trimIndent()
            )
            db.execSQL(
                "INSERT INTO `vault_new` (`id`,`name`,`localPartyId`,`pubKeyEcdsa`,`pubKeyEddsa`,`hexChainCode`,`resharePrefix`,`libType`) SELECT `id`,`name`,`localPartyId`,`pubKeyEcdsa`,`pubKeyEddsa`,`hexChainCode`,`resharePrefix`,`libType` FROM `vault`"
            )
            db.execSQL("DROP TABLE `vault`")
            db.execSQL("ALTER TABLE `vault_new` RENAME TO `vault`")
        }
    }

/** Drops the `vault_notification_settings` table introduced in version 28. */
internal val MIGRATION_28_27 =
    object : Migration(28, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `vault_notification_settings`")
        }
    }

/** Drops the `index_disabledCoin_vaultId` index added in version 29. */
internal val MIGRATION_29_28 =
    object : Migration(29, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_disabledCoin_vaultId`")
        }
    }

// transaction_history holds only display data (no vault keys or funds); DROP is safe.
/** Drops the `transaction_history` table added in version 30; data cannot be recovered. */
internal val MIGRATION_30_29 =
    object : Migration(30, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `transaction_history`")
        }
    }

// Recreates transaction_history with the v30 wide-column schema.
// transaction_history holds only display data; DROP+RECREATE is safe.
/**
 * Drops and recreates `transaction_history` with the v30 wide-column schema (without the `payload`
 * column).
 */
internal val MIGRATION_31_30 =
    object : Migration(31, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `transaction_history`")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `transaction_history` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `vaultId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `chain` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `txHash` TEXT NOT NULL,
                `explorerUrl` TEXT NOT NULL,
                `fiatValue` TEXT,
                `fromAddress` TEXT,
                `toAddress` TEXT,
                `amount` TEXT,
                `token` TEXT,
                `tokenLogo` TEXT,
                `feeEstimate` TEXT,
                `memo` TEXT,
                `fromToken` TEXT,
                `fromAmount` TEXT,
                `fromChain` TEXT,
                `fromTokenLogo` TEXT,
                `toToken` TEXT,
                `toAmount` TEXT,
                `toChain` TEXT,
                `toTokenLogo` TEXT,
                `provider` TEXT,
                `route` TEXT,
                `confirmedAt` INTEGER,
                `failureReason` TEXT,
                `lastCheckedAt` INTEGER,
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON DELETE CASCADE)"""
                    .trimIndent()
            )
            db.execSQL(
                "CREATE INDEX `index_transaction_history_vaultId` ON `transaction_history`(`vaultId`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX `index_transaction_history_txHash` ON `transaction_history`(`txHash`)"
            )
            db.execSQL(
                "CREATE INDEX `index_transaction_history_status` ON `transaction_history`(`status`)"
            )
            db.execSQL(
                "CREATE INDEX `index_transaction_history_type` ON `transaction_history`(`type`)"
            )
            db.execSQL(
                "CREATE INDEX `index_transaction_history_chain` ON `transaction_history`(`chain`)"
            )
            db.execSQL(
                "CREATE INDEX `index_transaction_history_timestamp` ON `transaction_history`(`timestamp`)"
            )
        }
    }

// Rebuilds transaction_history without the retryCount column added in 31→32.
// transaction_history holds only display data; DROP+RECREATE is safe.
/**
 * Rebuilds `transaction_history` with the v31 schema (without the `retryCount` column added in
 * version 32).
 */
internal val MIGRATION_32_31 =
    object : Migration(32, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `transaction_history`")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `transaction_history` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `vaultId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `chain` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `txHash` TEXT NOT NULL,
                `explorerUrl` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `confirmedAt` INTEGER,
                `failureReason` TEXT,
                `lastCheckedAt` INTEGER,
                FOREIGN KEY(`vaultId`) REFERENCES `vault`(`id`) ON DELETE CASCADE)"""
                    .trimIndent()
            )
            db.execSQL(
                "CREATE INDEX `index_transaction_history_vaultId` ON `transaction_history`(`vaultId`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX `index_transaction_history_txHash` ON `transaction_history`(`txHash`)"
            )
            db.execSQL(
                "CREATE INDEX `index_transaction_history_status` ON `transaction_history`(`status`)"
            )
            db.execSQL(
                "CREATE INDEX `index_transaction_history_type` ON `transaction_history`(`type`)"
            )
            db.execSQL(
                "CREATE INDEX `index_transaction_history_chain` ON `transaction_history`(`chain`)"
            )
            db.execSQL(
                "CREATE INDEX `index_transaction_history_timestamp` ON `transaction_history`(`timestamp`)"
            )
        }
    }
