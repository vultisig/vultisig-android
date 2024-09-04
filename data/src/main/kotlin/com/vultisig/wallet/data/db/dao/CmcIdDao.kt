package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vultisig.wallet.data.db.models.CmcIdEntity

@Dao
interface CmcIdDao {

    @Upsert
    suspend fun insertCmcId(cmcId: CmcIdEntity)

    @Query("SELECT id FROM cmcId WHERE contractAddress = :contractAddress")
    suspend fun getCmcId(contractAddress: String): Int?

}