package com.voltix.wallet.data.on_board.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.voltix.wallet.data.on_board.db.entity.VaultEntity
import com.voltix.wallet.data.on_board.db.entity.VaultWithKeyShare
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDAO {

    @Insert
    suspend fun upsertVault(vaultEntity: VaultEntity)

    @Delete
    suspend fun deleteVault(vaultEntity: VaultEntity)

    @Transaction
    @Query("SELECT * FROM vault")
    fun getAllVault() : Flow<List<VaultWithKeyShare>>

}