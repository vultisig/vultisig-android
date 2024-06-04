package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vultisig.wallet.data.db.models.TokenPriceEntity

@Dao
internal interface TokenPriceDao {

    @Query(
        "SELECT price FROM tokenPrice WHERE " +
                "priceProviderId = :priceProviderId AND currency = :currency"
    )
    suspend fun getTokenPrice(
        priceProviderId: String,
        currency: String
    ): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokenPrice(tokenPrice: TokenPriceEntity)

}