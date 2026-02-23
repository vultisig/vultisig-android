package com.vultisig.wallet.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vultisig.wallet.data.db.converters.DateConverter
import com.vultisig.wallet.data.db.converters.LocalDateTypeConverter
import com.vultisig.wallet.data.db.converters.SigningLibTypeTypeConverter
import com.vultisig.wallet.data.db.dao.ActiveBondedNodeDao
import com.vultisig.wallet.data.db.dao.AddressBookEntryDao
import com.vultisig.wallet.data.db.dao.AddressBookOrderDao
import com.vultisig.wallet.data.db.dao.FolderDao
import com.vultisig.wallet.data.db.dao.FolderOrderDao
import com.vultisig.wallet.data.db.dao.StakingDetailsDao
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.db.dao.TokenValueDao
import com.vultisig.wallet.data.db.dao.TransactionHistoryDao
import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.db.dao.VaultMetadataDao
import com.vultisig.wallet.data.db.dao.VaultOrderDao
import com.vultisig.wallet.data.db.models.ActiveBondedNodeEntity
import com.vultisig.wallet.data.db.models.AddressBookEntryEntity
import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import com.vultisig.wallet.data.db.models.CoinEntity
import com.vultisig.wallet.data.db.models.DisabledCoinEntity
import com.vultisig.wallet.data.db.models.FolderEntity
import com.vultisig.wallet.data.db.models.FolderOrderEntity
import com.vultisig.wallet.data.db.models.KeyShareEntity
import com.vultisig.wallet.data.db.models.SignerEntity
import com.vultisig.wallet.data.db.models.StakingDetailsEntity
import com.vultisig.wallet.data.db.models.TokenPriceEntity
import com.vultisig.wallet.data.db.models.TokenValueEntity
import com.vultisig.wallet.data.db.models.ChainPublicKeyEntity
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.VaultEntity
import com.vultisig.wallet.data.db.models.VaultMetadataEntity
import com.vultisig.wallet.data.db.models.VaultOrderEntity

@Database(
    entities = [
        VaultEntity::class,
        KeyShareEntity::class,
        SignerEntity::class,
        CoinEntity::class,
        VaultOrderEntity::class,
        TokenValueEntity::class,
        TokenPriceEntity::class,
        AddressBookEntryEntity::class,
        AddressBookOrderEntity::class,
        FolderEntity::class,
        FolderOrderEntity::class,
        VaultMetadataEntity::class,
        DisabledCoinEntity::class,
        ActiveBondedNodeEntity::class,
        StakingDetailsEntity::class,
        ChainPublicKeyEntity::class,
        TransactionHistoryEntity::class,
    ],
    version = 27,
    exportSchema = false,
)
@TypeConverters(
    SigningLibTypeTypeConverter::class,
    LocalDateTypeConverter::class,
    DateConverter::class,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao

    abstract fun vaultOrderDao(): VaultOrderDao

    abstract fun folderOrderDao(): FolderOrderDao

    abstract fun tokenValueDao(): TokenValueDao

    abstract fun tokenPriceDao(): TokenPriceDao

    abstract fun addressBookEntryDao(): AddressBookEntryDao

    abstract fun addressBookOrderDao(): AddressBookOrderDao

    abstract fun vaultMetadataDao(): VaultMetadataDao

    abstract fun folderDao(): FolderDao

    abstract fun activeBondedNodeDao(): ActiveBondedNodeDao

    abstract fun stakingDetailsDao(): StakingDetailsDao

    abstract fun transactionHistoryDao(): TransactionHistoryDao


}