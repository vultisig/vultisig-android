package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.vultisig.wallet.data.db.models.CmcPriceEntity

@Dao
internal interface CmcPriceDao {

    @Insert
    suspend fun insertCmcPrice(cmcPrice: CmcPriceEntity)

    @Query("SELECT cmcId FROM cmcPrice WHERE contractAddress = :contractAddress")
    suspend fun getCmcId(contractAddress: String): Int?

}