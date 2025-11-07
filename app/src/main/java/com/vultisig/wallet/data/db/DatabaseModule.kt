package com.vultisig.wallet.data.db

import android.content.Context
import androidx.room.Room
import com.vultisig.wallet.data.db.dao.ActiveBondedNodeDao
import com.vultisig.wallet.data.db.dao.AddressBookEntryDao
import com.vultisig.wallet.data.db.dao.AddressBookOrderDao
import com.vultisig.wallet.data.db.dao.FolderDao
import com.vultisig.wallet.data.db.dao.FolderOrderDao
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.db.dao.TokenValueDao
import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.db.dao.VaultMetadataDao
import com.vultisig.wallet.data.db.dao.VaultOrderDao
import com.vultisig.wallet.data.db.migrations.MIGRATION_10_11
import com.vultisig.wallet.data.db.migrations.MIGRATION_11_12
import com.vultisig.wallet.data.db.migrations.MIGRATION_12_13
import com.vultisig.wallet.data.db.migrations.MIGRATION_13_14
import com.vultisig.wallet.data.db.migrations.MIGRATION_14_15
import com.vultisig.wallet.data.db.migrations.MIGRATION_15_16
import com.vultisig.wallet.data.db.migrations.MIGRATION_16_17
import com.vultisig.wallet.data.db.migrations.MIGRATION_17_18
import com.vultisig.wallet.data.db.migrations.MIGRATION_18_19
import com.vultisig.wallet.data.db.migrations.MIGRATION_19_20
import com.vultisig.wallet.data.db.migrations.MIGRATION_1_2
import com.vultisig.wallet.data.db.migrations.MIGRATION_20_21
import com.vultisig.wallet.data.db.migrations.MIGRATION_21_22
import com.vultisig.wallet.data.db.migrations.MIGRATION_22_23
import com.vultisig.wallet.data.db.migrations.MIGRATION_23_24
import com.vultisig.wallet.data.db.migrations.MIGRATION_2_3
import com.vultisig.wallet.data.db.migrations.MIGRATION_3_4
import com.vultisig.wallet.data.db.migrations.MIGRATION_4_5
import com.vultisig.wallet.data.db.migrations.MIGRATION_5_6
import com.vultisig.wallet.data.db.migrations.MIGRATION_6_7
import com.vultisig.wallet.data.db.migrations.MIGRATION_7_8
import com.vultisig.wallet.data.db.migrations.MIGRATION_8_9
import com.vultisig.wallet.data.db.migrations.MIGRATION_9_10
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
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24,
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
        fun provideFolderOrderDao(
            appDatabase: AppDatabase,
        ): FolderOrderDao = appDatabase.folderOrderDao()

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

        @Provides
        @Singleton
        fun provideFolderDao(
            appDatabase: AppDatabase,
        ): FolderDao = appDatabase.folderDao()

        @Provides
        @Singleton
        fun provideAddressBookEntryDao(
            appDatabase: AppDatabase,
        ): AddressBookEntryDao = appDatabase.addressBookEntryDao()

        @Provides
        @Singleton
        fun provideAddressBookOrderDao(
            appDatabase: AppDatabase,
        ): AddressBookOrderDao = appDatabase.addressBookOrderDao()

        @Provides
        @Singleton
        fun provideVaultMetadataDao(
            appDatabase: AppDatabase,
        ): VaultMetadataDao = appDatabase.vaultMetadataDao()

        @Provides
        @Singleton
        fun provideActiveBondedNodeDao(
            appDatabase: AppDatabase,
        ): ActiveBondedNodeDao = appDatabase.activeBondedNodeDao()
    }
}