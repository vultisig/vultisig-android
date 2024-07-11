package com.vultisig.wallet.data.db

import android.content.Context
import androidx.room.Room
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.db.dao.TokenValueDao
import com.vultisig.wallet.data.db.migrations.MIGRATION_1_2
import com.vultisig.wallet.data.db.migrations.MIGRATION_2_3
import com.vultisig.wallet.data.db.migrations.MIGRATION_3_4
import com.vultisig.wallet.data.db.migrations.MIGRATION_4_5
import com.vultisig.wallet.data.db.migrations.MIGRATION_5_6
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DatabaseModule {

    companion object {

        @Provides
        @Singleton
        fun provideAppDatabase(
            @ApplicationContext appContext: Context,
        ): AppDatabase =
            /* never enable destructive migrations in production */
            Room.databaseBuilder(
                context = appContext,
                klass = AppDatabase::class.java,
                name = DB_NAME,
            )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6
                )
                .build()

        private const val DB_NAME = "vultisig_db"

        @Provides
        @Singleton
        fun provideVaultDao(
            appDatabase: AppDatabase,
        ): VaultDao = appDatabase.vaultDao()

        @Provides
        @Singleton
        fun provideVaultOrderDao(
            appDatabase: AppDatabase,
        ): VaultOrderDao = appDatabase.vaultOrderDao()

        @Provides
        @Singleton
        fun provideTokenValueDao(
            appDatabase: AppDatabase,
        ): TokenValueDao = appDatabase.tokenValueDao()

        @Provides
        @Singleton
        fun provideTokenPriceDao(
            appDatabase: AppDatabase,
        ): TokenPriceDao = appDatabase.tokenPriceDao()

    }

}