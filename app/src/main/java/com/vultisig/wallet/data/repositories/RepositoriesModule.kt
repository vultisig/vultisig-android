package com.vultisig.wallet.data.repositories

import android.content.Context
import com.vultisig.wallet.data.on_board.db.VaultDB
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoriesModule {

    @Binds
    abstract fun bindChainAccountsRepository(
        impl: ChainAccountsRepositoryImpl,
    ): ChainAccountsRepository

    companion object {
        @Provides
        fun provideVaultDb(
            @ApplicationContext context: Context,
        ) = VaultDB(context)
    }

}