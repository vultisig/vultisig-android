package com.vultisig.wallet.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vultisig.wallet.data.db.dao.AddressBookEntryDao
import com.vultisig.wallet.data.db.dao.AddressBookOrderDao
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.db.dao.TokenValueDao
import com.vultisig.wallet.data.db.models.AddressBookEntryEntity
import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import com.vultisig.wallet.data.db.models.CoinEntity
import com.vultisig.wallet.data.db.models.KeyShareEntity
import com.vultisig.wallet.data.db.models.SignerEntity
import com.vultisig.wallet.data.db.models.TokenPriceEntity
import com.vultisig.wallet.data.db.models.TokenValueEntity
import com.vultisig.wallet.data.db.models.VaultEntity
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
    ],
    version = 14,
    exportSchema = false,
)
internal abstract class AppDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao

    abstract fun vaultOrderDao(): VaultOrderDao

    abstract fun tokenValueDao(): TokenValueDao

    abstract fun tokenPriceDao(): TokenPriceDao

    abstract fun addressBookEntryDao(): AddressBookEntryDao

    abstract fun addressBookOrderDao(): AddressBookOrderDao

}