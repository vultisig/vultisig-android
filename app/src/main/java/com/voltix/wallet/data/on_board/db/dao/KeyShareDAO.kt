package com.voltix.wallet.data.on_board.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Upsert
import com.voltix.wallet.data.on_board.db.entity.KeyShareEntity

@Dao
interface KeyShareDAO {
    @Insert
    suspend fun upsertKeyShare(keyShareEntity: KeyShareEntity)

    @Delete
    suspend fun deleteKeyShare(keyShareEntity: KeyShareEntity)
}