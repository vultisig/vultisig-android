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

// Adds broadcastBlockNumber: the chain head block number captured at broadcast. Polkadot status
// confirmation scans the absolute inclusion window from this block instead of a head-relative
// window, so a confirmed transfer is no longer missed once the head advances past it. Nullable —
// existing rows keep NULL and fall back to the legacy head-relative scan.
internal val MIGRATION_32_33 =
    object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Nullable column, no DEFAULT clause — matches the entity (no defaultValue annotation)
            // so Room's TableInfo validation passes; existing rows get NULL implicitly.
            db.execSQL("ALTER TABLE transaction_history ADD COLUMN broadcastBlockNumber INTEGER")
        }
    }

// Rebrands the native Ton token from TON to GRAM (#4981). Coins are persisted per-vault and
// reconstructed directly from the stored columns, so the registry change alone never reaches
// vaults that already enabled TON — they would keep ticker="TON" + the old logo forever.
//
// The rebrand flips Coin.id ("${ticker}-${chain.id}") from TON-Ton → GRAM-Ton, and that id is a
// cross-table key. Updating only ticker/logo on `coin` would desync every cache keyed by the id,
// so the native row is migrated across all referencing tables together:
//   - coin       : id, ticker, logo (native row only: contractAddress = '')
//   - tokenValue : ticker (PK is chain,address,ticker; tokenId = "$ticker-$chain")
//   - tokenPrice : tokenId (cache keyed by Coin.id)
//   - disabledCoin: coinId (TON-Ton → GRAM-Ton)
// GRAM-Ton is a brand-new id, so none of these updates can collide with an existing PK.
// Chain identity, priceProviderID ("the-open-network") and decimals are intentionally unchanged.
internal val MIGRATION_33_34 =
    object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            UPDATE coin
            SET id = 'GRAM-Ton', ticker = 'GRAM', logo = 'gram'
            WHERE chain = 'Ton' AND ticker = 'TON' AND contractAddress = ''
            """
                    .trimIndent()
            )
            db.execSQL(
                """
            UPDATE tokenValue
            SET ticker = 'GRAM'
            WHERE chain = 'Ton' AND ticker = 'TON'
            """
                    .trimIndent()
            )
            db.execSQL(
                """
            UPDATE tokenPrice
            SET tokenId = 'GRAM-Ton'
            WHERE tokenId = 'TON-Ton'
            """
                    .trimIndent()
            )
            db.execSQL(
                """
            UPDATE disabledCoin
            SET coinId = 'GRAM-Ton'
            WHERE chain = 'Ton' AND coinId = 'TON-Ton'
            """
                    .trimIndent()
            )
        }
    }

// Adds contractAddress to tokenValue's primary key (#5251). Coin.id is contract-qualified for
// THORChain secured assets (MIGRATION for coin id already handled in-app, not a schema change),
// but tokenValue's key stayed chain+address+ticker only, so two secured assets sharing a ticker
// on different underlying chains (e.g. ETH.USDC and AVAX.USDC) collided on one cached-balance row.
// Existing rows get contractAddress='' — this only affects the balance CACHE (BalanceRepository
// re-fetches and re-keys correctly on next live read), so no user data is lost, just a one-time
// cache miss for previously-cached secured-asset balances.
internal val MIGRATION_34_35 =
    object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `tokenValue_new` (
                `chain` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `ticker` TEXT NOT NULL,
                `tokenValue` TEXT NOT NULL,
                `contractAddress` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`chain`, `address`, `ticker`, `contractAddress`)
            )
            """
                    .trimIndent()
            )

            db.execSQL(
                """
            INSERT INTO `tokenValue_new` (`chain`, `address`, `ticker`, `tokenValue`, `contractAddress`)
            SELECT `chain`, `address`, `ticker`, `tokenValue`, '' FROM `tokenValue`
            """
                    .trimIndent()
            )

            db.execSQL("DROP TABLE `tokenValue`")
            db.execSQL("ALTER TABLE `tokenValue_new` RENAME TO `tokenValue`")
        }
    }

// Corrects the Sui SEND token decimals from 9 to 6 (#5371). The registry fix in Coins.kt only
// reaches newly enabled coins; coins are persisted per-vault and reconstructed straight from the
// stored `decimals` column, so vaults that already enabled SEND keep signing amounts scaled by
// 10^9 instead of 10^6 (1000x wrong) until the stored row itself is corrected. Only display
// interpretation depends on decimals — cached raw balances in tokenValue are unaffected.
internal val MIGRATION_35_36 =
    object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            UPDATE coin
            SET decimals = 6
            WHERE chain = 'Sui' AND ticker = 'SEND'
            """
                    .trimIndent()
            )
        }
    }

// Corrects the Ethereum POL contract address from the legacy MATIC contract it was mistakenly
// sharing (#5404). The registry fix in Coins.kt only reaches newly enabled coins; coins are
// persisted per-vault and reconstructed straight from the stored `contractAddress` column, so
// vaults that already enabled POL keep resolving balances/transfers against the legacy MATIC
// contract until the stored row itself is corrected. The cached balance in `tokenValue` is keyed
// by contractAddress too, so it simply misses once and is re-fetched under the new address on the
// next live read — no separate migration needed there.
internal val MIGRATION_36_37 =
    object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            UPDATE coin
            SET contractAddress = '0x455e53CBB86018Ac2B8092FdCd39d8444aFFC3F6'
            WHERE chain = 'Ethereum' AND ticker = 'POL'
              AND contractAddress = '0x7d1afa7b718fb893db30a3abc0cfc608aacfebb0'
            """
                    .trimIndent()
            )
        }
    }

// Canonicalizes legacy EVM address-book rows to Chain.Ethereum's id (#5403). Before the network
// picker consolidated every EVM chain into one "EVM" tile, a contact saved on e.g. Arbitrum or BSC
// was persisted under that chain's own id; the app now always reads/writes EVM entries under
// "Ethereum", so those older rows would otherwise become permanently unreachable. Rows sharing an
// address (case-insensitively, matching the app's own EVM address matching) across the EVM family
// are collapsed to the earliest one first, so the chainId rewrite below can never collide with an
// existing primary key. addressBookOrder keys embed the old chainId too ("$chainId-$address"), so
// its now-stale legacy-chain rows are dropped; the list screen already re-creates a default order
// entry for any address-book row it doesn't find one for.
private val LEGACY_EVM_CHAIN_IDS =
    listOf(
        "Arbitrum",
        "Avalanche",
        "Base",
        "CronosChain",
        "BSC",
        "Blast",
        "Optimism",
        "Polygon",
        "Zksync",
        "Mantle",
        "Sei",
        "Hyperliquid",
    )
private val EVM_CHAIN_IDS = LEGACY_EVM_CHAIN_IDS + "Ethereum"

internal val MIGRATION_37_38 =
    object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val evmChainIdsSql = EVM_CHAIN_IDS.joinToString(",") { "'$it'" }
            val legacyEvmChainIdsSql = LEGACY_EVM_CHAIN_IDS.joinToString(",") { "'$it'" }

            db.execSQL(
                """
            DELETE FROM address_book_entry
            WHERE chainId IN ($evmChainIdsSql)
            AND rowid NOT IN (
                SELECT MIN(rowid) FROM address_book_entry
                WHERE chainId IN ($evmChainIdsSql)
                GROUP BY LOWER(address)
            )
            """
                    .trimIndent()
            )
            db.execSQL(
                """
            UPDATE address_book_entry
            SET chainId = 'Ethereum'
            WHERE chainId IN ($legacyEvmChainIdsSql)
            """
                    .trimIndent()
            )
            LEGACY_EVM_CHAIN_IDS.forEach { legacyChainId ->
                db.execSQL("DELETE FROM addressBookOrder WHERE `value` LIKE '$legacyChainId-%'")
            }
        }
    }

// Adds the pending_limit_order table: a local record of placed THORChain limit orders (#4154),
// keyed by the inbound deposit tx hash. Phase 1 has no in-app open-orders list, so this is a
// write-only store for now; Phase 2 surfaces it inside TX History.
internal val MIGRATION_38_39 =
    object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `pending_limit_order` (
                `inbound_tx_hash` TEXT NOT NULL,
                `vault_id` TEXT NOT NULL,
                `source_asset` TEXT NOT NULL,
                `source_amount` TEXT NOT NULL,
                `target_asset` TEXT NOT NULL,
                `dest_addr` TEXT NOT NULL,
                `target_price` TEXT NOT NULL,
                `expiry_blocks` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                PRIMARY KEY(`inbound_tx_hash`)
            )
            """
                    .trimIndent()
            )
        }
    }

// Widens pending_limit_order from a write-only placement record into the table the Limit Orders tab
// and the cancel flow read (#4154). Three groups of columns:
//   * identity — the source chain and sender address, without which an order can neither be polled
//     (the queue endpoint is scoped by sender) nor cancelled;
//   * the exact integers and full asset spellings a `m=<` cancel memo must reproduce, captured at
//     signing because none of them is recoverable afterwards;
//   * observations — fill split, expiry countdown, the assets/trade target THORChain itself
// reports,
//     and the local cancel bookkeeping.
// Every column is nullable (or defaulted) so existing rows survive: a null there means "unknown",
// and unknown cancel inputs deliberately leave an older order uncancellable rather than cancelled
// with guessed values.
internal val MIGRATION_39_40 =
    object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `source_chain` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `source_decimals` INTEGER",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `source_address` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `source_ticker` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `target_ticker` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `source_amount_1e8` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `trade_target` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `source_asset_full` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `target_asset_full` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `observed_trade_target` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `observed_source_asset` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `observed_target_asset` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `deposit_amount` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `filled_in_amount` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `filled_out_amount` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `time_to_expiry_blocks` INTEGER",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `expiry_observed_at` INTEGER",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `cancel_broadcast_hash` TEXT",
                    "ALTER TABLE `pending_limit_order` ADD COLUMN `cancel_confirmed` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                .forEach(db::execSQL)
        }
    }

// RUJI and sRUJI shipped with `ruji` as their price-provider id, which CoinGecko does not resolve
// to anything — it indexes the token as `rujira`. Coins are persisted per vault at the time they
// are added, so correcting the static definitions alone leaves every already-added RUJI stuck at
// $0.00 until the user removes and re-adds it. Rewrite the stored id in place instead.
internal val MIGRATION_40_41 =
    object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            UPDATE coin
            SET priceProviderId = 'rujira'
            WHERE priceProviderId = 'ruji'
            """
                    .trimIndent()
            )
        }
    }

// Gives the vault row the backup flag that until now lived in preferences, defaulting to
// not-backed-up: a vault nobody has exported must never read as safe to lose, which is how iOS and
// Windows have always declared it.
//
// Every existing row starts at 0, including the ones whose owner has already exported. Restoring
// those is a code-side pass — VaultBackupStatusBackfill — because the flags it reads live in
// DataStore, which SQL cannot see. It runs before the first vault read, so nothing observes the
// gap; a vault with no stored flag stays 0, since unknown is not the same as safe.
internal val MIGRATION_41_42 =
    object : Migration(41, 42) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `vault` ADD COLUMN `isBackedUp` INTEGER NOT NULL DEFAULT 0")
        }
    }
