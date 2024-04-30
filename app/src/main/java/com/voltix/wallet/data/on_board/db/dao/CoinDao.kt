package com.voltix.wallet.data.on_board.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Upsert
import com.voltix.wallet.data.on_board.db.entity.CoinEntity

@Dao
interface CoinDao {

    @Insert
    suspend fun upsertCoin(coinEntity: CoinEntity)

    @Delete
    suspend fun deleteCoin(coinEntity: CoinEntity)

}