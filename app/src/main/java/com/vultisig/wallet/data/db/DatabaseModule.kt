package com.vultisig.wallet.data.db

import android.content.Context
import androidx.room.Room
import com.vultisig.wallet.data.db.migrations.MIGRATION_1_2
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
        fun provideVaultDao(
            appDatabase: AppDatabase,
        ): VaultDao = appDatabase.vaultDao()

        @Provides
        @Singleton
        fun provideOrderDao(
            appDatabase: AppDatabase,
        ): ChainOrderDao = appDatabase.chainOrderDao()

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
            ).addMigrations(MIGRATION_1_2).build()

        private const val DB_NAME = "vultisig_db"

    }

}