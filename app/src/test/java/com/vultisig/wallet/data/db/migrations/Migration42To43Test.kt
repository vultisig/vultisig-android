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

        verify(exactly = 2) { db.execSQL(capture(statements)) }

        val deleteDuplicate = statements[0]
        assertTrue(deleteDuplicate.contains("DELETE FROM coin"))
        assertTrue(deleteDuplicate.contains("chain IN ('Ton', 'Tron')"))
        assertTrue(deleteDuplicate.contains("contractAddress != ''"))
        assertTrue(deleteDuplicate.contains("id = ticker || '-' || chain"))
        assertTrue(
            deleteDuplicate.contains(
                "corrected.id = coin.ticker || '-' || coin.chain || '-' || coin.contractAddress"
            )
        )

        val updateLegacy = statements[1]
        assertTrue(updateLegacy.contains("UPDATE coin"))
        assertTrue(
            updateLegacy.contains("SET id = ticker || '-' || chain || '-' || contractAddress")
        )
        assertTrue(updateLegacy.contains("chain IN ('Ton', 'Tron')"))
        assertTrue(updateLegacy.contains("contractAddress != ''"))
        assertTrue(updateLegacy.contains("id = ticker || '-' || chain"))
    }
}
