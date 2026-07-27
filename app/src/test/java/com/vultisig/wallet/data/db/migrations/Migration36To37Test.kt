package com.vultisig.wallet.data.db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import com.vultisig.wallet.data.models.Coins
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class Migration36To37Test {

    @Test
    fun `migrate corrects Ethereum POL rows still pointing at the legacy MATIC contract to the real POL contract`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sql = slot<String>()

        MIGRATION_36_37.migrate(db)

        verify { db.execSQL(capture(sql)) }
        val statement = sql.captured
        assertTrue(statement.contains("UPDATE coin"))
        assertTrue(
            statement.contains("SET contractAddress = '${Coins.Ethereum.POL.contractAddress}'")
        )
        assertTrue(statement.contains("WHERE chain = 'Ethereum'"))
        assertTrue(statement.contains("ticker = 'POL'"))
        assertTrue(
            statement.contains("contractAddress = '${Coins.Ethereum.MATIC.contractAddress}'")
        )
    }
}
