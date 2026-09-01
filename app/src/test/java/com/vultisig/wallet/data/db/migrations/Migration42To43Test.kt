package com.vultisig.wallet.data.db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class Migration42To43Test {

    @Test
    fun `migrate contract-qualifies legacy TON and TRON custom token ids`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()

        MIGRATION_42_43.migrate(db)

        verify(exactly = 5) { db.execSQL(capture(statements)) }

        val copyTokenPrice = statements[0]
        assertTrue(copyTokenPrice.contains("INSERT OR IGNORE INTO tokenPrice"))
        assertTrue(copyTokenPrice.contains("tokenPrice.tokenId = coin.ticker || '-' || coin.chain"))
        assertTrue(copyTokenPrice.contains("coin.chain IN ('Ton', 'Tron')"))
        assertTrue(copyTokenPrice.contains("coin.contractAddress != ''"))

        val deleteLegacyTokenPrice = statements[1]
        assertTrue(deleteLegacyTokenPrice.contains("DELETE FROM tokenPrice"))
        assertTrue(deleteLegacyTokenPrice.contains("tokenId IN"))
        assertTrue(deleteLegacyTokenPrice.contains("chain IN ('Ton', 'Tron')"))
        assertTrue(deleteLegacyTokenPrice.contains("contractAddress != ''"))

        val updateDisabledCoin = statements[2]
        assertTrue(updateDisabledCoin.contains("UPDATE disabledCoin"))
        assertTrue(updateDisabledCoin.contains("SET coinId ="))
        assertTrue(updateDisabledCoin.contains("coin.vaultId = disabledCoin.vaultId"))
        assertTrue(updateDisabledCoin.contains("coin.chain = disabledCoin.chain"))
        assertTrue(updateDisabledCoin.contains("coin.contractAddress != ''"))

        val deleteDuplicate = statements[3]
        assertTrue(deleteDuplicate.contains("DELETE FROM coin"))
        assertTrue(deleteDuplicate.contains("chain IN ('Ton', 'Tron')"))
        assertTrue(deleteDuplicate.contains("contractAddress != ''"))
        assertTrue(deleteDuplicate.contains("id = ticker || '-' || chain"))
        assertTrue(
            deleteDuplicate.contains(
                "corrected.id = coin.ticker || '-' || coin.chain || '-' || coin.contractAddress"
            )
        )

        val updateLegacy = statements[4]
        assertTrue(updateLegacy.contains("UPDATE coin"))
        assertTrue(
            updateLegacy.contains("SET id = ticker || '-' || chain || '-' || contractAddress")
        )
        assertTrue(updateLegacy.contains("chain IN ('Ton', 'Tron')"))
        assertTrue(updateLegacy.contains("contractAddress != ''"))
        assertTrue(updateLegacy.contains("id = ticker || '-' || chain"))
    }
}
