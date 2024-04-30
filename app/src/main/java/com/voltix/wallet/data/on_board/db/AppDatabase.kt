package com.voltix.wallet.data.on_board.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.voltix.wallet.data.on_board.db.dao.CoinDao
import com.voltix.wallet.data.on_board.db.dao.KeyShareDAO
import com.voltix.wallet.data.on_board.db.dao.VaultDAO
import com.voltix.wallet.data.on_board.db.entity.CoinEntity
import com.voltix.wallet.data.on_board.db.entity.KeyShareEntity
import com.voltix.wallet.data.on_board.db.entity.VaultEntity


/**
 * The Room database for this app
 */
@Database(
    entities = [VaultEntity::class, KeyShareEntity::class , CoinEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun VaultDAO(): VaultDAO
    abstract fun KeyShareDAO(): KeyShareDAO
    abstract fun CoinDao(): CoinDao

}