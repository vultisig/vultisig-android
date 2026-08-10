package com.vultisig.wallet.data.db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class Migration41To42Test {

    /**
     * Pins the default at 0. Every existing row gets it, including the vaults whose owner has
     * already exported — `VaultBackupStatusBackfill` restores those from preferences, which SQL
     * cannot read. A default of 1 would hand every vault in the table a backup it may not have.
     */
    @Test
    fun `migrate adds the vault backup flag defaulting to not backed up`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sql = slot<String>()

        MIGRATION_41_42.migrate(db)

        verify { db.execSQL(capture(sql)) }
        val statement = sql.captured
        assertTrue(statement.contains("ALTER TABLE `vault`"))
        assertTrue(statement.contains("ADD COLUMN `isBackedUp`"))
        assertTrue(statement.contains("INTEGER NOT NULL DEFAULT 0"))
    }
}
