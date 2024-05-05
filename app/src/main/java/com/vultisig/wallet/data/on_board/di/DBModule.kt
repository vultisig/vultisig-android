package com.vultisig.wallet.data.on_board.di

import android.content.Context
import com.vultisig.wallet.data.on_board.db.VaultDB

interface DatabaseModule {
    fun provideVaultDatabase(context: Context): VaultDB
}

object DefaultDatabaseModule : DatabaseModule {
    override fun provideVaultDatabase(context: Context): VaultDB {
        return VaultDB(context)
    }
}